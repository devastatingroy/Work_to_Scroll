package com.roy.worktoscroll;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/** Opaque, separate task: the target really leaves the foreground while this page stays. */
public final class BlockerActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LedgerStore ledger;
    private final Runnable check = new Runnable() {
        @Override public void run() {
            // No accounting here. Credit and the persisted opt-out remain authoritative.
            if (!shouldRemain()) { finishAndRemoveTask(); return; }
            handler.postDelayed(this, ProtectionService.POLL_MS);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ledger = LedgerStore.get(this);
        if (!shouldRemain()) { finishAndRemoveTask(); return; }
        setContentView(CatUi.blocker(this,
            () -> navigate(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)),
            this::goHome));
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goHome);
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
            ProtectionService service = ProtectionService.current();
            if (service != null) service.dismissForNavigation();
            Log.i("WTS_BLOCK_PAGE", "PAGE_VISIBLE");
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(check);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
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
            finishAndRemoveTask();
        } catch (RuntimeException ex) {
            Log.w("WTS_BLOCK_PAGE", "NAVIGATION_FAILED " + ex.getClass().getSimpleName());
            Toast.makeText(this, "请使用系统主页手势返回桌面", Toast.LENGTH_LONG).show();
        }
    }

}
