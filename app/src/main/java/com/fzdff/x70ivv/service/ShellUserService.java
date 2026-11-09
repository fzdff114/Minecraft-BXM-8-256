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
import java.util.Locale;
import java.util.regex.Pattern;

/** Shizuku shell service: narrowly scoped graphics diagnostics and per-app driver preferences. */
public final class ShellUserService extends ICompatService.Stub {
    private static final String KEY_PKGS = "angle_gl_driver_selection_pkgs";
    private static final String KEY_VALUES = "angle_gl_driver_selection_values";
    private static final String PACKAGE_ANGLE = "org.chromium.angle";
    private static final String[] ANGLE_PACKAGES = {
            "org.chromium.angle",
            "com.android.angle",
            "com.google.android.angle"
    };
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    private static final int MAX_CAPTURE_CHARS = 360_000;
    private volatile Process captureProcess;
    private volatile Thread captureThread;
    private volatile boolean captureActive;
    private volatile String captureTarget = "com.mojang.minecraftpe";
    private final StringBuilder captureBuffer = new StringBuilder();

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
        out.append("当前用户 ID：").append(run("am", "get-current-user")).append("\n");
        out.append("MagicOS build：").append(firstNonEmpty(
                run("getprop", "ro.build.version.magic"),
                run("getprop", "ro.build.display.id"))).append("\n");
        appendProperty(out, "GPU OpenGL ES property", "ro.opengles.version");
        appendProperty(out, "EGL vendor property", "ro.hardware.egl");
        appendProperty(out, "Vulkan HAL property", "ro.hardware.vulkan");
        appendProperty(out, "Vendor GPU driver", "ro.vendor.gpu.driver.version");
        appendProperty(out, "Graphics driver 0", "ro.gfx.driver.0");
        appendProperty(out, "Graphics driver 1", "ro.gfx.driver.1");
        out.append("游戏包：").append(targetPackage).append("\n");
        out.append("游戏 APK 路径：").append(run("pm", "path", targetPackage)).append("\n");
        out.append("游戏进程 PID：").append(run("pidof", targetPackage)).append("\n");
        out.append("当前用户 Minecraft 进程：").append(processRows(targetPackage)).append("\n");

        String currentUser = run("am", "get-current-user").trim();
        if (!currentUser.matches("[0-9]+")) currentUser = "0";
        String currentUserGraphicsPackages = run("sh", "-c",
                "pm list packages --user " + currentUser + " 2>&1 | grep -Ei 'angle|opengl|gpu.*driver|graphics.*driver' || true").trim();
        out.append("当前用户图形/ANGLE 相关包（仅 user ").append(currentUser).append("）：\n")
                .append(currentUserGraphicsPackages.isEmpty() ? "  未发现匹配包\n" : currentUserGraphicsPackages + "\n");

        out.append("ANGLE 候选包检测（逐个查询，不枚举所有用户）：\n");
        boolean found = false;
        for (String candidate : ANGLE_PACKAGES) {
            String path = run("pm", "path", candidate);
            if (path.startsWith("package:")) {
                found = true;
                out.append("  ").append(candidate).append("：").append(path.replace("\n", " ")).append("\n");
            } else {
                out.append("  ").append(candidate).append("：未安装/不可见").append("\n");
            }
        }
        if (!found) out.append("没有找到已知 ANGLE APK。设备可能未预装 ANGLE，也可能使用了非标准包名。\n");

        appendSetting(out, "ANGLE debug package", "angle_debug_package");
        appendSetting(out, "ANGLE whitelist", "angle_whitelist");
        appendSetting(out, "单应用包名列表", KEY_PKGS);
        appendSetting(out, "单应用驱动列表", KEY_VALUES);
        appendSetting(out, "全局 ANGLE-for-all（只读）", "angle_gl_driver_all_angle");

        String selection = alignedSelection(targetPackage);
        out.append("目标游戏当前驱动偏好：").append(selection).append("\n");
        String debugPackage = setting("angle_debug_package");
        if (found) {
            out.append("ANGLE APK 存在；但需检查 debug package / 系统 GraphicsEnvironment 日志确认实际加载。\n");
        } else if ("angle".equals(selection)) {
            out.append("警告：目标游戏被设置为 angle，但没有检测到已知 ANGLE APK。该偏好不等于 ANGLE 已加载。\n");
        }
        if ("null".equals(debugPackage) && found) {
            out.append("提示：angle_debug_package 未指定；是否需要指定取决于 ROM 的 ANGLE 集成方式，不能直接假设。\n");
        }
        out.append("\n说明：ANGLE 只接管 OpenGL ES。若 Minecraft 对应的 RenderDragon 路径直接使用 Vulkan，ANGLE 偏好不会改变那条路径。");
        return out.toString();
    }

    @Override
    public String applyAngle(String targetPackage) throws RemoteException {
        return setDriverPreference(targetPackage, "angle");
    }

    @Override
    public String setDriverPreference(String targetPackage, String driver) throws RemoteException {
        if (!validPackage(targetPackage)) return "ERROR\n目标包名格式无效。";
        if (!("angle".equals(driver) || "native".equals(driver) || "default".equals(driver))) {
            return "ERROR\n不支持的驱动选项。";
        }
        String path = run("pm", "path", targetPackage).trim();
        if (!path.startsWith("package:")) return "ERROR\n目标游戏包未安装或当前用户不可见：" + targetPackage;

        if ("angle".equals(driver) && !hasAngleProvider()) {
            return "NO_ANGLE\n未找到已知 ANGLE Provider，拒绝写入 angle。先保持原生驱动，不会修改设置。";
        }

        String beforePkgs = setting(KEY_PKGS);
        String beforeValues = setting(KEY_VALUES);
        Map<String, String> current = parsePairs(beforePkgs, beforeValues);
        if (current == null) {
            return "ERROR\n系统包名/驱动列表不匹配。为避免覆盖其他应用设置，已停止修改。";
        }

        boolean hadTarget = current.containsKey(targetPackage);
        String oldValue = current.get(targetPackage);
        JSONObject backup = new JSONObject();
        try {
            backup.put("hadTarget", hadTarget);
            backup.put("oldValue", oldValue == null ? JSONObject.NULL : oldValue);
        } catch (Exception ignored) { }

        current.put(targetPackage, driver);
        String error = writePairs(current);
        if (!error.isEmpty()) return "ERROR\n写入失败：" + error;

        String encoded = Base64.encodeToString(backup.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String label = "native".equals(driver) ? "原生 OpenGL" : ("default".equals(driver) ? "系统默认规则" : "ANGLE");
        return "OK\nBACKUP=" + encoded + "\n已将 " + targetPackage + " 设为 " + label
                + "（单应用）。请完全停止 Minecraft 和 MB Loader 后重新启动测试。";
    }

    @Override
    public String clearDriverOverride(String targetPackage) throws RemoteException {
        if (!validPackage(targetPackage)) return "ERROR\n目标包名格式无效。";
        String path = run("pm", "path", targetPackage).trim();
        if (!path.startsWith("package:")) return "ERROR\n目标游戏包未安装或当前用户不可见：" + targetPackage;

        String beforePkgs = setting(KEY_PKGS);
        String beforeValues = setting(KEY_VALUES);
        Map<String, String> current = parsePairs(beforePkgs, beforeValues);
        if (current == null) {
            return "ERROR\n系统包名/驱动列表不匹配。为避免覆盖其他应用设置，已停止修改。";
        }

        boolean hadTarget = current.containsKey(targetPackage);
        String oldValue = current.get(targetPackage);
        JSONObject backup = new JSONObject();
        try {
            backup.put("hadTarget", hadTarget);
            backup.put("oldValue", oldValue == null ? JSONObject.NULL : oldValue);
        } catch (Exception ignored) { }

        current.remove(targetPackage);
        String error = writePairs(current);
        if (!error.isEmpty()) return "ERROR\n清除失败：" + error;

        String encoded = Base64.encodeToString(backup.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "OK\nBACKUP=" + encoded + "\n已清除 " + targetPackage
                + " 的单应用图形驱动覆盖。现在由 Android / MagicOS 按系统默认规则选择驱动；其他应用的配置保持不变。";
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
    public synchronized String startGraphicsCapture(String targetPackage) throws RemoteException {
        if (!validPackage(targetPackage)) return "ERROR\\n目标包名格式无效。";
        if (captureActive) {
            return "OK\\n启动阶段监视已经运行。当前目标：" + captureTarget;
        }
        try {
            Process process = new ProcessBuilder("logcat", "-b", "all", "-v", "threadtime", "-T", "1")
                    .redirectErrorStream(true).start();
            captureProcess = process;
            captureTarget = targetPackage;
            synchronized (captureBuffer) {
                captureBuffer.setLength(0);
                captureBuffer.append("=== X70i VV 启动阶段连续日志 ===\\n");
                captureBuffer.append("开始时间：").append(System.currentTimeMillis()).append(" (epoch ms)\\n");
                captureBuffer.append("目标包：").append(targetPackage).append("\\n");
                captureBuffer.append("启动前 PID：").append(run("pidof", targetPackage)).append("\\n");
            }
            captureActive = true;
            run("log", "-t", "X70iVV", "CAPTURE_START target=" + targetPackage);
            Thread reader = new Thread(() -> readCapture(process), "X70iVV-Logcat");
            reader.setDaemon(true);
            captureThread = reader;
            reader.start();
            return "OK\\n已开始持续记录图形日志。现在可以启动 MBL2；记录会覆盖加载黑屏和后续切回花式的阶段。\\n返回助手后点击“停止监视并读取启动日志”。";
        } catch (Exception error) {
            captureActive = false;
            captureProcess = null;
            return "ERROR\\n无法启动 logcat 连续记录：" + error.getClass().getSimpleName() + ": " + error.getMessage();
        }
    }

    @Override
    public String stopGraphicsCapture() throws RemoteException {
        Process process = captureProcess;
        Thread reader = captureThread;
        captureActive = false;
        run("log", "-t", "X70iVV", "CAPTURE_STOP target=" + captureTarget);
        if (process != null) {
            try { process.destroy(); } catch (Throwable ignored) { }
        }
        if (reader != null && reader != Thread.currentThread()) {
            try { reader.join(1500); } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (captureBuffer) {
            captureBuffer.append("\\n结束时间：").append(System.currentTimeMillis()).append(" (epoch ms)\\n");
            captureBuffer.append("结束时 PID：").append(run("pidof", captureTarget)).append("\\n");
            captureBuffer.append("结束时进程：").append(processRows(captureTarget)).append("\\n");
            captureBuffer.append("=== 连续日志结束 ===\\n");
            String report = captureBuffer.toString();
            captureProcess = null;
            captureThread = null;
            if (report.length() > 500_000) {
                report = report.substring(report.length() - 500_000);
            }
            return report;
        }
    }

    private void readCapture(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isGraphicsLogLine(line)) appendCapture(line);
            }
        } catch (Exception error) {
            appendCapture("[X70iVV capture reader] " + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (captureProcess == process) captureActive = false;
        }
    }

    private void appendCapture(String line) {
        synchronized (captureBuffer) {
            captureBuffer.append(line).append('\\n');
            if (captureBuffer.length() > MAX_CAPTURE_CHARS) {
                int removeTo = captureBuffer.length() - (MAX_CAPTURE_CHARS * 3 / 4);
                int newline = captureBuffer.indexOf("\\n", removeTo);
                captureBuffer.delete(0, newline >= 0 ? newline + 1 : removeTo);
            }
        }
    }

    private static boolean isGraphicsLogLine(String line) {
        String value = line.toLowerCase(Locale.ROOT);
        String[] keys = {
                "graphicsenvironment", "angle", "powervr", "bxm", "vulkan", "renderdragon",
                "libgles", "gles", "egl", "framebuffer", "shader", "gpuwatchdog",
                "surfaceflinger", "vkcreate", "vkqueue", "vk_error", "context lost",
                "com.mojang.minecraftpe", "mbloader", "bambosan", "fancy", "vibrant visuals",
                "graphics mode", "deferred", "fallback", "unsupported", "failed", "fatal",
                "fatal signal", "gpu crash", "swapchain", "gralloc", "ion", "renderthread"
        };
        for (String key : keys) {
            if (value.contains(key)) return true;
        }
        return false;
    }

    @Override
    public String collectGraphicsLogs() throws RemoteException {
        String target = "com.mojang.minecraftpe";
        StringBuilder out = new StringBuilder();
        out.append("=== 设备与渲染属性 ===\n");
        for (String key : new String[]{"ro.opengles.version", "ro.hardware.egl", "ro.hardware.vulkan",
                "ro.vendor.gpu.driver.version", "ro.gfx.driver.0", "debug.hwui.renderer"}) {
            out.append(key).append(" = ").append(run("getprop", key)).append("\n");
        }
        out.append("\n=== Minecraft 进程 ===\n").append(processRows(target)).append("\n");
        out.append("PID: ").append(run("pidof", target)).append("\n");
        out.append("\n=== Graphics / EGL / Vulkan / PowerVR 日志 ===\n");
        String command = "logcat -d -v threadtime -t 18000 | grep -Ei 'GraphicsEnvironment|ANGLE|PowerVR|BXM|Vulkan|RenderDragon|libGLES|EGL|GLES|framebuffer|shader|Mojang|fatal|GpuWatchdog|SurfaceFlinger|vkCreate|vkQueue|VK_ERROR|link program|compile.*shader|context lost' | tail -n 700";
        String logs = run("sh", "-c", command).trim();
        out.append(logs.isEmpty() ? "没有匹配日志；请在黑屏复现后立即再次收集。\n" : logs);
        return out.toString();
    }

    private static boolean hasAngleProvider() {
        for (String candidate : ANGLE_PACKAGES) {
            if (run("pm", "path", candidate).trim().startsWith("package:")) return true;
        }
        return false;
    }

    private static String processRows(String targetPackage) {
        String result = run("sh", "-c", "ps -A 2>/dev/null | grep -F '" + targetPackage + "' | grep -v grep | head -n 20");
        return result.isEmpty() ? "未查到（游戏未运行或进程名不同）" : result;
    }

    private static String alignedSelection(String targetPackage) {
        Map<String, String> pairs = parsePairs(setting(KEY_PKGS), setting(KEY_VALUES));
        if (pairs == null) return "列表不匹配";
        String value = pairs.get(targetPackage);
        return value == null ? "default（未设置单独覆盖）" : value;
    }

    private static void appendProperty(StringBuilder out, String label, String key) {
        out.append(label).append("：").append(run("getprop", key)).append("\n");
    }

    private static void appendSetting(StringBuilder out, String label, String key) {
        out.append(label).append("：").append(setting(key)).append("\n");
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
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
                    if (output.length() < 48000) output.append(line).append('\n');
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
