package com.roy.worktoscroll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LedgerStore ledger;
    private LinearLayout content;
    private TextView balance;
    private TextView status;
    private TextView permissions;
    private TextView diagnostics;
    private Button workButton;
    private Button toggle;
    private Button overlayPermission;
    private final Runnable update = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 500); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ledger = LedgerStore.get(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        content.setPadding(p, p, p, p);
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = android.os.Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout())
                : insets.getSystemWindowInsets();
            content.setPadding(p + bars.left, p + bars.top, p + bars.right, p + bars.bottom);
            return insets;
        });
        setContentView(scroll);
        text(getString(R.string.app_name), 28, true);
        text("独立实验 · 不使用无障碍服务", 13, false);
        text("测试前请在原版 WorkToEarn 中停止保护，避免两版同时拦截。此版本有独立余额，不会覆盖原版。", 14, false);
        text("可用娱乐时间", 17, false);
        balance = text("00:00", 52, true);
        status = text("正在检查状态…", 18, true);
        workButton = button("开始摄像头工作", () -> startActivity(new Intent(this, WorkActivity.class)));
        permissions = text("", 14, false);
        button("1 · 授权使用情况访问", () -> openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:" + getPackageName()))));
        overlayPermission = button(getString(R.string.overlay_permission_grant), this::openOverlaySettings);
        button("显示保护通知（建议开启）", () -> {
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 10);
            } else openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        button("检查后台运行设置", () -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + getPackageName()))));
        toggle = button("开启保护", () -> {
            if (!ledger.enabled && (!Permissions.usage(this) || !Permissions.overlay(this))) {
                Toast.makeText(this, "请先完成上面两项系统授权", Toast.LENGTH_LONG).show();
                return;
            }
            if (ledger.enabled) {
                if (ProtectionService.current() != null) ProtectionService.current().stopProtection();
                else {
                    if (!ledger.setProtectionEnabled(false)) {
                        Toast.makeText(this, "停止状态保存失败，请重试", Toast.LENGTH_LONG).show();
                        return;
                    }
                    ledger.engine.resetObservation(System.currentTimeMillis());
                    ledger.save();
                    stopService(new Intent(this, ProtectionService.class));
                    ProtectionRecovery.stopped(this);
                }
            } else {
                prepareChange();
                if (ledger.setProtectionEnabled(true)) {
                    ProtectionRecovery.sync(this, false);
                    startProtection();
                }
                else {
                    Toast.makeText(this, "设置保存失败，未开启保护", Toast.LENGTH_LONG).show();
                }
            }
            render();
        });
        button("加入 5 分钟（测试）", () -> {
            prepareChange();
            ledger.engine.grant(300_000);
            if (!ledger.save()) Toast.makeText(this, "余额保存失败", Toast.LENGTH_LONG).show();
            render();
        });
        button("打开小红书", () -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(LedgerStore.TARGET);
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "未找到小红书，请确认已安装标准版", Toast.LENGTH_LONG).show();
        });
        button("清空测试余额", () -> new AlertDialog.Builder(this)
            .setTitle("清空测试余额？")
            .setMessage("只清空此测试版的可用娱乐时间，用于重新验证零余额拦截。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空", (d, w) -> {
                prepareChange(); ledger.engine.balanceMs = 0; ledger.save(); render();
            }).show());
        text("测试方法", 18, true);
        text("① 必要授权完成后默认开启保护，首页确认实际运行状态。\n② 开启保护时按小红书实际用量扣减；测试工具保留供验收。\n③ 手动停止后保持停止，白天每 4 小时最多静默提醒一次，系统可能延迟；需允许通知。\n④ 系统中断会尝试恢复，强行停止或省电限制仍可能使保护失效。", 14, false);
        diagnostics = text("", 13, false);
        text("本机处理 · 无网络权限\n使用情况访问用于判断应用切换和计算用时；悬浮窗用于在小红书上显示阻止页。这两项仍是敏感授权。\n不读取页面正文、输入内容或截图，余额与状态保存在本机。\n摄像头仅在工作页手动开始后运行，画面不保存、不上传。基础在场观察不证明正在工作。\n\n这是可关闭、可绕过的实验版。", 13, false);
    }

    private void prepareChange() {
        if (ProtectionService.current() != null) ProtectionService.current().prepareManualChange();
        else ledger.engine.resetObservation(System.currentTimeMillis());
    }

    private void render() {
        boolean usage = Permissions.usage(this);
        boolean access = Permissions.overlay(this);
        overlayPermission.setText(access ? R.string.overlay_permission_granted : R.string.overlay_permission_grant);
        ProtectionService service = ProtectionService.current();
        boolean running = service != null && SystemClock.elapsedRealtime() - service.heartbeatElapsed < 3_000;
        boolean backgroundRestricted = getSystemService(android.app.ActivityManager.class).isBackgroundRestricted();
        balance.setText(formatTime(ledger.engine.balanceMs));
        workButton.setText(ledger.work.active() ? "继续摄像头工作（当前已暂停）" : "开始摄像头工作");
        permissions.setText("使用情况访问：" + (usage ? "已授权" : "未授权")
            + "\n显示在其他应用上层：" + (access ? "已授权" : "未授权")
            + "\n运行通知：" + (getSystemService(android.app.NotificationManager.class).areNotificationsEnabled() ? "已允许" : "未允许")
            + "\n监测服务：" + (running ? "运行中" : "未连接")
            + (backgroundRestricted ? "\n系统已限制后台运行，请检查后台设置" : "")
            + ("Xiaomi".equalsIgnoreCase(android.os.Build.MANUFACTURER)
                ? "\n小米后台设置：省电策略应选无限制；当前心跳正常不代表后台不会被冻结。" : ""));
        if (!ledger.enabled) status.setText("未保护 · 小红书用时不扣减，建议开启");
        else if (!usage || !access) status.setText("尚未保护 · 请完成系统授权，返回后自动开启");
        else if (!running) status.setText("保护失效 · 服务未连接");
        else if (!service.healthy) status.setText(service.failure);
        else if (backgroundRestricted) status.setText("后台运行受限 · 保护可能中断");
        else status.setText(ledger.engine.balanceMs == 0 ? "保护运行中 · 零余额将拦截" : "保护运行中 · 按小红书用量扣减");
        toggle.setText(ledger.enabled ? "停止保护" : "开启保护");
        diagnostics.setText("累计扣减：" + formatTime(ledger.engine.consumedMs)
            + "　阻止界面次数：" + ledger.blocks
            + (ledger.workUpgradeCreditMs > 0 ? "\n历史规则升级补齐：" + formatTime(ledger.workUpgradeCreditMs) + "（已计入余额）" : "")
            + (ledger.observationGaps > 0 ? "\n监测中断记录：" + ledger.observationGaps
                + " 次 · 最近 " + formatTime(ledger.lastObservationGapMs) + "（含系统休眠等停顿）" : "")
            + "\n" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
            + " · Android " + android.os.Build.VERSION.RELEASE
            + (ledger.note.isEmpty() ? "" : "\n" + ledger.note));
    }

    private void startProtection() {
        try { startForegroundService(new Intent(this, ProtectionService.class)); }
        catch (RuntimeException ex) {
            ledger.note = "监测服务启动失败，请检查系统设置后重新打开测试版。";
            ledger.save();
            Toast.makeText(this, ledger.note, Toast.LENGTH_LONG).show();
        }
    }

    private void openOverlaySettings() {
        if (!"Xiaomi".equalsIgnoreCase(android.os.Build.MANUFACTURER)) {
            openOverlayList();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(Permissions.overlay(this) ? R.string.overlay_settings_title : R.string.overlay_grant_title)
            .setMessage(R.string.overlay_xiaomi_steps)
            .setPositiveButton(R.string.overlay_open_app_settings, (dialog, which) -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                } catch (android.content.ActivityNotFoundException | SecurityException ex) {
                    Toast.makeText(this, R.string.overlay_settings_fallback, Toast.LENGTH_LONG).show();
                    openOverlayList();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openOverlayList() {
        openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName())));
    }

    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException ex) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            Toast.makeText(this, "请在系统设置中搜索对应权限", Toast.LENGTH_LONG).show();
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String message, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(30, 58, 47));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(6), 0, dp(8));
        content.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
    private static String formatTime(long millis) {
        long seconds = (Math.max(0, millis) + 999) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    @Override protected void onResume() {
        super.onResume();
        // Persist default-on migration before starting anything, including permission setup visits.
        if (ledger.save()) ProtectionRecovery.sync(this, true);
        if (ProtectionService.current() != null) ProtectionService.current().dismissForNavigation();
        handler.post(update);
    }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
}
