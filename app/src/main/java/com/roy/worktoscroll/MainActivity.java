package com.roy.worktoscroll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Home and secondary pages retain the original protection and debug actions. */
public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LedgerStore ledger;
    private LinearLayout content;
    private TextView balance, status, diagnostics, worked, speech, seal;
    private Button workButton, toggle, usagePermission, overlayPermission, entertainment, repair;
    private TextView expiry, ratio, ratioDetail, protectionDetail, permissionSummary, backgroundSummary;
    private TextView usageState, overlayState, notificationState, backgroundState, bootRecoveryState, dailyDetail;
    private static final int SETTINGS_SURFACE = 0xFFFFFDF8, SETTINGS_BORDER = 0xFFE8E0D1;
    private static final int GOOD = 0xFF456447, WARNING = 0xFF946119;
    private android.widget.Switch dailySwitch;
    private boolean updatingDailySwitch;
    private android.widget.Switch nightSwitch;
    private TextView nightDetail;
    private boolean updatingNightSwitch;
    private ImageView homeCat;
    private Drawable waitingCat, approvedCat;
    private String page;
    private final Runnable update = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 500); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ledger = LedgerStore.get(this);
        BootRecoveryStatus.inspectOnAppOpen(this, ledger.enabled);
        page = getIntent().getStringExtra("page");
        content = CatUi.screen(this, false);
        if ("settings".equals(page)) buildSettings();
        else if ("permissions".equals(page)) buildPermissions();
        else if ("tools".equals(page)) buildTools();
        else {
            page = "home";
            buildHome();
            if (!uiPreferences().getBoolean("cat_intro_seen_v3", false)) {
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
        speech = CatUi.text(this, "假条呢？", 18, CatUi.INK, true);
        CatUi.add(content, speech, -2, 12);
        int imageHeight = Math.max(130, Math.min(240, getResources().getConfiguration().screenHeightDp - 620));
        homeCat = CatUi.image(this, R.drawable.cat_home_waiting);
        waitingCat = homeCat.getDrawable();
        approvedCat = CatUi.imageDrawable(this, R.drawable.cat_home_approved);
        CatUi.add(content, homeCat, imageHeight, 4);
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
        expiry = CatUi.text(this, "", 12, CatUi.MUTED, false);
        CatUi.add(ticket, expiry, -2, 6);
        CatUi.add(content, ticket, -2, 0);
        worked = CatUi.text(this, "", 17, CatUi.INK, false);
        worked.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_work_laptop, 0, 0, 0);
        worked.setCompoundDrawablePadding(CatUi.dp(this, 8));
        CatUi.add(content, worked, -2, 18);
        worked.getLayoutParams().width = -2;
        workButton = CatUi.button(this, "▶  开始工作", true, false, this::openWork);
        CatUi.add(content, workButton, -2, 20);
        entertainment = CatUi.button(this, "去" + BuildConfig.TARGET_NAME + "  ›", false, false, this::openTargetApp);
        try {
            android.graphics.drawable.Drawable icon = getPackageManager().getApplicationIcon(LedgerStore.TARGET);
            icon.setBounds(0, 0, CatUi.dp(this, 24), CatUi.dp(this, 24));
            android.text.SpannableString label = new android.text.SpannableString("  去" + BuildConfig.TARGET_NAME + "  ›");
            label.setSpan(new android.text.style.ImageSpan(icon, android.text.style.ImageSpan.ALIGN_BOTTOM), 0, 1, 0);
            entertainment.setText(label);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { /* Text remains usable without the target app. */ }
        entertainment.setContentDescription("去" + BuildConfig.TARGET_NAME);
        CatUi.add(content, entertainment, -2, 10);
    }

    private void buildSettings() {
        settingsHeader("设置");
        LinearLayout protection = settingsCard(14);
        settingsText(protection, BuildConfig.TARGET_NAME + "保护", 12, CatUi.MUTED, 0);
        status = settingsText(protection, "正在检查保护…", 24, GOOD, 10);
        protectionDetail = settingsText(protection, "", 14, CatUi.MUTED, 10);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        repair = settingsAction("恢复保护", this::repairProtection, true);
        actions.addView(repair, new LinearLayout.LayoutParams(0, -2, 1));
        toggle = settingsAction("停止保护", this::requestProtectionChange, false);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-2, -2);
        toggleParams.leftMargin = CatUi.dp(this, 10);
        actions.addView(toggle, toggleParams);
        CatUi.add(protection, actions, -2, 16);
        section("保护所需设置");
        LinearLayout access = settingsCard(10);
        settingsNavigation(access, "权限与后台保障", () -> openPage("permissions"));
        permissionSummary = settingsText(access, "", 13, GOOD, 5);
        backgroundSummary = settingsText(access, "", 13, WARNING, 5);
        access.setOnClickListener(v -> openPage("permissions"));
        section("娱乐规则");
        LinearLayout rules = settingsCard(10);
        ratio = settingsNavigation(rules, "兑换比例", this::chooseRatio);
        ratioDetail = settingsText(rules, "", 13, CatUi.MUTED, 5);
        settingsDivider(rules);
        dailySwitch = new android.widget.Switch(this);
        dailySwitch.setText("每天清零可用娱乐");
        dailySwitch.setTextColor(CatUi.INK);
        dailySwitch.setTextSize(16);
        dailySwitch.setMinHeight(CatUi.dp(this, 48));
        CatUi.add(rules, dailySwitch, -2, 4);
        dailyDetail = settingsText(rules, "", 13, CatUi.MUTED, 0);
        dailySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingDailySwitch) return;
            if (!ledger.setDailyEnabled(checked)) Toast.makeText(this, "设置保存失败，请重试", Toast.LENGTH_LONG).show();
            render();
        });
        settingsDivider(rules);
        nightSwitch = new android.widget.Switch(this);
        nightSwitch.setText("夜间放松");
        nightSwitch.setTextColor(CatUi.INK);
        nightSwitch.setTextSize(16);
        nightSwitch.setMinHeight(CatUi.dp(this, 48));
        CatUi.add(rules, nightSwitch, -2, 4);
        nightDetail = settingsText(rules, "", 13, CatUi.MUTED, 0);
        nightSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingNightSwitch) return;
            if (!ledger.setNightEnabled(checked)) Toast.makeText(this, "设置保存失败，请重试", Toast.LENGTH_LONG).show();
            render();
        });
        section("帮助与说明");
        LinearLayout help = settingsCard(10);
        settingsNavigation(help, "如何使用", () -> showGuide(false));
        settingsDivider(help);
        settingsNavigation(help, "隐私说明", this::showPrivacy);
        settingsDivider(help);
        settingsNavigation(help, "开发工具", () -> openPage("tools"));
        TextView footer = paragraph(getString(R.string.app_name) + " · 自用测试版");
        footer.setGravity(Gravity.CENTER); footer.setTextSize(12);
    }

    private void buildPermissions() {
        settingsHeader("权限与后台保障");
        paragraph("先完成必需授权，再核对后台设置，让保护在离开应用后也能持续。");
        permissionHeading("必需", "缺少任意一项，无法正常保护", CatUi.RED);
        LinearLayout required = settingsCard(10);
        usageState = permissionItem(required, "使用情况访问", "识别何时使用" + BuildConfig.TARGET_NAME + "，按实际用时扣减余额。");
        usagePermission = permissionLink(required, "查看系统设置  ›", () -> openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:" + getPackageName()))));
        settingsDivider(required);
        overlayState = permissionItem(required, "悬浮窗", "娱乐时间用完时，显示阻止界面。");
        overlayPermission = permissionLink(required, "查看系统设置  ›", this::openOverlaySettings);
        permissionHeading("重要", "后台运行保障", WARNING);
        LinearLayout background = settingsCard(10);
        backgroundState = permissionItem(background, "允许持续后台运行", "避免系统休眠应用，导致用时扣减或阻止失效。");
        permissionLink(background, isXiaomi() ? "核对小米后台设置  ›" : "核对后台设置  ›", this::openBackgroundSettings);
        if (isXiaomi()) {
            settingsDivider(background);
            bootRecoveryState = permissionItem(background, "允许开机自启动", "重启后恢复保护需要小米允许自启动；省电策略和最近任务锁定不能替代它。");
            permissionLink(background, "查看自启动说明  ›", this::showXiaomiAutostartGuide);
            settingsDivider(background);
            settingsText(background, "锁定最近任务", 16, CatUi.INK, 16);
            settingsText(background, "减少清理最近任务时保护被停止的风险。", 14, CatUi.MUTED, 9);
            permissionLink(background, "查看三步图解  ›", this::showXiaomiRecentsGuide);
        }
        TextView note = paragraph(isXiaomi()
            ? "小米请完成三项设置：省电策略选「无限制」、允许自启动，并锁定最近任务。应用无法自动确认这些厂商设置，当前运行中也不代表已配置。"
            : "请核对系统的后台运行与省电设置。当前运行中不代表后台能够持续运行。");
        note.setTextSize(13);
        permissionHeading("可选", "通知提醒", CatUi.MUTED);
        LinearLayout notification = settingsCard(10);
        notificationState = permissionItem(notification, "允许通知", "显示保护状态，以及停止保护后的开启提醒。关闭通知不影响开启保护。");
        permissionLink(notification, "查看通知设置  ›", this::requestNotifications);
    }

    private boolean isXiaomi() { return "Xiaomi".equalsIgnoreCase(android.os.Build.MANUFACTURER); }

    private void settingsHeader(String title) {
        CatUi.header(this, content, title, "‹ 返回", this::finish, null, null, false);
        LinearLayout header = (LinearLayout) content.getChildAt(content.getChildCount() - 1);
        header.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(CatUi.dp(this, 76), 1));
    }

    private void openBackgroundSettings() {
        new AlertDialog.Builder(this).setTitle("核对后台运行设置")
            .setMessage(isXiaomi() ? "进入本应用设置，找到省电策略并选择「无限制」。返回后仍需以系统实际设置为准，不能仅凭保护当前运行判断后台配置成功。"
                : "进入本应用设置，检查后台运行与省电限制。系统未报告受限不代表能够持续运行。")
            .setNegativeButton("取消", null)
            .setPositiveButton("进入本应用设置", (d, w) -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())))).show();
    }

    private void showXiaomiRecentsGuide() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        int padding = CatUi.dp(this, 16);
        scroll.setPadding(padding, 0, padding, 0);
        scroll.addView(new RecentsLockGuideView(this), new ScrollView.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("锁定 " + getString(R.string.app_name))
            .setView(scroll).setPositiveButton("知道了", null).show();
    }

    private void showXiaomiAutostartGuide() {
        new AlertDialog.Builder(this).setTitle("允许 " + getString(R.string.app_name) + " 自启动")
            .setMessage("手机重启后，保护需要由系统开机广播重新启动。小米默认可能阻止自启动，即使省电策略已经设为「无限制」。\n\n请打开系统设置，搜索「自启动」，找到 " + getString(R.string.app_name) + " 后允许。不同 HyperOS 版本的入口可能不同；若搜索不到，可在系统安全中心或应用管理中查找「自启动」。\n\n应用无法代为开启或可靠读取这项厂商设置。完成后请回到这里点「我已允许」，它只记录你的确认，不会伪装成系统已检测通过。")
            .setNegativeButton("稍后", null)
            .setNeutralButton("我已允许", (dialog, which) -> {
                uiPreferences().edit().putBoolean("xiaomi_autostart_confirmed", true).apply();
                render();
            })
            .setPositiveButton("打开系统设置", (dialog, which) -> openSettings(new Intent(Settings.ACTION_SETTINGS))).show();
    }

    private LinearLayout settingsCard(int top) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(CatUi.dp(this, 16), CatUi.dp(this, 14), CatUi.dp(this, 16), CatUi.dp(this, 14));
        box.setBackground(CatUi.shape(this, SETTINGS_SURFACE, 18, SETTINGS_BORDER));
        CatUi.add(content, box, -2, top); return box;
    }

    private TextView settingsText(LinearLayout parent, String text, int size, int color, int top) {
        TextView view = CatUi.text(this, text, size, color, false);
        view.setGravity(Gravity.START); view.setLineSpacing(CatUi.dp(this, 3), 1);
        CatUi.add(parent, view, -2, top); return view;
    }

    private void settingsDivider(LinearLayout parent) {
        View divider = new View(this); divider.setBackgroundColor(SETTINGS_BORDER);
        CatUi.add(parent, divider, 1, 8);
    }

    private TextView settingsNavigation(LinearLayout parent, String title, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(CatUi.dp(this, 44)); row.setFocusable(true);
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x227D5848),
            CatUi.shape(this, SETTINGS_SURFACE, 8, Color.TRANSPARENT), null));
        TextView label = CatUi.text(this, title, 16, CatUi.INK, false); label.setGravity(Gravity.START);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = CatUi.text(this, "", 13, CatUi.MUTED, false); value.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-2, -2);
        valueParams.leftMargin = CatUi.dp(this, 8); row.addView(value, valueParams);
        TextView arrow = CatUi.text(this, "›", 22, CatUi.MUTED, false);
        row.addView(arrow, new LinearLayout.LayoutParams(CatUi.dp(this, 20), -2));
        row.setOnClickListener(v -> action.run()); CatUi.add(parent, row, -2, 0);
        return value;
    }

    private void permissionHeading(String importance, String title, int color) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = CatUi.text(this, importance, 12, color, true);
        badge.setPadding(CatUi.dp(this, 6), CatUi.dp(this, 3), CatUi.dp(this, 6), CatUi.dp(this, 3));
        badge.setBackground(CatUi.shape(this, color == CatUi.RED ? 0xFFF9E7DF : color == WARNING ? 0xFFF8EBD4 : 0xFFEDE9E1, 5, Color.TRANSPARENT));
        row.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        TextView heading = CatUi.text(this, title, 14, CatUi.MUTED, false); heading.setGravity(Gravity.START);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1); labelParams.leftMargin = CatUi.dp(this, 8);
        row.addView(heading, labelParams); CatUi.add(content, row, -2, 24);
    }

    private TextView permissionItem(LinearLayout parent, String title, String reason) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = CatUi.text(this, title, 16, CatUi.INK, false); name.setGravity(Gravity.START);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = CatUi.text(this, "", 13, CatUi.MUTED, false);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
        stateParams.leftMargin = CatUi.dp(this, 8); row.addView(state, stateParams);
        CatUi.add(parent, row, -2, parent.getChildCount() == 0 ? 0 : 16);
        settingsText(parent, reason, 14, CatUi.MUTED, 9); return state;
    }

    private Button permissionLink(LinearLayout parent, String title, Runnable action) {
        Button link = settingsAction(title, action, false);
        link.setBackgroundColor(Color.TRANSPARENT); link.setTextColor(CatUi.RED);
        link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); link.setPadding(0, 0, 0, 0);
        CatUi.add(parent, link, -2, 4); return link;
    }

    private Button settingsAction(String title, Runnable action, boolean primary) {
        Button button = CatUi.button(this, title, primary, false, action);
        button.setTextSize(14); button.setMinHeight(CatUi.dp(this, 48)); button.setMinimumHeight(CatUi.dp(this, 48));
        button.setMinWidth(0); button.setMinimumWidth(0);
        styleSettingsAction(button, primary); return button;
    }

    private void styleSettingsAction(Button button, boolean primary) {
        if (Boolean.valueOf(primary).equals(button.getTag())) return;
        button.setTag(primary); button.setTextColor(primary ? Color.WHITE : CatUi.INK);
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x227D5848),
            CatUi.shape(this, primary ? CatUi.RED : SETTINGS_SURFACE, 11, primary ? CatUi.RED : SETTINGS_BORDER), null));
    }

    private void requestProtectionChange() {
        if (!ledger.enabled) { toggleProtection(); return; }
        new AlertDialog.Builder(this).setTitle("停止" + BuildConfig.TARGET_NAME + "保护？")
            .setMessage("停止后，不再扣减娱乐时间，也不再阻止" + BuildConfig.TARGET_NAME + "。已有余额保留。").setNegativeButton("取消", null)
            .setPositiveButton("停止保护", (d, w) -> { if (ledger.enabled) toggleProtection(); }).show();
    }

    private void repairProtection() {
        if (!ledger.enabled) { render(); return; }
        if (!Permissions.usage(this) || !Permissions.overlay(this)) { openPage("permissions"); return; }
        if (getSystemService(android.app.ActivityManager.class).isBackgroundRestricted()) { openPage("permissions"); return; }
        if (!ledger.save()) { Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_LONG).show(); return; }
        startProtection(); render();
    }

    private void chooseRatio() {
        int[] values = {10, 15, 20};
        String[] labels = {"10 分钟换 5 分钟", "15 分钟换 5 分钟", "20 分钟换 5 分钟"};
        int checked = ledger.work.selectedMinutes() == 15 ? 1 : ledger.work.selectedMinutes() == 20 ? 2 : 0;
        TextView rule = CatUi.text(this, "当前选择：" + WorkProgress.ruleText(ledger.work.selectedMinutes()) + "修改从下一轮生效。", 14, CatUi.MUTED, false);
        rule.setGravity(Gravity.START); rule.setPadding(CatUi.dp(this, 24), CatUi.dp(this, 12), CatUi.dp(this, 24), CatUi.dp(this, 12));
        new AlertDialog.Builder(this).setTitle("兑换比例")
            .setView(rule)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                if (ledger.selectMinutes(values[which])) {
                    Toast.makeText(this, ledger.work.active() && ledger.work.roundMinutes() != values[which]
                        ? "已保存，下轮工作生效" : "兑换比例已保存", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else Toast.makeText(this, "设置保存失败，请重试", Toast.LENGTH_LONG).show();
                render();
            }).setNegativeButton("取消", null).show();
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
        paragraph("先确认保护实际运行，再验证" + BuildConfig.TARGET_NAME + "消费与零余额阻止。\n手动停止后保持停止；白天每 4 小时最多静默提醒一次，系统可能延迟。\n系统中断会尝试恢复；强行停止或厂商冻结时仍可能失效。\n不要同时运行旧版 WorkToEarn 的保护，两版账本独立。");
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
            .setMessage("① 开始工作\n固定手机，让前置摄像头朝向你的肩部和躯干；可放在侧后方，无需露出人脸。在工作页手动开启监测，背影效果仍在试用验证。\n\n② 攒娱乐时间\n" + WorkProgress.ruleText(ledger.work.selectedMinutes()) + "连续离开或暂停达到 3 分钟，本轮进度归零；不足 3 分钟可续接。本轮重置不倒扣奖励，历史工作保留；主动结束后重新开始一轮。" + ledger.expiryDescription() + "修改比例从下一轮生效。\n\n③ 去" + BuildConfig.TARGET_NAME + "\n保护运行时按实际用时扣减，时间用完后限制使用。请先在设置完成必要授权。\n\n仅观察是否在场，不代表证明正在工作。画面在本机处理，不保存、不上传。")
            .setPositiveButton("知道了", null).create();
        if (first) dialog.setOnDismissListener(d -> uiPreferences().edit().putBoolean("cat_intro_seen_v3", true).apply());
        dialog.show();
    }
    private void showPrivacy() {
        new AlertDialog.Builder(this).setTitle("仅在本机处理")
            .setMessage("摄像头仅在工作页手动开始后运行，不保存照片或录像、不上传画面。基础在场观察不能证明正在工作。\n\n使用情况访问用于判断应用切换和用时；悬浮窗用于显示阻止页。不读取页面正文、输入内容或截图。\n\n余额与状态保存在本机，没有网络权限。这是可关闭、可绕过的自用测试版。")
            .setPositiveButton("知道了", null).show();
    }
    private void openTargetApp() {
        if (!ledger.refreshDailyReset()) {
            Toast.makeText(this, "余额保存失败，请稍后重试", Toast.LENGTH_LONG).show(); return;
        }
        if (ledger.engine.balanceMs <= 0) {
            Toast.makeText(this, "还没有可用娱乐时间，先开始工作吧", Toast.LENGTH_SHORT).show(); return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(LedgerStore.TARGET);
        if (launch != null) startActivity(launch);
        else Toast.makeText(this, BuildConfig.TARGET_MISSING_MESSAGE, Toast.LENGTH_LONG).show();
    }
    private void prepareChange() {
        ledger.refreshDailyReset();
        if (ProtectionService.current() != null) ProtectionService.current().prepareManualChange();
        else ledger.engine.resetObservation(System.currentTimeMillis());
    }
    private void toggleProtection() {
        if (!ledger.enabled && (!Permissions.usage(this) || !Permissions.overlay(this))) {
            openPage("permissions"); return;
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
        if (!ledger.refreshDailyReset()) {
            if (status != null) { CatUi.setText(status, "余额保存失败，请稍后重试"); status.setTextColor(CatUi.RED); }
            if (protectionDetail != null) CatUi.setText(protectionDetail, "无法确认当前保护状态，请稍后重试。");
            if (entertainment != null) entertainment.setEnabled(false);
            return;
        }
        if (ratio != null) {
            CatUi.setText(ratio, ledger.work.selectedMinutes() + " 分钟换 5 分钟");
            boolean pendingRatio = ledger.work.active() && ledger.work.roundMinutes() != ledger.work.selectedMinutes();
            ratioDetail.setVisibility(pendingRatio ? View.VISIBLE : View.GONE);
            CatUi.setText(ratioDetail, pendingRatio ? "本轮仍为 " + ledger.work.roundMinutes() + " 分钟换 5 分钟，新比例下轮生效。" : "");
            updatingDailySwitch = true;
            dailySwitch.setChecked(ledger.daily.enabled());
            updatingDailySwitch = false;
            CatUi.setText(dailyDetail, ledger.daily.enabled() ? "每天早晨 06:00 清空剩余时间；重新开启从下一个 06:00 生效，不立即清空。"
                : "已关闭，剩余娱乐时间不每日清零；关闭不会恢复已到期余额。");
        }
        if (nightSwitch != null) {
            updatingNightSwitch = true;
            nightSwitch.setChecked(ledger.night.enabled());
            updatingNightSwitch = false;
            String detail = ledger.nightDescription();
            if (ledger.night.enabled() && ledger.night.active(System.currentTimeMillis(), java.time.ZoneId.systemDefault()))
                detail += "\n今晚剩余 " + ledger.night.remainingMs() / 60_000 + " 分钟。";
            CatUi.setText(nightDetail, detail);
        }
        if (expiry != null) {
            expiry.setVisibility(ledger.daily.enabled() ? android.view.View.VISIBLE : android.view.View.GONE);
            if (ledger.daily.enabled()) {
                var now = java.time.LocalDate.now();
                var due = java.time.Instant.ofEpochMilli(ledger.daily.nextMs()).atZone(java.time.ZoneId.systemDefault());
                String day = due.toLocalDate().equals(now) ? "今天" : due.toLocalDate().equals(now.plusDays(1))
                    ? "明早" : due.toLocalDate().toString() + " ";
                CatUi.setText(expiry, "下次清零：" + day + " 06:00");
            }
            if (hasWindowFocus() && ledger.acknowledgeReset())
                Toast.makeText(this, "已按设置清零剩余娱乐时间", Toast.LENGTH_LONG).show();
        }
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
        else { state = "● " + BuildConfig.TARGET_NAME + "保护运行中"; healthy = true; }
        if (status != null && "home".equals(page)) {
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
            Drawable cat = hasTime ? approvedCat : waitingCat;
            if (homeCat.getDrawable() != cat) homeCat.setImageDrawable(cat);
            CatUi.setText(speech, hasTime ? "准了，去玩吧！" : "假条呢？");
            CatUi.setText(seal, hasTime ? "猫总 · 准玩" : "猫总等你开工");
            CatUi.setText(workButton, ledger.work.active() ? "▶  继续工作" : "▶  开始工作");
            entertainment.setEnabled(hasTime);
        }
        if (toggle != null) {
            renderProtectionCard(ProtectionUiState.from(ledger.enabled, usage, overlay, running,
                service != null && service.healthy, restricted), usage, overlay, service);
            int missing = (usage ? 0 : 1) + (overlay ? 0 : 1);
            CatUi.setText(permissionSummary, missing == 0 ? "✓ 两项必需权限已授权" : "! 缺少 " + missing + " 项必需授权");
            permissionSummary.setTextColor(missing == 0 ? GOOD : CatUi.RED);
            CatUi.setText(backgroundSummary, restricted ? "! 系统已限制后台运行" : "! 后台运行设置待核对");
        }
        if (usageState != null) {
            renderPermissionState(usageState, usage, "已授权", "未授权");
            renderPermissionState(overlayState, overlay, "已授权", "未授权");
            renderPermissionState(notificationState, getSystemService(android.app.NotificationManager.class).areNotificationsEnabled(), "已允许", "未允许");
            notificationState.setTextColor(getSystemService(android.app.NotificationManager.class).areNotificationsEnabled() ? GOOD : CatUi.MUTED);
            CatUi.setText(backgroundState, restricted ? "已受限" : "待核对"); backgroundState.setTextColor(WARNING);
            CatUi.setText(usagePermission, usage ? "查看系统设置  ›" : "去授权  ›");
            CatUi.setText(overlayPermission, overlay ? "查看系统设置  ›" : "去授权  ›");
        }
        if (bootRecoveryState != null) {
            BootRecoveryStatus.Snapshot recovery = BootRecoveryStatus.read(this);
            String recoveryText = recovery.description(ledger.enabled, BootRecoveryStatus.bootCount(this));
            boolean attention = BootRecoveryPolicy.needsAttention(recovery.outcome());
            boolean confirmed = uiPreferences().getBoolean("xiaomi_autostart_confirmed", false);
            CatUi.setText(bootRecoveryState, (confirmed ? "✓ 已由你确认" : "待核对") + " · " + recoveryText);
            bootRecoveryState.setTextColor(attention ? CatUi.RED : confirmed ? GOOD : WARNING);
        }
        if (diagnostics != null) CatUi.setText(diagnostics,
            "监测服务：" + (running ? "运行中" : "未连接")
            + (service != null && !service.healthy ? "\n运行异常：" + service.failure : "")
            + "\n可用余额：" + duration(ledger.engine.balanceMs, true)
            + "\n累计扣减：" + duration(ledger.engine.consumedMs, false)
            + "\n阻止界面次数：" + ledger.blocks
            + (ledger.workUpgradeCreditMs > 0 ? "\n历史规则补齐：" + duration(ledger.workUpgradeCreditMs, false) + "（已计入余额）" : "")
            + "\n监测中断记录：" + ledger.observationGaps + " 次"
            + "\n最近中断：" + duration(ledger.lastObservationGapMs, false) + "（含系统休眠等停顿）"
            + "\n开机恢复：" + BootRecoveryStatus.read(this).description(ledger.enabled, BootRecoveryStatus.bootCount(this))
            + "\n" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE
            + "\n" + getString(R.string.app_version) + (ledger.note.isEmpty() ? "" : "\n" + ledger.note));
    }

    private void renderPermissionState(TextView view, boolean allowed, String yes, String no) {
        CatUi.setText(view, allowed ? "✓ " + yes : no); view.setTextColor(allowed ? GOOD : CatUi.RED);
    }

    private void renderProtectionCard(ProtectionUiState state, boolean usage, boolean overlay, ProtectionService service) {
        String title, detail, action = ""; int color = CatUi.RED;
        switch (state) {
            case STOPPED:
                title = "保护已停止"; color = CatUi.MUTED;
                detail = "当前不扣减娱乐时间，也不阻止" + BuildConfig.TARGET_NAME + "。已有余额保留。"; break;
            case MISSING_PERMISSION:
                title = "保护未生效"; action = "补齐必要授权";
                detail = !usage && !overlay ? "缺少使用情况访问和悬浮窗授权，无法正常保护。"
                    : !usage ? "缺少使用情况访问授权，无法正常识别用时并扣减余额。" : "缺少悬浮窗授权，无法正常显示阻止界面。"; break;
            case INTERRUPTED:
                title = "保护已中断"; detail = "未检测到保护运行，请尝试恢复。"; action = "恢复保护"; break;
            case FAILED:
                title = "保护运行异常"; detail = service == null || service.failure.isEmpty() ? "保护未能正常运行，请尝试恢复。" : service.failure;
                action = "恢复保护"; break;
            case BACKGROUND_RESTRICTED:
                title = "后台运行受限"; detail = "系统已限制后台运行，持续保护可能中断。";
                action = "检查后台设置"; color = WARNING; break;
            default:
                title = "● 保护运行中"; detail = "使用" + BuildConfig.TARGET_NAME + "时扣减娱乐时间，余额用完后阻止继续使用。"; color = GOOD;
        }
        CatUi.setText(status, title); status.setTextColor(color); CatUi.setText(protectionDetail, detail);
        repair.setVisibility(action.isEmpty() ? View.GONE : View.VISIBLE); CatUi.setText(repair, action);
        CatUi.setText(toggle, ledger.enabled ? "停止保护" : "开启保护"); styleSettingsAction(toggle, !ledger.enabled);
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
        resumeShortExternalReturn();
        handler.post(update);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resumeShortExternalReturn();
    }
    private void resumeShortExternalReturn() {
        Intent intent = getIntent();
        boolean launcherHome = "home".equals(page) && Intent.ACTION_MAIN.equals(intent.getAction())
            && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
        if (!launcherHome || !ledger.work.active()) return;
        boolean resume = MonitoringReturnTicket.consume(ledger.work.startedWallMs(), SystemClock.elapsedRealtime());
        if (!resume || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        startActivity(new Intent(this, WorkActivity.class)
            .putExtra(WorkActivity.EXTRA_RESUME_SHORT_EXTERNAL_RETURN, true));
    }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
}
