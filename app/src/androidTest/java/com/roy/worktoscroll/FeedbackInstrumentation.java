package com.roy.worktoscroll;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

/** Opt-in native presentation check. Simulates only the transient event, never grants/debits time. */
public final class FeedbackInstrumentation extends Instrumentation {
    private Activity blocker;
    private long balance, consumed, work;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            LedgerStore[] holder = new LedgerStore[1];
            runOnMainSync(() -> holder[0] = LedgerStore.get(getTargetContext()));
            LedgerStore ledger = holder[0];
            balance = ledger.engine.balanceMs; consumed = ledger.engine.consumedMs; work = ledger.work.totalValidMs();
            require(balance == 0 && !ledger.work.active(), "Requires idle work and existing zero balance; no ledger changes allowed");
            startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for (int i = 0; i < 30 && (ProtectionService.current() == null || !ProtectionService.current().healthy); i++)
                SystemClock.sleep(200);
            ProtectionService service = ProtectionService.current();
            require(service != null && service.healthy, "Protection service unavailable");
            blocker = startActivitySync(new Intent(getTargetContext(), BlockerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); SystemClock.sleep(350);
            require(findText(blocker.getWindow().getDecorView(), "暂无可用时间") != null, "Initial zero must stay neutral");
            snapshot("feedback-initial-zero.png");
            runOnMainSync(() -> blocker.finishAndRemoveTask());
            startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Field field = ProtectionService.class.getDeclaredField("exhaustion"); field.setAccessible(true);
            ExhaustionFeedback feedback = (ExhaustionFeedback) field.get(service);
            runOnMainSync(() -> feedback.settled(1, 0, 0, 1, SystemClock.elapsedRealtime()));
            String id = feedback.pendingId(SystemClock.elapsedRealtime());
            blocker = startActivitySync(new Intent(getTargetContext(), BlockerActivity.class)
                .putExtra(BlockerActivity.EXTRA_EXHAUSTION, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); SystemClock.sleep(300);
            TextView title = findText(blocker.getWindow().getDecorView(), "时间到了！");
            require(title != null, "Exhaustion title missing");
            require(title.getScaleX() > .9f, "Entry animation did not start");
            require(title.getLayout().getEllipsisCount(0) == 0, "Title clipped");
            snapshot("feedback-peak.png");
            SystemClock.sleep(1700);
            require(Math.abs(title.getScaleX() - .83f) < .01f, "Animation did not settle");
            require(!feedback.consume(id, SystemClock.elapsedRealtime()), "Event was not claimed once by activity");
            snapshot("feedback-stable.png");
            runOnMainSync(() -> blocker.recreate());
            waitForIdleSync(); SystemClock.sleep(500);
            require(feedback.pendingId(SystemClock.elapsedRealtime()) == null, "Recreation must not re-arm event");
            require(ledger.engine.balanceMs == balance && ledger.engine.consumedMs == consumed
                && ledger.work.totalValidMs() == work, "User accounting changed");
            result.putString("stream", "PASS: native zero state, simulated exhaustion entry, stable frame, event claimed once, recreation; accounting unchanged.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void snapshot(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        require(image != null, "Screenshot unavailable");
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getFilesDir(), name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { image.recycle(); }
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView t && text.contentEquals(t.getText())) return t;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findText(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
