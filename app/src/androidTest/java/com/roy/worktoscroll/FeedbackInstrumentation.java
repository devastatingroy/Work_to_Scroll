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
    private boolean pauseFeedback;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        pauseFeedback = args != null && "true".equals(args.getString("pauseFeedback"));
        start();
    }

    @Override public void onStart() {
        if (pauseFeedback) { checkPauseFeedback(); return; }
        Bundle result = new Bundle();
        try {
            LedgerStore[] holder = new LedgerStore[1];
            runOnMainSync(() -> holder[0] = LedgerStore.get(getTargetContext()));
            LedgerStore ledger = holder[0];
            balance = ledger.engine.balanceMs; consumed = ledger.engine.consumedMs; work = ledger.work.totalValidMs();
            require(balance == 0 && !ledger.work.active(), "Requires idle work and existing zero balance; no ledger changes allowed");
            Activity main = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for (int i = 0; i < 30 && (ProtectionService.current() == null || !ProtectionService.current().healthy); i++)
                SystemClock.sleep(200);
            ProtectionService service = ProtectionService.current();
            require(service != null && service.healthy, "Protection service unavailable");
            blocker = startActivitySync(new Intent(getTargetContext(), BlockerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); SystemClock.sleep(350);
            require(blocker.getTaskId() == main.getTaskId(), "Blocker must share the product app task");
            require(findText(blocker.getWindow().getDecorView(), "暂无可用时间") != null, "Initial zero must stay neutral");
            snapshot("feedback-initial-zero.png");
            runOnMainSync(() -> blocker.finish());
            main = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Field field = ProtectionService.class.getDeclaredField("exhaustion"); field.setAccessible(true);
            ExhaustionFeedback feedback = (ExhaustionFeedback) field.get(service);
            runOnMainSync(() -> feedback.settled(1, 0, 0, 1, SystemClock.elapsedRealtime()));
            String id = feedback.pendingId(SystemClock.elapsedRealtime());
            blocker = startActivitySync(new Intent(getTargetContext(), BlockerActivity.class)
                .putExtra(BlockerActivity.EXTRA_EXHAUSTION, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); SystemClock.sleep(300);
            require(blocker.getTaskId() == main.getTaskId(), "Repeated blocker must reuse the product app task");
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

    private void checkPauseFeedback() {
        Bundle result = new Bundle();
        Activity[] activity = new Activity[1];
        CuePlayer[] isolatedAudio = new CuePlayer[1];
        try {
            LedgerStore[] store = new LedgerStore[1];
            checkedOnMain(() -> store[0] = LedgerStore.get(getTargetContext()));
            LedgerStore ledger = store[0];
            WorkProgress original = ledger.work;
            long originalBalance = ledger.engine.balanceMs, originalConsumed = ledger.engine.consumedMs;
            require(!original.active(), "Requires an ended work session");
            ActivityMonitor homeMonitor = addMonitor(MainActivity.class.getName(), null, false);
            try (android.os.ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(
                    "am start -n " + getTargetContext().getPackageName() + "/com.roy.worktoscroll.MainActivity");
                    java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                byte[] buffer = new byte[1024];
                while (input.read(buffer) != -1) { /* Drain the normal launcher result. */ }
            }
            Activity home = waitForMonitorWithTimeout(homeMonitor, 10_000);
            removeMonitor(homeMonitor);
            require(home != null, "Home launch timed out");
            awaitFocus(home);
            ActivityMonitor workMonitor = addMonitor(WorkActivity.class.getName(), null, false);
            checkedOnMain(() -> home.startActivity(new Intent(home, WorkActivity.class)));
            activity[0] = waitForMonitorWithTimeout(workMonitor, 10_000);
            removeMonitor(workMonitor);
            require(activity[0] != null, "Work page launch timed out");
            awaitFocus(activity[0]);
            // Exercise the production View in isolation: the idle page correctly cancels
            // its own alerts every tick. Do not fake monitoring or change the user's ledger.
            PauseFlash[] flashHolder = new PauseFlash[1];
            checkedOnMain(() -> {
                flashHolder[0] = new PauseFlash(activity[0], event -> {});
                activity[0].addContentView(flashHolder[0], new ViewGroup.LayoutParams(-1, -1));
                isolatedAudio[0] = new CuePlayer(activity[0], (event, cue, detail) -> {});
            });
            PauseFlash flash = flashHolder[0];
            CuePlayer audio = isolatedAudio[0];
            require(!(boolean) field(activity[0], "capturing"), "Test must not start the camera");
            int[] repeats = {0};
            long started = SystemClock.elapsedRealtime();
            checkedOnMain(() -> {
                flash.start();
                try {
                    android.animation.ValueAnimator animator = (android.animation.ValueAnimator) field(flash, "animator");
                    animator.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override public void onAnimationRepeat(android.animation.Animator a) { repeats[0]++; }
                    });
                } catch (Exception e) { throw new RuntimeException(e); }
                audio.playUnrecordedReminder(false);
            });
            String[] status = {""};
            while (SystemClock.elapsedRealtime() - started < 1_000) {
                checkedOnMain(() -> status[0] = audio.status());
                if ("playing".equals(status[0]) || status[0].startsWith("suppressed")) break;
                SystemClock.sleep(20);
            }
            long latency = SystemClock.elapsedRealtime() - started;
            require("playing".equals(status[0]), "Speech did not start within 1s: " + status[0]);
            checkedOnMain(() -> {
                require(flash.running(), "Flash missing");
                require(!flash.isClickable() && !flash.isFocusable(), "Flash blocks interaction");
                View content = activity[0].findViewById(android.R.id.content);
                require(flash.getWidth() == content.getWidth() && flash.getHeight() == content.getHeight(),
                    "Flash does not cover app content");
            });
            SystemClock.sleep(3_400);
            checkedOnMain(() -> {
                require(repeats[0] == 2, "Expected three pulses, repeats=" + repeats[0]);
                require(!flash.running() && flash.getVisibility() == View.INVISIBLE, "Flash did not finish");
                audio.stopPauseAlert();
                flash.start();
                flash.start(); // No stacking/restart.
                flash.stop();
                require(!flash.running() && flash.getVisibility() == View.INVISIBLE, "Recovery did not cancel flash");
            });
            // These represent separate alerts, not a same-instant supersession. Android
            // may still report our just-stopped clip as active media for a short interval.
            android.media.AudioManager audioManager = activity[0].getSystemService(android.media.AudioManager.class);
            long mediaDeadline = SystemClock.elapsedRealtime() + 2_000;
            while (audioManager.isMusicActive() && SystemClock.elapsedRealtime() < mediaDeadline)
                SystemClock.sleep(50);
            require(!audioManager.isMusicActive(), "Active media prevents the second isolated speech check");
            checkedOnMain(() -> audio.playUnrecordedReminder(true));
            SystemClock.sleep(600);
            checkedOnMain(() -> {
                require("playing".equals(audio.status()), "Camera interruption phrase failed: " + audio.status());
                audio.stopDetectionCue("camera_rebound");
                require("playing".equals(audio.status()), "Binding must not cancel pause speech before counting recovers");
                audio.playCountingResumed(); // Must replace our playing pause clip, not suppress itself.
            });
            long resumeDeadline = SystemClock.elapsedRealtime() + 1_000;
            String[] resumeStatus = {""};
            while (SystemClock.elapsedRealtime() < resumeDeadline) {
                checkedOnMain(() -> resumeStatus[0] = audio.lastCue() + ":" + audio.status());
                if ("counting_resumed:playing".equals(resumeStatus[0])) break;
                SystemClock.sleep(20);
            }
            require("counting_resumed:playing".equals(resumeStatus[0]), "Resume phrase failed: " + resumeStatus[0]);
            checkedOnMain(() -> {
                audio.stopPauseAlert();
                require("playing".equals(audio.status()), "Counting ticks must not stop resume speech");
                audio.stopCountingResumed();
                require("stopped".equals(audio.status()), "A new pause did not stop resume speech");
                require(ledger.work.equals(original) && ledger.engine.balanceMs == originalBalance
                    && ledger.engine.consumedMs == originalConsumed, "User accounting changed");
                activity[0].finish();
            });
            result.putString("stream", "PASS: production flash covers app, three pulses, nonblocking, cancellation; pause/interruption/resume speech starts; resume replaces own pause audio and cancels; first speech latency="
                + latency + "ms; no camera started and accounting unchanged. Simulated presentation only.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            runOnMainSync(() -> {
                if (isolatedAudio[0] != null) isolatedAudio[0].stop();
                if (activity[0] != null) activity[0].finish();
            });
            result.putString("stream", "FAIL: " + error + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void awaitFocus(Activity activity) {
        boolean[] focused = {false};
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            checkedOnMain(() -> focused[0] = activity.hasWindowFocus());
            if (focused[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Page did not receive focus; keep phone unlocked and in the app");
    }

    /** Propagate assertion failures to the runner instead of crashing Android's main thread. */
    private void checkedOnMain(Runnable action) {
        Throwable[] failure = {null};
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError("Main-thread check failed: " + failure[0], failure[0]);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
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
