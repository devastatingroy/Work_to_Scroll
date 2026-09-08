package com.roy.worktoscroll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Home and secondary pages retain the original protection and debug actions. */
public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LedgerStore ledger;
    private LinearLayout content;
    private TextView balance, status, permissions, diagnostics, worked, speech, seal;
    private Button workButton, toggle, overlayPermission, entertainment;
    private String page;
    private final Runnable update = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 500); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ledger = LedgerStore.get(this);
        page = getIntent().getStringExtra("page");
        content = CatUi.screen(this, false);
        if ("settings".equals(page)) buildSettings();
        else if ("tools".equals(page)) buildTools();
        else {
            page = "home";
            buildHome();
            if (!uiPreferences().getBoolean("cat_intro_seen_v2", false)) {
                content.post(() -> { if (!isFinishing() && !isDestroyed()) showGuide(true); });
            }
        }
        render();
    }

    private void buildHome() {
        CatUi.header(this, content, getString(R.string.app_name), null, null, "⚙ 设置", () -> openPage("settings"), false);
        status = CatUi.text(this, "正在检查保护…", 13, CatUi.MUTED, false);
        status.setMinHeight(CatUi.dp(this, 44));
        status.setFocusable(true);
        status.setPadding(CatUi.dp(this, 10), CatUi.dp(this, 8), CatUi.dp(this, 10), CatUi.dp(this, 8));
        status.setOnClickListener(v -> openPage("settings"));
        CatUi.add(content, status, -2, 2);
        speech = CatUi.text(this, "先工作，攒点时间。", 18, CatUi.INK, true);
        CatUi.add(content, speech, -2, 12);
        int imageHeight = Math.max(130, Math.min(240, getResources().getConfiguration().screenHeightDp - 620));
        CatUi.add(content, CatUi.image(this, R.drawable.cat_home), imageHeight, 4);
        LinearLayout ticket = new LinearLayout(this);
        ticket.setOrientation(LinearLayout.VERTICAL);
        ticket.setGravity(Gravity.CENTER);
        ticket.setPadding(CatUi.dp(this, 16), CatUi.dp(this, 14), CatUi.dp(this, 16), CatUi.dp(this, 12));
        ticket.setBackground(CatUi.shape(this, 0xFFFFF9EF, 12, 0xFFDAB997));
        CatUi.add(ticket, CatUi.text(this, "可用娱乐", 16, CatUi.INK, false), -2, 0);
        balance = CatUi.text(this, "0 分 00 秒", 44, CatUi.RED, true);
        balance.setMaxLines(1);
        balance.setAutoSizeTextTypeUniformWithConfiguration(24, 44, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        CatUi.add(ticket, balance, 58, 4);
        seal = CatUi.text(this, "先攒时间", 14, CatUi.RED, true);
        CatUi.add(ticket, seal, -2, 0);
        CatUi.add(content, ticket, -2, 0);
        worked = CatUi.text(this, "", 17, CatUi.INK, false);
        worked.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_work_laptop, 0, 0, 0);
        worked.setCompoundDrawablePadding(CatUi.dp(this, 8));
        CatUi.add(content, worked, -2, 18);
        worked.getLayoutParams().width = -2;
        workButton = CatUi.button(this, "▶  开始工作", true, false, this::openWork);
        CatUi.add(content, workButton, -2, 20);
        entertainment = CatUi.button(this, "去小红书  ›", false, false, this::openXiaohongshu);
        try {
            android.graphics.drawable.Drawable icon = getPackageManager().getApplicationIcon(LedgerStore.TARGET);
            icon.setBounds(0, 0, CatUi.dp(this, 24), CatUi.dp(this, 24));
            android.text.SpannableString label = new android.text.SpannableString("  去小红书  ›");
            label.setSpan(new android.text.style.ImageSpan(icon, android.text.style.ImageSpan.ALIGN_BOTTOM), 0, 1, 0);
            entertainment.setText(label);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { /* Text remains usable without the target app. */ }
        entertainment.setContentDescription("去小红书");
        CatUi.add(content, entertainment, -2, 10);
    }

    private void buildSettings() {
        CatUi.header(this, content, "设置", "‹ 返回", this::finish, null, null, false);
        section("应用保护");
        status = paragraph("");
        toggle = row("开启保护", this::toggleProtection);
        section("权限与后台运行");
        permissions = paragraph("");
        row("使用情况访问  ›", () -> openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:" + getPackageName()))));
        overlayPermission = row("悬浮窗权限  ›", this::openOverlaySettings);
        row("通知提醒  ›", this::requestNotifications);
        row("后台运行设置  ›", () -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + getPackageName()))));
        section("使用帮助");
        row("如何使用  ›", () -> showGuide(false));
        row("隐私说明  ›", this::showPrivacy);
        row("开发工具  ›", () -> openPage("tools"));
        paragraph("WorkToScroll · 自用测试版");
    }

    private void buildTools() {
        CatUi.header(this, content, "开发工具", "‹ 返回", this::finish, null, null, false);
        paragraph("测试额度会改变本机余额。日常使用不需要这些操作。");
        diagnostics = paragraph("");
        row("加入 5 分钟（测试）", () -> {
            prepareChange(); ledger.engine.grant(300_000);
            if (!ledger.save()) Toast.makeText(this, "余额保存失败", Toast.LENGTH_LONG).show();
            render();
        });
        row("清空测试余额", () -> new AlertDialog.Builder(this)
            .setTitle("清空测试余额？")
            .setMessage("将清空此测试版当前可用的全部娱乐时间，包括工作获得的时间。仅用于零余额验证。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空", (d, w) -> {
                prepareChange(); ledger.engine.balanceMs = 0;
                if (!ledger.save()) Toast.makeText(this, "余额保存失败", Toast.LENGTH_LONG).show();
                render();
            }).show());
        section("测试说明");
        paragraph("先确认保护实际运行，再验证小红书消费与零余额阻止。\n手动停止后保持停止；白天每 4 小时最多静默提醒一次，系统可能延迟。\n系统中断会尝试恢复；强行停止或厂商冻结时仍可能失效。\n不要同时运行旧版 WorkToEarn 的保护，两版账本独立。");
    }

    private Button row(String label, Runnable action) {
        Button b = CatUi.button(this, label, false, false, action);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        CatUi.add(content, b, -2, 8); return b;
    }
    private void section(String title) {
        TextView t = CatUi.text(this, title, 15, CatUi.MUTED, true);
        t.setGravity(Gravity.START); CatUi.add(content, t, -2, 24);
    }
    private TextView paragraph(String value) {
        TextView t = CatUi.text(this, value, 14, CatUi.MUTED, false);
        t.setGravity(Gravity.START); t.setLineSpacing(CatUi.dp(this, 4), 1);
        CatUi.add(content, t, -2, 12); return t;
    }
    private void openPage(String name) { startActivity(new Intent(this, MainActivity.class).putExtra("page", name)); }
    private void openWork() { startActivity(new Intent(this, WorkActivity.class)); }
    private SharedPreferences uiPreferences() { return getSharedPreferences("ui_preferences", MODE_PRIVATE); }

    private void showGuide(boolean first) {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("先工作，再安心刷一会儿")
            .setMessage("① 开始工作\n固定手机，让前置摄像头朝向你的肩部和躯干；可放在侧后方，无需露出人脸。在工作页手动开启监测，背影效果仍在试用验证。\n\n② 攒娱乐时间\n每轮累计 10 分钟有效工作，获得 5 分钟娱乐；之后每分钟再得 30 秒。连续离开或暂停达到 3 分钟，本轮进度归零；不足 3 分钟可续接。已到账奖励和历史工作保留，主动结束后重新开始一轮。\n\n③ 去小红书\n保护运行时按实际用时扣减，时间用完后限制使用。请先在设置完成必要授权。\n\n仅观察是否在场，不代表证明正在工作。画面在本机处理，不保存、不上传。")
            .setPositiveButton("知道了", null).create();
        if (first) dialog.setOnDismissListener(d -> uiPreferences().edit().putBoolean("cat_intro_seen_v2", true).apply());
        dialog.show();
    }
    private void showPrivacy() {
        new AlertDialog.Builder(this).setTitle("仅在本机处理")
            .setMessage("摄像头仅在工作页手动开始后运行，不保存照片或录像、不上传画面。基础在场观察不能证明正在工作。\n\n使用情况访问用于判断应用切换和用时；悬浮窗用于显示阻止页。不读取页面正文、输入内容或截图。\n\n余额与状态保存在本机，没有网络权限。这是可关闭、可绕过的自用测试版。")
            .setPositiveButton("知道了", null).show();
    }
    private void openXiaohongshu() {
        if (ledger.engine.balanceMs <= 0) {
            Toast.makeText(this, "还没有可用娱乐时间，先开始工作吧", Toast.LENGTH_SHORT).show(); return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(LedgerStore.TARGET);
        if (launch != null) startActivity(launch);
        else Toast.makeText(this, "未找到小红书，请确认已安装标准版", Toast.LENGTH_LONG).show();
    }
    private void prepareChange() {
        if (ProtectionService.current() != null) ProtectionService.current().prepareManualChange();
        else ledger.engine.resetObservation(System.currentTimeMillis());
    }
    private void toggleProtection() {
        if (!ledger.enabled && (!Permissions.usage(this) || !Permissions.overlay(this))) {
            Toast.makeText(this, "请先完成使用情况访问与悬浮窗授权", Toast.LENGTH_LONG).show(); return;
        }
        if (ledger.enabled) {
            if (ProtectionService.current() != null) ProtectionService.current().stopProtection();
            else {
                if (!ledger.setProtectionEnabled(false)) {
                    Toast.makeText(this, "停止状态保存失败，请重试", Toast.LENGTH_LONG).show(); return;
                }
                ledger.engine.resetObservation(System.currentTimeMillis()); ledger.save();
                stopService(new Intent(this, ProtectionService.class)); ProtectionRecovery.stopped(this);
            }
        } else {
            prepareChange();
            if (ledger.setProtectionEnabled(true)) { ProtectionRecovery.sync(this, false); startProtection(); }
            else Toast.makeText(this, "设置保存失败，未开启保护", Toast.LENGTH_LONG).show();
        }
        render();
    }

    private void render() {
        boolean usage = Permissions.usage(this), overlay = Permissions.overlay(this);
        ProtectionService service = ProtectionService.current();
        boolean running = service != null && SystemClock.elapsedRealtime() - service.heartbeatElapsed < 3_000;
        boolean restricted = getSystemService(android.app.ActivityManager.class).isBackgroundRestricted();
        String state; boolean healthy = false;
        if (!ledger.enabled) state = "未保护 · 用时不扣减，点此开启";
        else if (!usage || !overlay) state = "尚未保护 · 点此完成必要授权";
        else if (!running) state = "保护中断 · 点此检查运行设置";
        else if (!service.healthy) state = service.failure;
        else if (restricted) state = "后台运行受限 · 点此检查";
        else { state = "● 小红书保护运行中"; healthy = true; }
        if (status != null) {
            CatUi.setText(status, state); status.setTextColor(healthy ? 0xFF456447 : CatUi.RED);
            if ("home".equals(page)) {
                status.setContentDescription(state + "，点击进入设置");
                status.setBackground(CatUi.shape(this, healthy ? Color.TRANSPARENT : 0xFFFFE7D9, 10, Color.TRANSPARENT));
            }
        }
        if (balance != null) {
            CatUi.setText(balance, duration(ledger.engine.balanceMs, true));
            CatUi.setText(worked, "已工作 " + ledger.work.totalValidMs() / 60_000 + " 分钟");
            boolean hasTime = ledger.engine.balanceMs > 0;
            CatUi.setText(speech, hasTime ? "准了，去玩吧。" : "先工作，攒点时间。");
            CatUi.setText(seal, hasTime ? "猫总 · 准玩" : "猫总等你开工");
            CatUi.setText(workButton, ledger.work.active() ? "▶  继续工作" : "▶  开始工作");
            entertainment.setEnabled(hasTime);
        }
        if (permissions != null) {
            CatUi.setText(permissions, "使用情况访问：" + (usage ? "已授权" : "未授权")
                + "\n悬浮窗：" + (overlay ? "已授权" : "未授权")
                + "\n通知：" + (getSystemService(android.app.NotificationManager.class).areNotificationsEnabled() ? "已允许" : "未允许")
                + "\n监测服务：" + (running ? "运行中" : "未连接")
                + (restricted ? "\n系统已限制后台运行，请检查后台设置。" : "")
                + ("Xiaomi".equalsIgnoreCase(android.os.Build.MANUFACTURER)
                    ? "\n小米省电策略应选无限制；当前心跳正常不代表后台不会被冻结。" : ""));
            CatUi.setText(overlayPermission, overlay ? "悬浮窗已授权 · 查看设置  ›" : "开启悬浮窗权限  ›");
            CatUi.setText(toggle, ledger.enabled ? "停止保护" : "开启保护");
        }
        if (diagnostics != null) CatUi.setText(diagnostics,
            "可用余额：" + duration(ledger.engine.balanceMs, true)
            + "\n累计扣减：" + duration(ledger.engine.consumedMs, false)
            + "\n阻止界面次数：" + ledger.blocks
            + (ledger.workUpgradeCreditMs > 0 ? "\n历史规则补齐：" + duration(ledger.workUpgradeCreditMs, false) + "（已计入余额）" : "")
            + "\n监测中断记录：" + ledger.observationGaps + " 次"
            + "\n最近中断：" + duration(ledger.lastObservationGapMs, false) + "（含系统休眠等停顿）"
            + "\n" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE
            + "\n" + getString(R.string.app_version) + (ledger.note.isEmpty() ? "" : "\n" + ledger.note));
    }
    static String duration(long millis, boolean roundUp) {
        long seconds = Math.max(0, millis) / 1000;
        if (roundUp && millis > 0 && millis % 1000 > 0) seconds++;
        return seconds / 60 + " 分 " + String.format(java.util.Locale.ROOT, "%02d", seconds % 60) + " 秒";
    }
    private void requestNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 10);
        } else openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    private void startProtection() {
        try { startForegroundService(new Intent(this, ProtectionService.class)); }
        catch (RuntimeException ex) {
            ledger.note = "监测服务启动失败，请检查系统设置后重新打开测试版。"; ledger.save();
            Toast.makeText(this, ledger.note, Toast.LENGTH_LONG).show();
        }
    }
    private void openOverlaySettings() {
        if (!"Xiaomi".equalsIgnoreCase(android.os.Build.MANUFACTURER)) { openOverlayList(); return; }
        new AlertDialog.Builder(this)
            .setTitle(Permissions.overlay(this) ? R.string.overlay_settings_title : R.string.overlay_grant_title)
            .setMessage(R.string.overlay_xiaomi_steps)
            .setPositiveButton(R.string.overlay_open_app_settings, (dialog, which) -> {
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                catch (android.content.ActivityNotFoundException | SecurityException ex) {
                    Toast.makeText(this, R.string.overlay_settings_fallback, Toast.LENGTH_LONG).show(); openOverlayList();
                }
            }).setNegativeButton(android.R.string.cancel, null).show();
    }
    private void openOverlayList() {
        openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException ex) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            Toast.makeText(this, "请在系统设置中搜索对应权限", Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (ledger.save()) ProtectionRecovery.sync(this, true);
        if (ProtectionService.current() != null) ProtectionService.current().dismissForNavigation();
        handler.post(update);
    }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
}
