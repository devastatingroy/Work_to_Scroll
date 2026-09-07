package com.roy.worktoscroll;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.lang.ref.WeakReference;

public final class ProtectionService extends AccessibilityService {
    // Initial experiment values, not an accuracy promise. Leave time for UsageEvents delivery.
    static final long POLL_MS = 500;
    static final long EVENT_LAG_MS = 750;
    private static WeakReference<ProtectionService> reference = new WeakReference<>(null);
    static ProtectionService current() { return reference.get(); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LedgerStore ledger;
    private View blocker;
    private long lastWall;
    private long lastElapsed;
    private long dismissUntilElapsed;
    private long fastBlockUntilElapsed;
    long heartbeatElapsed;
    boolean healthy;
    String failure = "";

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override protected void onServiceConnected() {
        reference = new WeakReference<>(this);
        ledger = LedgerStore.get(this);
        lastWall = System.currentTimeMillis();
        lastElapsed = SystemClock.elapsedRealtime();
        handler.post(loop);
    }

    void refresh() {
        if (ledger == null) return;
        heartbeatElapsed = SystemClock.elapsedRealtime();
        long now = System.currentTimeMillis();
        long drift = Math.abs((now - lastWall) - (heartbeatElapsed - lastElapsed));
        lastWall = now;
        lastElapsed = heartbeatElapsed;
        try {
            if (!ledger.enabled || !Permissions.usage(this)) {
                healthy = false;
                failure = ledger.enabled ? "使用情况访问权限未开启，保护已失效" : "保护未开启";
                ledger.engine.resetObservation(now);
                ledger.save();
                hideBlocker();
                return;
            }
            if (drift > 2_000 || now < ledger.engine.cursorMs) {
                ledger.engine.resetObservation(now);
                ledger.note = "系统时间发生变化：余额保留，已重建监测时间基准。";
            }
            settleUntil(now - EVENT_LAG_MS);
            healthy = ledger.save();
            failure = healthy ? "" : "余额保存失败，保护状态异常";
            boolean visible = getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
            if (visible && (ledger.engine.targetActive || heartbeatElapsed < fastBlockUntilElapsed)
                    && ledger.engine.balanceMs == 0 && heartbeatElapsed >= dismissUntilElapsed) showBlocker();
            else hideBlocker();
        } catch (RuntimeException ex) {
            healthy = false;
            failure = "监测中断，请返回应用检查权限并重新开启保护";
            ledger.note = failure;
            ledger.engine.resetObservation(now);
            ledger.save();
            hideBlocker();
            Log.e("WTS", "Protection failed: " + ex.getClass().getSimpleName());
        }
    }

    private void settleUntil(long end) {
        BalanceEngine e = ledger.engine;
        if (end <= e.cursorMs) return;
        UsageEvents events = getSystemService(UsageStatsManager.class).queryEvents(e.cursorMs, end);
        if (events == null) throw new IllegalStateException("Usage events unavailable");
        var transitions = new ArrayList<BalanceEngine.Transition>();
        var event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            boolean target = LedgerStore.TARGET.equals(event.getPackageName());
            if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                transitions.add(new BalanceEngine.Transition(event.getTimeStamp(), target));
            } else if ((type == UsageEvents.Event.ACTIVITY_PAUSED && target)
                    || type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                    || type == UsageEvents.Event.KEYGUARD_SHOWN
                    || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                transitions.add(new BalanceEngine.Transition(event.getTimeStamp(), false));
            }
            // Discard other apps' metadata immediately; never read window contents or store history.
        }
        e.settle(transitions, end);
    }

    /** Used only from our visible test controls: settle old credit before applying a new grant. */
    void prepareManualChange() {
        refresh();
        // The visible main activity is not Xiaohongshu. Close the short delivery-lag tail here.
        ledger.engine.resetObservation(System.currentTimeMillis());
        hideBlocker();
    }

    void dismissForNavigation() {
        // The user's departure must win over the short, delayed usage-event tail.
        dismissUntilElapsed = SystemClock.elapsedRealtime() + EVENT_LAG_MS + POLL_MS;
        fastBlockUntilElapsed = 0;
        hideBlocker();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (ledger == null || !ledger.enabled || !Permissions.usage(this)) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        // Fast zero-balance interception; usage accounting remains independent of window messages.
        if (pkg != null && LedgerStore.TARGET.contentEquals(pkg) && ledger.engine.balanceMs == 0
                && getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            fastBlockUntilElapsed = SystemClock.elapsedRealtime() + EVENT_LAG_MS + POLL_MS;
            try {
                if (SystemClock.elapsedRealtime() >= dismissUntilElapsed) showBlocker();
            }
            catch (RuntimeException ex) { healthy = false; failure = "阻止界面未能显示，请重新开启保护"; }
        }
    }

    private void showBlocker() {
        if (blocker != null) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        int pad = Math.round(28 * getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad * 2, pad, pad * 2);
        panel.setBackgroundColor(Color.rgb(244, 246, 241));
        addText(panel, getString(R.string.app_name), 18);
        addText(panel, "娱乐时间已用完", 30);
        addText(panel, "工作 10 分钟，可获得 5 分钟娱乐时间。\n\n当前为 Gate 1 测试版，请回到应用手动加入测试余额。", 17);
        Button open = new Button(this);
        open.setText(R.string.return_to_app);
        open.setAllCaps(false);
        open.setOnClickListener(v -> {
            // Start directly from the visible, user-clicked overlay. Sending HOME first races
            // the activity launch and may leave the user on the launcher instead of our app.
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            // MainActivity.onResume removes the blocker only after the destination is visible.
        });
        panel.addView(open, new LinearLayout.LayoutParams(-1, -2));
        Button home = new Button(this);
        home.setText("回到桌面");
        home.setOnClickListener(v -> { dismissForNavigation(); performGlobalAction(GLOBAL_ACTION_HOME); });
        panel.addView(home, new LinearLayout.LayoutParams(-1, -2));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
        params.setTitle(getString(R.string.app_name) + " blocker");
        getSystemService(WindowManager.class).addView(panel, params);
        blocker = panel;
        ledger.blocks++;
        ledger.save();
        Log.i("WTS", "BLOCK_SHOWN elapsed=" + SystemClock.elapsedRealtime());
    }

    private void addText(LinearLayout panel, String message, int size) {
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(30, 58, 47));
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, 12, 0, 28);
        panel.addView(text, new LinearLayout.LayoutParams(-1, -2));
    }

    private void hideBlocker() {
        if (blocker == null) return;
        try { getSystemService(WindowManager.class).removeView(blocker); }
        finally { blocker = null; }
    }

    @Override public void onInterrupt() {
        healthy = false;
        failure = "无障碍服务被中断，请检查系统授权";
        hideBlocker();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (ledger != null) {
            ledger.note = "无障碍服务已停止，请检查权限及系统省电设置。";
            ledger.save();
        }
        hideBlocker();
        reference.clear();
        super.onDestroy();
    }
}
