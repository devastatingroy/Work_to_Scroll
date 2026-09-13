package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnrecordedReminderTest {
    private static UnrecordedReminder.Feedback update(UnrecordedReminder r, boolean counting,
                                                     boolean noPerson, boolean sound, long now) {
        return r.update(true, true, counting, true, noPerson, sound, now);
    }

    @Test public void firstPauseAndEveryDoublingBoundaryHaveDistinctFeedback() {
        UnrecordedReminder reminder = new UnrecordedReminder();
        update(reminder, true, false, true, 0);
        assertEquals(UnrecordedReminder.Feedback.VOICE, update(reminder, false, false, true, 1_000));
        int ordinal = 1;
        for (long offset = 30_000; offset <= 1_920_000; offset *= 2) {
            assertEquals(UnrecordedReminder.Feedback.NONE, update(reminder, false, false, true, 999 + offset));
            UnrecordedReminder.Feedback feedback = update(reminder, false, false, true, 1_000 + offset);
            assertEquals(UnrecordedReminder.Feedback.REPEAT, feedback);
            assertTrue(feedback.speaks());
            assertTrue(feedback.flashes());
            assertEquals(++ordinal, reminder.ordinal());
            assertEquals(UnrecordedReminder.Feedback.NONE, update(reminder, false, false, true, 1_000 + offset));
        }
    }

    @Test public void actualRecoveryImmediatelyRearmsWithoutSixtySecondCooldown() {
        UnrecordedReminder reminder = new UnrecordedReminder();
        assertEquals(UnrecordedReminder.Feedback.VOICE, update(reminder, false, false, true, 1_000));
        update(reminder, true, false, true, 2_000);
        assertEquals(UnrecordedReminder.Feedback.VOICE, update(reminder, false, false, true, 3_000));
        assertEquals(1, reminder.ordinal());
        assertEquals(UnrecordedReminder.Feedback.NONE, update(reminder, false, false, true, 31_000));
        assertEquals(UnrecordedReminder.Feedback.REPEAT, update(reminder, false, false, true, 33_000));
    }

    @Test public void routineReturnWaitsForFreshEvaluationWithoutCatchup() {
        UnrecordedReminder reminder = new UnrecordedReminder();
        update(reminder, true, false, true, 1_000);
        assertEquals(UnrecordedReminder.Feedback.NONE, reminder.update(true, false, false, true, false, true, 2_000));
        assertEquals(UnrecordedReminder.Feedback.NONE, reminder.update(true, true, false, false, false, true, 301_000));
        assertEquals(UnrecordedReminder.Feedback.NONE, update(reminder, true, false, true, 302_000));
        assertEquals(UnrecordedReminder.Feedback.NONE, reminder.update(false, true, false, true, false, true, 303_000));
    }

    @Test public void disablingSoundDoesNotReplayTheSameInterruptionWhenReenabled() {
        UnrecordedReminder reminder = new UnrecordedReminder();
        assertEquals(UnrecordedReminder.Feedback.VISUAL, update(reminder, false, false, false, 1_000));
        assertEquals(UnrecordedReminder.Feedback.NONE, update(reminder, false, false, true, 2_000));
        assertEquals(UnrecordedReminder.Feedback.MUTED_REPEAT, update(reminder, false, false, false, 31_000));
        assertEquals(UnrecordedReminder.Feedback.NONE, update(reminder, false, false, true, 32_000));
        assertEquals(UnrecordedReminder.Feedback.REPEAT, update(reminder, false, false, true, 61_000));
    }

    @Test public void zeroPersonPauseDoesNotFlashOrRepeatWhenReasonChanges() {
        UnrecordedReminder r = new UnrecordedReminder();
        update(r, true, false, true, 0);
        assertEquals(UnrecordedReminder.Feedback.NONE, update(r, false, true, true, 4_000));
        assertFalse(r.abnormalPause());
        assertEquals(UnrecordedReminder.Feedback.NONE, update(r, false, false, true, 5_000));
        assertEquals(UnrecordedReminder.Feedback.NONE, update(r, false, false, true, 3_600_000));
    }

    @Test public void checkingDoesNotClearPauseOrRearmFeedback() {
        UnrecordedReminder r = new UnrecordedReminder();
        assertFalse(r.showPaused());
        update(r, false, false, true, 0);
        assertTrue(r.showPaused());
        assertEquals(UnrecordedReminder.Feedback.NONE, r.update(true, true, false, false, false, true, 1_000));
        assertTrue(r.abnormalPause());
        assertEquals(UnrecordedReminder.Feedback.REPEAT, r.update(true, true, false, false, false, true, 30_000));
        update(r, true, false, true, 31_000);
        assertFalse(r.abnormalPause());
    }

    @Test public void continuousHourContainsExactlyEightAlertsAndEightFlashSequences() {
        UnrecordedReminder r = new UnrecordedReminder();
        int voices = 0, flashes = 0;
        for (long now = 0; now <= 3_600_000; now += 500) {
            UnrecordedReminder.Feedback feedback = update(r, false, false, true, now);
            if (feedback.speaks()) voices++;
            if (feedback.flashes()) flashes++;
        }
        assertEquals(8, voices);
        assertEquals(8, flashes);
    }

    @Test public void mutedRepeatsStillFlashButDoNotSpeak() {
        UnrecordedReminder r = new UnrecordedReminder();
        update(r, false, false, false, 0);
        UnrecordedReminder.Feedback feedback = update(r, false, false, false, 30_000);
        assertEquals(UnrecordedReminder.Feedback.MUTED_REPEAT, feedback);
        assertTrue(feedback.flashes());
        assertFalse(feedback.speaks());
        assertFalse(UnrecordedReminder.Feedback.NONE.flashes());
    }

    @Test public void clearAbsenceSkipsRepeatWithoutRestartingAnExistingSequence() {
        UnrecordedReminder r = new UnrecordedReminder();
        update(r, false, false, true, 0);
        assertEquals(UnrecordedReminder.Feedback.NONE, update(r, false, true, true, 30_000));
        assertEquals(UnrecordedReminder.Feedback.NONE, update(r, false, false, true, 31_000));
        assertEquals(UnrecordedReminder.Feedback.REPEAT, update(r, false, false, true, 60_000));
        assertEquals(3, r.ordinal());
    }

    @Test public void lateTickCoalescesMissedSlotsInsteadOfQueuingSpeech() {
        UnrecordedReminder r = new UnrecordedReminder();
        update(r, false, false, true, 0);
        assertEquals(UnrecordedReminder.Feedback.REPEAT, update(r, false, false, true, 125_000));
        assertEquals(4, r.ordinal());
        assertEquals(UnrecordedReminder.Feedback.NONE, update(r, false, false, true, 126_000));
        assertEquals(UnrecordedReminder.Feedback.REPEAT, update(r, false, false, true, 240_000));
    }

    @Test public void backgroundDiscardsSequenceAndFreshFailureStartsNewOne() {
        UnrecordedReminder r = new UnrecordedReminder();
        update(r, false, false, true, 0);
        assertEquals(UnrecordedReminder.Feedback.NONE, r.update(true, false, false, true, false, true, 29_000));
        assertEquals(UnrecordedReminder.Feedback.NONE, r.update(true, true, false, false, false, true, 300_000));
        assertEquals(UnrecordedReminder.Feedback.VOICE, update(r, false, false, true, 301_000));
        assertEquals(1, r.ordinal());
        r.reset();
        assertFalse(r.showPaused());
        assertEquals(UnrecordedReminder.Feedback.NONE, r.update(false, true, false, true, false, true, 331_000));
    }

    @Test public void roundResetDoesNotRestartReminderSchedule() {
        UnrecordedReminder r = new UnrecordedReminder();
        WorkProgress work = new WorkProgress(5_000, 5_000, 0, 0, true, 0, 0, 1);
        update(r, false, false, true, 0);
        update(r, false, false, true, 30_000);
        update(r, false, false, true, 60_000);
        update(r, false, false, true, 120_000);
        work = work.pause(180_000, 1);
        assertEquals(0, work.sessionValidMs());
        assertEquals(5_000, work.totalValidMs());
        assertEquals(UnrecordedReminder.Feedback.NONE, r.update(work.active(), true, false, true, false, true, 180_000));
        assertEquals(UnrecordedReminder.Feedback.REPEAT, r.update(work.active(), true, false, true, false, true, 240_000));
        assertEquals(5, r.ordinal());
    }

    @Test public void scheduleSaturatesWithoutOverflowOrBusyLoop() {
        UnrecordedReminder r = new UnrecordedReminder();
        update(r, false, false, true, 0);
        assertEquals(UnrecordedReminder.Feedback.REPEAT, update(r, false, false, true, Long.MAX_VALUE));
        assertEquals(UnrecordedReminder.Feedback.NONE, update(r, false, false, true, Long.MAX_VALUE));
    }

    @Test public void realClockWeakGraceOnlyAlertsAtActualPause() {
        WorkClock c = new WorkClock();
        UnrecordedReminder r = new UnrecordedReminder();
        c.observe(0, 1); c.observe(1_000, 1);
        update(r, c.counting(), false, true, 1_000);
        for (long now = 2_000; now < 5_000; now += 1_000) {
            c.observe(now, WorkClock.WEAK_ONLY);
            assertEquals(UnrecordedReminder.Feedback.NONE, update(r, c.counting(), false, true, now));
        }
        c.observe(5_000, WorkClock.WEAK_ONLY);
        assertEquals(UnrecordedReminder.Feedback.VOICE, update(r, c.counting(), false, true, 5_000));
    }
}
