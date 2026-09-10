package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class CameraRecoveryTest {
    @Test public void aNewProcessNeverRestoresCameraIntentFromWorkProgress() {
        CameraRecovery recovery = new CameraRecovery();
        assertFalse(recovery.wanted());
        assertFalse(recovery.beginAttempt(100, true));
    }

    @Test public void interruptionWaitsForAllConditionsAndStartsOnlyOnce() {
        CameraRecovery r = new CameraRecovery();
        r.request();
        assertFalse(r.beginAttempt(0, false));
        assertTrue(r.beginAttempt(100, true));
        assertFalse(r.beginAttempt(100, true));
        r.freshFrame(200);
        r.suspend(); r.suspend();
        assertTrue(r.wanted());
        assertFalse(r.beginAttempt(1_000, false));
        assertTrue(r.beginAttempt(2_000, true));
        assertFalse(r.beginAttempt(2_001, true));
    }

    @Test public void manualStopCancelsWaitingAndFutureFocusCallbacks() {
        CameraRecovery r = new CameraRecovery();
        r.request(); r.beginAttempt(0, true); r.retry(100, "occupied");
        r.stop(); r.suspend(); r.freshFrame(200); r.retry(300, "late callback");
        assertFalse(r.beginAttempt(9_000, true));
        assertEquals(CameraRecovery.State.IDLE, r.state());
    }

    @Test public void cameraWithoutFramesStopsAfterThreeFailuresAndAlertsOnce() {
        CameraRecovery r = new CameraRecovery();
        r.request();
        assertTrue(r.beginAttempt(0, true));
        assertFalse(r.timedOut(7_999)); assertTrue(r.timedOut(8_000));
        r.retry(8_000, "no frames");
        assertFalse(r.beginAttempt(8_999, true)); assertTrue(r.beginAttempt(9_000, true));
        r.retry(17_000, "no frames");
        assertFalse(r.beginAttempt(18_999, true)); assertTrue(r.beginAttempt(19_000, true));
        r.retry(27_000, "no frames");
        assertTrue(r.failed()); assertEquals("no frames", r.failureReason());
        assertTrue(r.takeAlert()); assertFalse(r.takeAlert());
        r.suspend(); assertFalse(r.beginAttempt(60_000, true));
        r.fail("no frames"); assertFalse(r.takeAlert());
        r.request(); assertTrue(r.beginAttempt(61_000, true));
    }

    @Test public void oneStrayFrameAndFocusChangesCannotResetFailureBudget() {
        CameraRecovery r = new CameraRecovery();
        r.request(); r.beginAttempt(0, true); r.retry(10, "occupied");
        r.suspend(); r.beginAttempt(2_000, true); r.freshFrame(2_100);
        assertEquals(1, r.failures());
        r.retry(10_100, "stalled");
        r.beginAttempt(12_100, true); r.freshFrame(12_200);
        r.retry(20_200, "stalled");
        assertTrue(r.failed());
    }

    @Test public void healthyFramesResetBudgetRegardlessOfWhetherAnyoneIsPresent() {
        CameraRecovery r = new CameraRecovery();
        r.request(); r.beginAttempt(0, true); r.retry(10, "occupied");
        r.beginAttempt(2_000, true);
        for (long t = 2_000; t <= 7_000; t += 500) r.freshFrame(t);
        assertEquals(0, r.failures());
        assertFalse(r.timedOut(14_999)); assertTrue(r.timedOut(15_000));
    }

    @Test public void gapsInResultsCannotPassTheStableWindow() {
        CameraRecovery r = new CameraRecovery();
        r.request(); r.beginAttempt(0, true); r.retry(10, "occupied");
        r.beginAttempt(2_000, true); r.freshFrame(2_000); r.freshFrame(7_000);
        assertEquals(1, r.failures());
    }

    @Test public void sporadicFramesCannotKeepRecoveryPendingForever() {
        CameraRecovery r = new CameraRecovery();
        r.request(); r.beginAttempt(0, true);
        for (long t = 0; t <= 28_000; t += 7_000) r.freshFrame(t);
        assertFalse(r.timedOut(29_999));
        assertTrue(r.timedOut(30_000));
        r.retry(30_000, "unstable frames");
        assertTrue(r.failed());
    }

    @Test public void failedRecoveryCannotBeRevivedByLateFrameOrLifecycleCallback() {
        CameraRecovery r = new CameraRecovery();
        r.request(); r.beginAttempt(0, true); r.fail("save failed");
        r.freshFrame(1_000); r.suspend();
        assertTrue(r.failed()); assertFalse(r.beginAttempt(5_000, true));
    }

    @Test public void automaticResumeDoesNotCreditTheInterruptionOrConfirmationWindow() {
        CameraRecovery r = new CameraRecovery(); WorkClock c = new WorkClock();
        r.request(); r.beginAttempt(0, true);
        c.observe(0, 1); c.observe(1_000, 1);
        assertEquals(500, c.observe(1_500, 1));
        r.suspend(); c.interrupt();
        r.beginAttempt(60_000, true); r.freshFrame(60_000);
        assertEquals(0, c.observe(60_000, 1));
        assertEquals(0, c.observe(61_000, 1));
        assertEquals(500, c.observe(61_500, 1));
    }

    @Test public void retriesDoNotMoveThreeMinuteDeadlineOrDuplicateRewards() {
        WorkProgress work = new WorkProgress(599_500, 599_500, 0, 1, true, -1, 10_000, 1);
        work = work.pause(10_000, 1);
        CameraRecovery r = new CameraRecovery(); r.request();
        for (long t : new long[] {20_000, 80_000, 189_999}) {
            r.suspend(); r.beginAttempt(t, true);
            work = work.begin(1, t, 1).pause(t, 1);
            assertEquals(10_000, work.interruptedAtElapsedMs());
            assertEquals(599_500, work.sessionValidMs());
            assertEquals(0, work.sessionRewardMs());
        }
        WorkProgress shortReturn = work.observe(500, true, 189_999, 1);
        assertEquals(300_000, shortReturn.rewardSince(work));
        assertEquals(0, shortReturn.observe(0, true, 189_999, 1).rewardSince(shortReturn));
        WorkProgress longReturn = work.begin(1, 190_000, 1);
        assertEquals(0, longReturn.sessionValidMs());
        assertEquals(599_500, longReturn.totalValidMs());
        assertEquals(10_000, longReturn.interruptedAtElapsedMs());
    }
}
