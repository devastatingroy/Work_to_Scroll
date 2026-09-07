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
    private Button toggle;
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
        text("GATE 1 · 手机限制闭环测试", 13, false);
        text("可用娱乐时间", 17, false);
        balance = text("00:00", 52, true);
        status = text("正在检查状态…", 18, true);
        permissions = text("", 14, false);
        button("1 · 授权使用情况访问", () -> openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:" + getPackageName()))));
        button("2 · 授权无障碍保护服务", () -> {
            openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        toggle = button("开启保护", () -> {
            if (!ledger.enabled && (!Permissions.usage(this) || !Permissions.accessibility(this))) {
                Toast.makeText(this, "请先完成上面两项系统授权", Toast.LENGTH_LONG).show();
                return;
            }
            prepareChange();
            ledger.enabled = !ledger.enabled;
            if (ledger.enabled) ledger.note = "";
            ledger.save();
            if (ProtectionService.current() != null) ProtectionService.current().refresh();
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
        text("① 两项授权完成后开启保护，零余额打开小红书。\n② 加入 5 分钟，验证连续或分段使用。\n③ 退出或锁屏后应停止扣减，耗尽后再次拦截。", 14, false);
        diagnostics = text("", 13, false);
        text("本机处理 · 无网络权限\n仅观察应用切换与锁屏状态，不读取页面正文、输入内容或截图。余额与状态保存在本机。\n\n这是可关闭、可绕过的自用原型。摄像头工作验证尚未接入。", 13, false);
    }

    private void prepareChange() {
        if (ProtectionService.current() != null) ProtectionService.current().prepareManualChange();
        else ledger.engine.resetObservation(System.currentTimeMillis());
    }

    private void render() {
        boolean usage = Permissions.usage(this);
        boolean access = Permissions.accessibility(this);
        ProtectionService service = ProtectionService.current();
        boolean running = service != null && SystemClock.elapsedRealtime() - service.heartbeatElapsed < 3_000;
        balance.setText(formatTime(ledger.engine.balanceMs));
        permissions.setText("使用情况访问：" + (usage ? "已授权" : "未授权")
            + "\n无障碍服务：" + (access ? "已授权" : "未授权")
            + "\n服务连接：" + (running ? "运行中" : "未连接"));
        if (!ledger.enabled) status.setText("保护未开启");
        else if (!usage || !access) status.setText("保护失效 · 请恢复系统授权");
        else if (!running) status.setText("保护失效 · 服务未连接");
        else if (!service.healthy) status.setText(service.failure);
        else status.setText(ledger.engine.balanceMs == 0 ? "保护运行中 · 零余额将拦截" : "保护运行中 · 按小红书用量扣减");
        toggle.setText(ledger.enabled ? "停止保护" : "开启保护");
        diagnostics.setText("累计扣减：" + formatTime(ledger.engine.consumedMs)
            + "　阻止界面次数：" + ledger.blocks
            + "\n" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
            + " · Android " + android.os.Build.VERSION.RELEASE
            + (ledger.note.isEmpty() ? "" : "\n" + ledger.note));
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
        if (ProtectionService.current() != null) ProtectionService.current().dismissForNavigation();
        handler.post(update);
    }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
}
