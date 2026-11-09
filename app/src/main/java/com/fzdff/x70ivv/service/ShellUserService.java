package com.fzdff.x70ivv.service;

import android.os.RemoteException;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Executes read-only diagnostics and per-package ANGLE settings as Shizuku shell. */
public final class ShellUserService extends ICompatService.Stub {
    private static final String KEY_PKGS = "angle_gl_driver_selection_pkgs";
    private static final String KEY_VALUES = "angle_gl_driver_selection_values";
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");

    @Override public void destroy() { System.exit(0); }
    @Override public void exit() { destroy(); }

    @Override
    public String inspect(String targetPackage) throws RemoteException {
        if (!validPackage(targetPackage)) return "ERROR\n目标包名格式无效。";
        StringBuilder out = new StringBuilder();
        out.append("设备：").append(run("getprop", "ro.product.manufacturer")).append(" ")
                .append(run("getprop", "ro.product.model")).append("\n");
        out.append("Android：").append(run("getprop", "ro.build.version.release"))
                .append(" / API ").append(run("getprop", "ro.build.version.sdk")).append("\n");
        out.append("MagicOS build：").append(run("getprop", "ro.build.version.magic")).append("\n");
        out.append("GPU OpenGL ES property：").append(run("getprop", "ro.opengles.version")).append("\n");
        out.append("游戏包：").append(targetPackage).append("\n");
        out.append("游戏 APK 路径：").append(run("pm", "path", targetPackage)).append("\n");
        String providers = run("sh", "-c", "pm list packages | grep -i angle || true").trim();
        out.append("ANGLE 候选包：").append(providers.isEmpty() ? "未发现" : "\n" + providers).append("\n");
        out.append("ANGLE debug package：").append(setting("angle_debug_package")).append("\n");
        out.append("ANGLE whitelist：").append(setting("angle_whitelist")).append("\n");
        out.append("单应用包名列表：").append(setting(KEY_PKGS)).append("\n");
        out.append("单应用驱动列表：").append(setting(KEY_VALUES)).append("\n");
        out.append("全局 ANGLE-for-all（只读）：").append(setting("angle_gl_driver_all_angle")).append("\n\n");
        out.append("提醒：检测到 ANGLE 包不等于系统一定能为 Minecraft 加载 ANGLE；还需真机验证。");
        return out.toString();
    }

    @Override
    public String applyAngle(String targetPackage) throws RemoteException {
        if (!validPackage(targetPackage)) return "ERROR\n目标包名格式无效。";
        String path = run("pm", "path", targetPackage).trim();
        if (!path.startsWith("package:")) return "ERROR\n目标游戏包未安装：" + targetPackage;

        String providers = run("sh", "-c", "pm list packages | grep -i angle || true").trim();
        if (providers.isEmpty()) {
            return "NO_ANGLE\n系统没有发现 ANGLE 候选包。本次没有修改任何设置；仅写入 ANGLE 偏好无法安装 ANGLE。";
        }

        String beforePkgs = setting(KEY_PKGS);
        String beforeValues = setting(KEY_VALUES);
        Map<String, String> current = parsePairs(beforePkgs, beforeValues);
        if (current == null) {
            return "ERROR\n系统包名/驱动列表不匹配。为避免覆盖其他应用的设置，已停止修改。";
        }

        boolean hadTarget = current.containsKey(targetPackage);
        String oldValue = current.get(targetPackage);
        JSONObject backup = new JSONObject();
        try {
            backup.put("hadTarget", hadTarget);
            backup.put("oldValue", oldValue == null ? JSONObject.NULL : oldValue);
        } catch (Exception ignored) { }

        current.put(targetPackage, "angle");
        String error = writePairs(current);
        if (!error.isEmpty()) return "ERROR\n写入失败：" + error;

        String encoded = Base64.encodeToString(backup.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "OK\nBACKUP=" + encoded + "\n已将 " + targetPackage
                + " 设为 ANGLE（单应用）。请强制停止 Minecraft 与 MB Loader，再通过启动器重新进入世界测试。";
    }

    @Override
    public String restoreAngle(String targetPackage, String backupEncoded) throws RemoteException {
        if (!validPackage(targetPackage)) return "ERROR\n目标包名格式无效。";
        try {
            JSONObject backup = new JSONObject(new String(
                    Base64.decode(backupEncoded, Base64.NO_WRAP), StandardCharsets.UTF_8));
            boolean hadTarget = backup.optBoolean("hadTarget", false);
            String oldValue = backup.isNull("oldValue") ? null : backup.optString("oldValue", null);
            Map<String, String> current = parsePairs(setting(KEY_PKGS), setting(KEY_VALUES));
            if (current == null) return "ERROR\n当前 ANGLE 列表格式异常，未执行恢复。";

            current.remove(targetPackage);
            if (hadTarget && oldValue != null) current.put(targetPackage, oldValue);
            String error = writePairs(current);
            if (!error.isEmpty()) return "ERROR\n恢复失败：" + error;
            return "OK\n已恢复 " + targetPackage + " 修改前的驱动偏好；其他应用设置已保留。";
        } catch (Exception error) {
            return "ERROR\n备份数据无效：" + error.getMessage();
        }
    }

    @Override
    public String collectGraphicsLogs() throws RemoteException {
        String command = "logcat -d -v time -t 6000 | grep -Ei 'ANGLE|PowerVR|BXM|Vulkan|RenderDragon|libGLES|EGL|framebuffer|shader|Mojang|fatal|GpuWatchdog' | tail -n 300";
        String logs = run("sh", "-c", command).trim();
        return logs.isEmpty()
                ? "没有找到图形日志。请先复现一次闪烁/黑屏，再立即重新收集。"
                : logs;
    }

    private static boolean validPackage(String value) {
        return value != null && PACKAGE.matcher(value).matches();
    }

    private static String setting(String key) {
        String value = run("settings", "get", "global", key).trim();
        return value.isEmpty() ? "null" : value;
    }

    private static Map<String, String> parsePairs(String packages, String values) {
        Map<String, String> result = new LinkedHashMap<>();
        boolean noPackages = "null".equals(packages) || packages.isEmpty();
        boolean noValues = "null".equals(values) || values.isEmpty();
        if (noPackages && noValues) return result;
        if (noPackages || noValues) return null;

        String[] packageArray = packages.split(",", -1);
        String[] valueArray = values.split(",", -1);
        if (packageArray.length != valueArray.length) return null;

        for (int i = 0; i < packageArray.length; i++) {
            String pkg = packageArray[i].trim();
            String value = valueArray[i].trim();
            if (!validPackage(pkg)) return null;
            if (!("angle".equals(value) || "native".equals(value) || "default".equals(value))) return null;
            result.put(pkg, value);
        }
        return result;
    }

    private static String writePairs(Map<String, String> values) {
        String oldPackages = setting(KEY_PKGS);
        String oldDrivers = setting(KEY_VALUES);

        if (values.isEmpty()) {
            run("settings", "delete", "global", KEY_PKGS);
            run("settings", "delete", "global", KEY_VALUES);
            if (!"null".equals(setting(KEY_PKGS)) || !"null".equals(setting(KEY_VALUES))) {
                restoreKey(KEY_PKGS, oldPackages);
                restoreKey(KEY_VALUES, oldDrivers);
                return "系统未确认删除，已尝试回滚。";
            }
            return "";
        }

        List<String> packages = new ArrayList<>();
        List<String> drivers = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            packages.add(entry.getKey());
            drivers.add(entry.getValue());
        }

        String expectedPackages = String.join(",", packages);
        String expectedDrivers = String.join(",", drivers);
        run("settings", "put", "global", KEY_PKGS, expectedPackages);
        run("settings", "put", "global", KEY_VALUES, expectedDrivers);

        if (!expectedPackages.equals(setting(KEY_PKGS)) || !expectedDrivers.equals(setting(KEY_VALUES))) {
            restoreKey(KEY_PKGS, oldPackages);
            restoreKey(KEY_VALUES, oldDrivers);
            return "系统未确认写入，已尝试回滚原设置。";
        }
        return "";
    }

    private static void restoreKey(String key, String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            run("settings", "delete", "global", key);
        } else {
            run("settings", "put", "global", key, value);
        }
    }

    private static String run(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 24000) output.append(line).append('\n');
                }
            }
            process.waitFor();
            return output.toString().trim();
        } catch (Exception error) {
            return "ERROR: " + error.getClass().getSimpleName() + ": " + error.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
