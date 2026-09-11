package com.roy.worktoscroll;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.ServiceInfo;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.lang.ref.WeakReference;

public final class ProtectionService extends Service {
    private static final String CHANNEL = "overlay_protection";
    private static final String ACTION_STOP = "com.roy.worktoscroll.overlaytest.STOP";
    private static final int NOTIFICATION_ID = 1;
    // Initial experiment values, not an accuracy promise. Leave time for UsageEvents delivery.
    static final long POLL_MS = 500;
    static final long EVENT_LAG_MS = 750;
    private static WeakReference<ProtectionService> reference = new WeakReference<>(null);
    static ProtectionService current() { return reference.get(); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LedgerStore ledger;
    private final ExhaustionFeedback exhaustion = new ExhaustionFeedback();
    private long feedbackResetBoundary;
    private View blocker;
    private AudioFocusRequest blockerAudioFocus;
    private long lastWall;
    private long lastElapsed;
    private long dismissUntilElapsed;
    private boolean monitoring;
    private boolean reconcileObservation = true;
    long heartbeatElapsed;
    boolean healthy;
    String failure = "";

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            refresh();
            if (monitoring) handler.postDelayed(this, POLL_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        reference = new WeakReference<>(this);
        ledger = LedgerStore.get(this);
        feedbackResetBoundary = ledger.daily.lastMs();
        lastWall = System.currentTimeMillis();
        lastElapsed = SystemClock.elapsedRealtime();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "悬浮窗保护运行状态", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        boolean bootRecovery = intent != null && intent.getBooleanExtra(ProtectionRecovery.EXTRA_BOOT_RECOVERY, false);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopProtection();
            return START_NOT_STICKY;
        }
        Notification notification = notification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else startForeground(NOTIFICATION_ID, notification);
        } catch (RuntimeException ex) {
            if (bootRecovery) BootRecoveryStatus.record(this, BootRecoveryPolicy.Outcome.START_REJECTED);
            ledger.note = bootRecovery ? "开机恢复被系统拒绝，请检查小米自启动后再试。"
                : "监测服务启动失败，请检查系统设置后重新打开测试版。";
            ledger.save();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ledger.enabled || !Permissions.usage(this) || !Permissions.overlay(this)) {
            if (bootRecovery) BootRecoveryStatus.record(this, BootRecoveryPolicy.Outcome.MISSING_PERMISSION);
            ledger.note = "监测未启动，请检查使用情况访问和悬浮窗授权。";
            ledger.save();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!monitoring) {
            // The experimental service does not charge time when it was not running.
            ledger.engine.resetObservation(System.currentTimeMillis());
            ledger.note = "监测已启动；中断期间不补扣。";
            ledger.save();
            monitoring = true;
            handler.post(loop);
        }
        if (bootRecovery) BootRecoveryStatus.record(this, BootRecoveryPolicy.Outcome.STARTED);
        // Request system restart; the persisted user opt-out is checked on every start.
        return START_STICKY;
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
            new Intent(this, ProtectionService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name) + " 保护服务")
            .setContentText("点击查看保护状态 · 可随时停止")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "停止保护", stop).build())
            .build();
    }

    void stopProtection() {
        refresh();
        if (!ledger.setProtectionEnabled(false)) {
            healthy = false;
            failure = "停止状态保存失败，请返回应用重试";
            return;
        }
        ledger.engine.resetObservation(System.currentTimeMillis());
        ledger.save();
        monitoring = false;
        healthy = false;
        handler.removeCallbacks(loop);
        hideBlocker();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        ProtectionRecovery.stopped(this);
    }

    void refresh() {
        if (ledger == null) return;
        long previousHeartbeat = heartbeatElapsed;
        heartbeatElapsed = SystemClock.elapsedRealtime();
        if (ProtectionPolicy.observationInterrupted(previousHeartbeat, heartbeatElapsed)) {
            reconcileObservation = true;
            ledger.observationGaps++;
            ledger.lastObservationGapMs = Math.max(0, heartbeatElapsed - previousHeartbeat);
            ledger.note = "检测到监测中断，已重新核对前台；中断期间不补扣。请检查后台运行设置。";
            Log.w("WTS", "OBSERVATION_GAP ms=" + ledger.lastObservationGapMs);
        }
        long now = System.currentTimeMillis();
        long drift = Math.abs((now - lastWall) - (heartbeatElapsed - lastElapsed));
        lastWall = now;
        lastElapsed = heartbeatElapsed;
        try {
            if (!ledger.enabled || !Permissions.usage(this) || !Permissions.overlay(this)) {
                exhaustion.clear();
                healthy = false;
                failure = ledger.enabled ? "系统授权未开启，保护已失效" : "保护未开启";
                reconcileObservation = true;
                ledger.engine.resetObservation(now);
                ledger.save();
                hideBlocker();
                return;
            }
            if (drift > 2_000 || now < ledger.engine.cursorMs) {
                reconcileObservation = true;
                ledger.note = "系统时间发生变化：余额保留，已重建监测时间基准。";
            }
            long end = now - EVENT_LAG_MS;
            if (reconcileObservation) {
                exhaustion.clear();
                // Recent events establish current state only. Never charge pre-start/frozen time.
                ledger.engine.recoverObservation(queryTransitions(Math.max(0, end - 86_400_000L), end), end, now);
                reconcileObservation = false;
            } else {
                // Never charge yesterday's balance beyond its deadline.
                settleUntil(ledger.daily.enabled() && ledger.daily.nextMs() > 0
                    ? Math.min(end, ledger.daily.nextMs()) : end);
            }
            if (!ledger.refreshDailyReset()) throw new IllegalStateException("Daily reset save failed");
            settleUntil(end);
            healthy = ledger.save();
            syncExhaustion();
            if (!healthy) exhaustion.clear();
            failure = healthy ? "" : "余额保存失败，保护状态异常";
            boolean visible = getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
            if (visible && ledger.engine.targetActive
                    && ledger.engine.balanceMs == 0 && heartbeatElapsed >= dismissUntilElapsed) showBlocker();
            else hideBlocker();
        } catch (RuntimeException ex) {
            exhaustion.clear();
            healthy = false;
            failure = "监测中断，请返回应用检查权限并重新开启保护";
            ledger.note = failure;
            reconcileObservation = true;
            ledger.engine.resetObservation(now);
            ledger.save();
            hideBlocker();
            Log.e("WTS", "Protection failed: " + ex.getClass().getSimpleName());
        }
    }

    void settleBeforeExpiry(long deadline) {
        // Called by ledger from any foreground path. Unknown/frozen intervals stay uncharged.
        if (monitoring && healthy && heartbeatElapsed > 0
                && SystemClock.elapsedRealtime() - heartbeatElapsed < 5_000
                && Permissions.usage(this) && ledger.engine.cursorMs < deadline)
            settleUntil(deadline);
    }

    private void settleUntil(long end) {
        BalanceEngine e = ledger.engine;
        if (end <= e.cursorMs) return;
        syncExhaustion();
        long balanceBefore = e.balanceMs, consumedBefore = e.consumedMs;
        e.settle(queryTransitions(e.cursorMs, end), end);
        // Daily expiry is a separate event; never infer consumption from a generic zero balance.
        if (!ledger.daily.due(System.currentTimeMillis()))
            exhaustion.settled(balanceBefore, e.balanceMs, consumedBefore, e.consumedMs,
                SystemClock.elapsedRealtime());
    }

    private void syncExhaustion() {
        if (ledger.engine.balanceMs > 0 || ledger.daily.lastMs() != feedbackResetBoundary
                || ledger.daily.due(System.currentTimeMillis())) exhaustion.clear();
        feedbackResetBoundary = ledger.daily.lastMs();
    }

    boolean hasExhaustedBalance() {
        syncExhaustion();
        return ledger.enabled && healthy && ledger.engine.balanceMs == 0 && exhaustion.exhausted();
    }

    boolean consumeExhaustion(String id) {
        return hasExhaustedBalance() && exhaustion.consume(id, SystemClock.elapsedRealtime());
    }

    private ArrayList<BalanceEngine.Transition> queryTransitions(long begin, long end) {
        UsageEvents events = getSystemService(UsageStatsManager.class).queryEvents(begin, end);
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
        return transitions;
    }

    /** Used only from our visible test controls: settle old credit before applying a new grant. */
    void prepareManualChange() {
        refresh();
        exhaustion.clear();
        // The visible main activity is not Xiaohongshu. Close the short delivery-lag tail here.
        ledger.engine.resetObservation(System.currentTimeMillis());
        hideBlocker();
    }

    void dismissForNavigation() {
        exhaustion.discardPending();
        // The user's departure must win over the short, delayed usage-event tail.
        dismissUntilElapsed = SystemClock.elapsedRealtime() + EVENT_LAG_MS + POLL_MS;
        hideBlocker();
    }

    private void showBlocker() {
        if (blocker != null) return;
        View panel = CatUi.blocker(this, () -> {
            // Start directly from the visible, user-clicked overlay. Sending HOME first races
            // the activity launch and may leave the user on the launcher instead of our app.
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            // MainActivity.onResume removes the blocker only after the destination is visible.
        }, () -> {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                dismissForNavigation();
            } catch (RuntimeException ex) {
                android.widget.Toast.makeText(this, "请使用系统主页手势返回桌面", android.widget.Toast.LENGTH_LONG).show();
            }
        }, hasExhaustedBalance());
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Take touches inside the full overlay, but keep focus on the underlying activity
            // so showing the blocker does not fabricate an ACTIVITY_PAUSED / hide loop.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE);
        params.setTitle(getString(R.string.app_name) + " blocker");
        getSystemService(WindowManager.class).addView(panel, params);
        blocker = panel;
        requestBlockerAudioFocus();
        ledger.blocks++;
        ledger.save();
        Log.i("WTS_OVERLAY", "BLOCK_SHOWN elapsed=" + SystemClock.elapsedRealtime());
        // The visible overlay is immediate protection and a fallback if the OEM refuses
        // the activity launch. Only the new page's actual window focus removes it.
        panel.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener() {
            private boolean queued;
            @Override public void onDraw() {
                if (queued) return;
                queued = true;
                panel.post(() -> panel.getViewTreeObserver().removeOnDrawListener(this));
                // addView/post alone races Surface visibility: HyperOS accepts the visible
                // window route but rejects the SAW-only route. Allow the drawn frame to commit.
                panel.postDelayed(() -> requestBlockerPage(panel), 250);
            }
        });
    }

    private void requestBlockerPage(View panel) {
            if (blocker != panel || !ledger.enabled || ledger.engine.balanceMs != 0
                    || !ledger.engine.targetActive) return;
            try {
                startActivity(new Intent(this, BlockerActivity.class)
                    .putExtra(BlockerActivity.EXTRA_EXHAUSTION, exhaustion.pendingId(SystemClock.elapsedRealtime()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                Log.i("WTS_BLOCK_PAGE", "PAGE_REQUESTED");
                handler.postDelayed(() -> {
                    if (blocker == panel) Log.w("WTS_BLOCK_PAGE", "PAGE_NOT_VISIBLE_OVERLAY_RETAINED");
                }, 2_000);
            } catch (RuntimeException ex) {
                Log.w("WTS_BLOCK_PAGE", "PAGE_START_FAILED " + ex.getClass().getSimpleName());
            }
    }

    private void hideBlocker() {
        releaseBlockerAudioFocus();
        if (blocker == null) return;
        try { getSystemService(WindowManager.class).removeView(blocker); }
        finally { blocker = null; }
    }

    private void requestBlockerAudioFocus() {
        // A non-focusable overlay leaves the underlying video running. GAIN signals a
        // lasting media interruption (not ducking), with system fade-out on supported
        // Android players. This is shared audio focus, not a package-specific pause API.
        // Request once per visible blocker: never fight calls or other focus owners.
        try {
            AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(change ->
                    Log.i("WTS_AUDIO", "FOCUS_CHANGE value=" + change), handler)
                .build();
            blockerAudioFocus = request;
            int result = getSystemService(AudioManager.class).requestAudioFocus(request);
            Log.i("WTS_AUDIO", "BLOCK_FOCUS_REQUEST result=" + result);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) releaseBlockerAudioFocus();
        } catch (RuntimeException ex) {
            // Audio failure must not remove the visual blocker or interrupt accounting.
            Log.w("WTS_AUDIO", "BLOCK_FOCUS_FAILED " + ex.getClass().getSimpleName());
            releaseBlockerAudioFocus();
        }
    }

    private void releaseBlockerAudioFocus() {
        AudioFocusRequest request = blockerAudioFocus;
        if (request == null) return;
        blockerAudioFocus = null;
        try {
            int result = getSystemService(AudioManager.class).abandonAudioFocusRequest(request);
            Log.i("WTS_AUDIO", "BLOCK_FOCUS_RELEASE result=" + result);
        } catch (RuntimeException ex) {
            Log.w("WTS_AUDIO", "BLOCK_FOCUS_RELEASE_FAILED " + ex.getClass().getSimpleName());
        }
        // Never send PLAY on release: earning credit/returning home is not a play action.
    }

    @Override public void onDestroy() {
        exhaustion.clear();
        monitoring = false;
        handler.removeCallbacksAndMessages(null);
        if (ledger != null) {
            ledger.note = ledger.enabled ? "监测服务已停止，重新打开测试版可尝试恢复。" : "保护已停止。";
            ledger.save();
        }
        hideBlocker();
        reference.clear();
        super.onDestroy();
    }
}
