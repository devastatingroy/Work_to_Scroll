package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorkClockTest {
    private WorkClock present() {
        WorkClock c = new WorkClock();
        assertEquals(0, c.observe(0, 1));
        assertEquals(0, c.observe(500, 1));
        assertEquals(0, c.observe(1000, 1));
        assertEquals(WorkClock.State.PRESENT, c.state());
        return c;
    }

    @Test public void confirmationIsNotBackfilledAndDuplicateFramesNeverCredit() {
        WorkClock c = present();
        assertEquals(500, c.observe(1500, 1));
        assertEquals(0, c.observe(1500, 1));
        assertEquals(0, c.observe(1400, 1));
        assertEquals(500, c.observe(2000, 1));
    }
    @Test public void sustainedAbsenceOnlyGetsBoundedGrace() {
        WorkClock c = present();
        long earned = 0;
        for (long t = 1500; t <= 6500; t += 500) earned += c.observe(t, 0);
        assertEquals(3500, earned); // 500 ms to first missing frame + 3 s grace
        assertEquals(WorkClock.State.AWAY, c.state());
        assertEquals(0, c.observe(7000, 1));
        assertEquals(0, c.observe(7500, 1));
        assertEquals(0, c.observe(8000, 1));
        assertEquals(500, c.observe(8500, 1));
    }
    @Test public void briefLookAwayDoesNotResetPresence() {
        WorkClock c = present();
        assertEquals(500, c.observe(1500, 0));
        assertEquals(500, c.observe(2000, 1));
        assertEquals(WorkClock.State.PRESENT, c.state());
    }
    @Test public void returnAfterGraceDeadlineCannotCreditPastDeadline() {
        WorkClock c = present();
        c.observe(1500, 0);
        c.observe(2500, 0);
        c.observe(3500, 0);
        c.observe(4400, 0);
        assertEquals(100, c.observe(4800, 1));
        assertEquals(WorkClock.State.CHECKING, c.state());
    }
    @Test public void frozenAnalysisNeverBackfillsTime() {
        WorkClock c = present();
        assertEquals(0, c.observe(100_000, 1));
        assertEquals(WorkClock.State.CHECKING, c.state());
        c.expire(102_000);
        assertEquals(WorkClock.State.PAUSED, c.state());
    }
    @Test public void interruptionNeedsFreshConfirmation() {
        WorkClock c = present();
        c.interrupt();
        assertEquals(0, c.observe(500_000, 1));
        assertEquals(0, c.observe(501_000, 1));
        assertEquals(500, c.observe(501_500, 1));
    }
    @Test public void multipleFacesPauseImmediately() {
        WorkClock c = present();
        assertEquals(0, c.observe(1500, 2));
        assertEquals(WorkClock.State.UNCERTAIN, c.state());
        assertEquals(0, c.observe(2000, 1));
    }
    @Test public void weakSingleHoldIsBoundedAndNeedsReliableConfirmationAgain() {
        WorkClock c = present();
        assertEquals(500, c.observe(1500, WorkClock.WEAK_SINGLE));
        assertEquals(WorkClock.State.WEAK_HOLD, c.state());
        long held = 0;
        for (long t = 2500; t <= 121_500; t += 1000) held += c.observe(t, WorkClock.WEAK_SINGLE);
        assertEquals(WorkClock.WEAK_HOLD_MS, held);
        assertEquals(WorkClock.State.WEAK_HOLD, c.state());
        assertEquals(121_500, c.expireWeakHold(121_501));
        assertEquals(WorkClock.State.UNCERTAIN, c.state());
        assertEquals(0, c.observe(122_000, 1));
        assertEquals(WorkClock.State.CHECKING, c.state());
        assertEquals(0, c.observe(123_000, 1));
        assertEquals(WorkClock.State.PRESENT, c.state());
    }
}
