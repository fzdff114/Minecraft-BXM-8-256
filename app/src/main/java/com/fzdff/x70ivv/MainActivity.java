package com.fzdff.x70ivv;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import rikka.shizuku.Shizuku;
import com.fzdff.x70ivv.service.ICompatService;
import com.fzdff.x70ivv.service.ShellUserService;

public final class MainActivity extends Activity {
    private static final int REQUEST_SHIZUKU = 501;
    private static final String PREFS = "angle_backup";
    private static final String DEFAULT_GAME = "com.mojang.minecraftpe";
    private static final String DEFAULT_LAUNCHER = "io.github.bambosan.mbloader";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText gamePackage;
    private EditText launcherPackage;
    private TextView status;
    private TextView device;
    private TextView result;
    private Button restoreButton;
    private GLSurfaceView glProbe;
    private String glInfo = "OpenGL 信息尚未读取";
    private ICompatService service;
    private Runnable pending;

    private final Shizuku.OnBinderReceivedListener binderReceived =
            () -> main.post(() -> status("Shizuku 已连接"));
    private final Shizuku.OnBinderDeadListener binderDead = () -> main.post(() -> {
        service = null;
        status("Shizuku 已断开");
    });
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (code, grant) -> {
        if (code == REQUEST_SHIZUKU) {
            if (grant == PERMISSION_GRANTED) bindShell();
            else status("未授权；没有修改系统设置");
        }
    };

    private final Shizuku.UserServiceArgs serviceArgs =
            new Shizuku.UserServiceArgs(new ComponentName(BuildConfig.APPLICATION_ID, ShellUserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("compat_shell")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ICompatService.Stub.asInterface(binder);
            status("诊断通道已就绪");
            Runnable task = pending;
            pending = null;
            if (task != null) task.run();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            status("诊断通道已断开");
        }
    };

    @Override protected void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        getWindow().setStatusBarColor(Color.rgb(244, 247, 252));
        getWindow().setNavigationBarColor(Color.rgb(244, 247, 252));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        buildUi();
        updateRestoreButton();
    }

    @Override protected void onResume() {
        super.onResume();
        if (glProbe != null) glProbe.onResume();
    }

    @Override protected void onPause() {
        if (glProbe != null) glProbe.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        if (glProbe != null) glProbe.onPause();
        try {
            if (service != null) Shizuku.unbindUserService(serviceArgs, serviceConnection, true);
        } catch (Throwable ignored) { }
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(244, 247, 252));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("X70i VV 兼容助手", 25, 0xff142033, true), full());
        root.addView(text("原生 Vibrant Visuals · ANGLE 检测 · 安全回滚", 13, 0xff64748b, false), top(5));

        LinearLayout statusCard = card();
        status = text("等待 Shizuku 授权", 14, 0xff246bfd, true);
        statusCard.addView(status, full());
        statusCard.addView(text("只读取图形信息，并在有 ANGLE Provider 时修改目标游戏的单应用设置。不修改 Minecraft 文件或世界存档。", 13, 0xff475569, false), top(8));
        root.addView(statusCard, top(14));

        LinearLayout deviceCard = card();
        deviceCard.addView(text("设备与图形能力", 16, 0xff142033, true), full());
        device = text(deviceSummary() + "\n等待 OpenGL 查询…", 12, 0xff475569, false);
        deviceCard.addView(device, top(8));
        root.addView(deviceCard, top(12));

        LinearLayout gameCard = card();
        gameCard.addView(text("实际渲染 Minecraft 3D 世界的包名", 16, 0xff142033, true), full());
        gamePackage = edit(DEFAULT_GAME);
        gameCard.addView(gamePackage, top(8));
        gameCard.addView(text("默认 com.mojang.minecraftpe。若 MB Loader 使用独立/克隆实例，请填写真正运行游戏画面的包名，而不是启动器包名。", 12, 0xff64748b, false), top(5));
        root.addView(gameCard, top(12));

        LinearLayout launcherCard = card();
        launcherCard.addView(text("MB Loader 启动器包名（仅用于打开）", 16, 0xff142033, true), full());
        launcherPackage = edit(DEFAULT_LAUNCHER);
        launcherCard.addView(launcherPackage, top(8));
        root.addView(launcherCard, top(12));

        gamePackage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateRestoreButton(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout ops = card();
        ops.addView(text("操作", 16, 0xff142033, true), full());
        addButton(ops, "检测 ANGLE Provider / 游戏包", () -> withShell(this::inspect));
        addButton(ops, "为目标游戏启用 ANGLE（仅 Provider 存在时）", () -> withShell(this::applyAngle));
        addButton(ops, "切回原生 OpenGL（诊断基线）", () -> withShell(this::setNativeDriver));
        addButton(ops, "清除 Minecraft 的 ANGLE 强制覆盖（恢复系统默认）", () -> withShell(this::clearDriverOverride));
        restoreButton = button("恢复修改前的驱动设置");
        restoreButton.setEnabled(false);
        restoreButton.setAlpha(0.45f);
        restoreButton.setOnClickListener(v -> withShell(this::restoreAngle));
        ops.addView(restoreButton, top(8));
        addButton(ops, "打开 MB Loader", this::launchLauncher);
        addButton(ops, "收集图形诊断日志", () -> withShell(this::collectLogs));
        addButton(ops, "打开 Shizuku 安装说明", () -> openUrl("https://shizuku.rikka.app/download/"));
        root.addView(ops, top(12));

        LinearLayout outputCard = card();
        outputCard.addView(text("检测结果", 16, 0xff142033, true), full());
        result = text("请先在 Shizuku 中通过无线调试启动服务，再点击检测。", 12, 0xff334155, false);
        result.setTextIsSelectable(true);
        outputCard.addView(result, top(8));
        addButton(outputCard, "复制检测结果", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("X70i VV diagnostics", result.getText()));
            toast("已复制");
        });
        root.addView(outputCard, top(12));

        root.addView(text("诊断会在助手自己的 EGL/GLES 上下文测试 Shader 和 Framebuffer；结果不能直接代表 Minecraft 的渲染上下文。ANGLE 只影响 OpenGL ES，不会修复直接使用 Vulkan 的 RenderDragon 路径。", 12, 0xff64748b, false), top(14));

        glProbe = new GLSurfaceView(this);
        glProbe.setEGLContextClientVersion(3);
        glProbe.setRenderer(new GlesDiagnostics(report -> main.post(() -> {
            glInfo = report;
            if (device != null) device.setText(deviceSummary() + "\n\n" + glInfo);
        })));
        root.addView(glProbe, new LinearLayout.LayoutParams(dp(2), dp(2)));
    }

    private String deviceSummary() {
        StringBuilder out = new StringBuilder();
        out.append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL);
        out.append("\nAndroid ").append(android.os.Build.VERSION.RELEASE).append(" / API ").append(android.os.Build.VERSION.SDK_INT);
        int vkLevel = 0;
        int vkVersion = 0;
        for (FeatureInfo feature : getPackageManager().getSystemAvailableFeatures()) {
            if (PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL.equals(feature.name)) vkLevel = feature.version;
            if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) vkVersion = feature.version;
        }
        out.append("\nVulkan feature level: ").append(vkLevel);
        out.append("\nVulkan feature version: ").append(vkVersion);
        return out.toString();
    }

    private void withShell(Runnable action) {
        if (!Shizuku.pingBinder()) {
            status("Shizuku 尚未运行");
            showResult("请先打开 Shizuku，通过无线调试启动服务。普通应用权限不足以修改 ANGLE 的系统级按应用设置。");
            return;
        }
        try {
            if (Shizuku.isPreV11()) {
                status("Shizuku 版本过旧，请更新");
                return;
            }
            if (Shizuku.checkSelfPermission() != PERMISSION_GRANTED) {
                pending = action;
                Shizuku.requestPermission(REQUEST_SHIZUKU);
                status("等待 Shizuku 授权");
                return;
            }
            pending = action;
            bindShell();
        } catch (Throwable error) {
            status("Shizuku 调用失败");
            showResult(error.toString());
        }
    }

    private void bindShell() {
        try {
            if (!Shizuku.pingBinder()) {
                status("Shizuku 已断开，请重新启动服务");
                return;
            }
            if (service != null && service.asBinder().pingBinder()) {
                Runnable task = pending;
                pending = null;
                if (task != null) task.run();
            } else {
                Shizuku.bindUserService(serviceArgs, serviceConnection);
                status("正在连接诊断服务…");
            }
        } catch (Throwable error) {
            status("无法建立 Shizuku 诊断通道");
            showResult(error.toString());
        }
    }

    private String targetPackage() { return gamePackage.getText().toString().trim(); }

    private void inspect() {
        String pkg = targetPackage();
        remote(() -> service.inspect(pkg), null);
    }

    private void applyAngle() {
        setDriver("angle");
    }

    private void setNativeDriver() {
        setDriver("native");
    }

    private void clearDriverOverride() {
        String pkg = targetPackage();
        if (pkg.isEmpty()) { toast("请填写 Minecraft 游戏包名"); return; }
        status("正在清除 Minecraft 的 ANGLE 强制覆盖…");
        remote(() -> service.clearDriverOverride(pkg), value -> saveBackup(pkg, value));
    }

    private void setDriver(String driver) {
        String pkg = targetPackage();
        if (pkg.isEmpty()) { toast("请填写 Minecraft 游戏包名"); return; }
        String label = "native".equals(driver) ? "原生 OpenGL" : "ANGLE";
        status("正在检查并设置 " + label + "…");
        remote(() -> service.setDriverPreference(pkg, driver), value -> saveBackup(pkg, value));
    }

    private void saveBackup(String pkg, String value) {
        if (!value.startsWith("OK\n")) return;
        String backup = null;
        for (String line : value.split("\\n")) {
            if (line.startsWith("BACKUP=")) {
                backup = line.substring(7);
                break;
            }
        }
        if (backup != null) {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            // Preserve the first pre-tool state; do not replace it with a later test state.
            if (!prefs.contains(pkg)) prefs.edit().putString(pkg, backup).apply();
            updateRestoreButton();
        }
    }

    private void restoreAngle() {
        String pkg = targetPackage();
        String backup = getSharedPreferences(PREFS, MODE_PRIVATE).getString(pkg, null);
        if (backup == null) { toast("没有找到该包名的修改前备份"); return; }
        remote(() -> service.restoreAngle(pkg, backup), value -> {
            if (value.startsWith("OK")) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(pkg).apply();
                updateRestoreButton();
            }
        });
    }

    private void collectLogs() { remote(() -> service.collectGraphicsLogs(), null); }

    private interface RemoteCall { String run() throws Exception; }
    private interface Completion { void accept(String value); }

    private void remote(RemoteCall call, Completion completion) {
        status("正在读取设备信息…");
        worker.execute(() -> {
            String value;
            try { value = call.run(); }
            catch (Throwable error) { value = "ERROR\n" + error; }
            final String output = value;
            main.post(() -> {
                showResult(output);
                if (output.startsWith("NO_ANGLE")) status("系统未发现 ANGLE Provider");
                else if (output.startsWith("OK")) status("操作完成");
                else status("检测完成，请查看结果");
                if (completion != null) completion.accept(output);
            });
        });
    }

    private void launchLauncher() {
        String pkg = launcherPackage.getText().toString().trim();
        if (pkg.isEmpty()) { toast("请填写 MB Loader 包名"); return; }
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) { toast("找不到启动器：" + pkg); return; }
        try { startActivity(intent); }
        catch (Throwable error) { toast("启动失败：" + error.getMessage()); }
    }

    private void updateRestoreButton() {
        if (restoreButton == null || gamePackage == null) return;
        boolean available = getSharedPreferences(PREFS, MODE_PRIVATE).contains(targetPackage());
        restoreButton.setEnabled(available);
        restoreButton.setAlpha(available ? 1f : 0.45f);
    }

    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button button = button(label);
        button.setOnClickListener(v -> action.run());
        parent.addView(button, top(8));
    }

    private EditText edit(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setTextSize(14);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff246bfd));
        return input;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackgroundColor(Color.WHITE);
        box.setElevation(dp(1));
        return box;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTextColor(Color.WHITE);
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff246bfd));
        view.setMinHeight(dp(46));
        return view;
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams p = full();
        p.topMargin = dp(margin);
        return p;
    }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void status(String value) { if (status != null) status.setText(value); }
    private void showResult(String value) { if (result != null) result.setText(value); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception error) { toast("无法打开链接"); }
    }
}
