package com.roy.worktoscroll;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/** Opaque page in the app task: the target leaves the foreground without creating a second card. */
public final class BlockerActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    static final String EXTRA_EXHAUSTION = "exhaustion_event";
    private LedgerStore ledger;
    private BlockerView panel;
    private String pendingExhaustion;
    private final Runnable check = new Runnable() {
        @Override public void run() {
            // No accounting here. Credit and the persisted opt-out remain authoritative.
            if (!shouldRemain()) { finish(); return; }
            handler.postDelayed(this, ProtectionService.POLL_MS);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ledger = LedgerStore.get(this);
        if (!shouldRemain()) { finish(); return; }
        ProtectionService service = ProtectionService.current();
        pendingExhaustion = state == null ? getIntent().getStringExtra(EXTRA_EXHAUSTION) : null;
        panel = CatUi.blocker(this,
            () -> navigate(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)),
            this::goHome, service != null && service.hasExhaustedBalance());
        setContentView(panel);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goHome);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingExhaustion = intent.getStringExtra(EXTRA_EXHAUSTION);
        if (hasWindowFocus()) revealFeedback();
    }

    private boolean shouldRemain() {
        return ledger != null && ledger.enabled && ledger.engine.balanceMs == 0
            && Permissions.usage(this) && Permissions.overlay(this);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(check);
        check.run();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isFinishing() && shouldRemain()) {
            // Keep the immediate overlay until the real destination has window focus.
            revealFeedback();
            Log.i("WTS_BLOCK_PAGE", "PAGE_VISIBLE");
        }
    }

    private void revealFeedback() {
        if (isFinishing() || !shouldRemain()) return;
        ProtectionService service = ProtectionService.current();
        // Claim before dismissing the overlay; dismissal also cancels unshown pending events.
        boolean play = service != null && service.consumeExhaustion(pendingExhaustion);
        pendingExhaustion = null;
        if (service != null) service.dismissForNavigation();
        if (play && panel != null) {
            panel.playExhaustion();
            Log.i("WTS_BLOCK_PAGE", "EXHAUSTION_FEEDBACK");
        }
    }

    @Override protected void onPause() {
        pendingExhaustion = null;
        if (panel != null) panel.stopAnimation();
        handler.removeCallbacks(check);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (panel != null) panel.stopAnimation();
        super.onDestroy();
    }

    @Override public void onBackPressed() { goHome(); }

    private void goHome() {
        navigate(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void navigate(Intent destination) {
        try {
            startActivity(destination);
            // The blocker shares the product app's task. Remove only this page; removing
            // the whole task would also discard the main activity and its single recent card.
            finish();
        } catch (RuntimeException ex) {
            Log.w("WTS_BLOCK_PAGE", "NAVIGATION_FAILED " + ex.getClass().getSimpleName());
            Toast.makeText(this, "请使用系统主页手势返回桌面", Toast.LENGTH_LONG).show();
        }
    }

}
