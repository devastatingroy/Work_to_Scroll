package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnrecordedReminderTest {
    private static WorkProgress interruptedAt(long at) {
        return new WorkProgress(0, 0, 0, 1, true, at, at, 1);
    }

    @Test public void startsAtOneMinuteAndOnlyRemindsOnce() {
        UnrecordedReminder reminder = new UnrecordedReminder();
        WorkProgress work = interruptedAt(1_000);
        assertFalse(reminder.due(work, true, true, true, 60_999));
        assertTrue(reminder.due(work, true, true, true, 61_000));
        assertFalse(reminder.due(work, true, true, true, 61_500));
        assertFalse(reminder.due(work, true, true, true, 121_000));
    }

    @Test public void neverRemindsWhenUserPausedOrWorkResumes() {
        UnrecordedReminder reminder = new UnrecordedReminder();
        WorkProgress work = interruptedAt(1_000);
        assertFalse(reminder.due(work, false, true, true, 61_000));
        assertFalse(reminder.due(work, true, false, true, 61_000));
        assertFalse(reminder.due(work, true, true, false, 61_000));
        WorkProgress resumed = new WorkProgress(0, 0, 0, 1, true, -1, 61_000, 1);
        assertFalse(reminder.due(resumed, true, true, true, 121_000));
    }

    @Test public void backgroundReturnDefersOneReminderUntilItIsSafeToPlay() {
        UnrecordedReminder reminder = new UnrecordedReminder();
        WorkProgress work = interruptedAt(1_000);
        assertFalse(reminder.due(work, true, false, true, 301_000));
        assertTrue(reminder.due(work, true, true, true, 301_000));
        assertFalse(reminder.due(work, true, true, true, 301_500));
        assertFalse(reminder.due(work, true, true, true, 361_000));
    }

    @Test public void disablingSoundDoesNotReplayTheSameInterruptionWhenReenabled() {
        UnrecordedReminder reminder = new UnrecordedReminder();
        WorkProgress work = interruptedAt(1_000);
        assertFalse(reminder.due(work, true, true, false, 61_000));
        assertFalse(reminder.due(work, true, true, true, 61_500));
    }
}
