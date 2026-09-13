package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class CountingResumeCueTest {
    @Test public void firstStartDoesNotSayResume() {
        CountingResumeCue cue = new CountingResumeCue();
        assertFalse(cue.update(false, false, false));
        assertFalse(cue.update(true, false, false));
        assertFalse(cue.update(true, true, false));
        assertFalse(cue.update(true, true, false));
    }

    @Test public void actualPauseAndRecoveryAnnounceOnce() {
        CountingResumeCue cue = new CountingResumeCue();
        cue.update(true, true, false);
        assertFalse(cue.update(true, false, true));
        assertTrue(cue.awaitingResume());
        assertFalse(cue.update(true, false, false)); // Checking is still stopped.
        assertTrue(cue.update(true, true, false));
        assertFalse(cue.awaitingResume());
        assertFalse(cue.update(true, true, false));
    }

    @Test public void briefCountedGraceDoesNotCreateAResumeAnnouncement() {
        CountingResumeCue cue = new CountingResumeCue();
        WorkClock clock = new WorkClock();
        clock.observe(0, 1); clock.observe(1_000, 1);
        cue.update(true, clock.counting(), false);
        clock.observe(2_000, WorkClock.WEAK_ONLY);
        assertFalse(cue.update(true, clock.counting(), false));
        clock.observe(3_000, 1);
        assertFalse(cue.update(true, clock.counting(), false));
        clock.observe(4_000, 0);
        assertFalse(cue.update(true, clock.counting(), false));
        clock.observe(5_000, 1);
        assertFalse(cue.update(true, clock.counting(), false));
    }

    @Test public void weakPauseWaitsForReliableConfirmationNotFirstReliableFrame() {
        CountingResumeCue cue = new CountingResumeCue();
        WorkClock clock = new WorkClock();
        clock.observe(0, 1); clock.observe(1_000, 1);
        cue.update(true, clock.counting(), false);
        for (long now = 2_000; now <= 5_000; now += 1_000) {
            clock.observe(now, WorkClock.WEAK_ONLY);
            assertFalse(cue.update(true, clock.counting(), !clock.counting()));
        }
        clock.observe(6_000, 1);
        assertEquals(WorkClock.State.CHECKING, clock.state());
        assertFalse(cue.update(true, clock.counting(), false));
        clock.observe(7_000, 1);
        assertTrue(cue.update(true, clock.counting(), false));
        clock.observe(8_000, 1);
        assertFalse(cue.update(true, clock.counting(), false));
    }

    @Test public void initialExplicitInvalidStateCanRecoverButBindingAloneCannot() {
        CountingResumeCue cue = new CountingResumeCue();
        cue.update(true, false, false);
        assertFalse(cue.awaitingResume());
        cue.update(true, false, true);
        assertTrue(cue.update(true, true, false));
    }

    @Test public void backgroundOrManualPauseCanResumeWithoutAnAbnormalAlert() {
        CountingResumeCue cue = new CountingResumeCue();
        cue.update(true, true, false);
        cue.update(true, false, false);
        assertTrue(cue.awaitingResume());
        assertTrue(cue.update(true, true, false));
    }

    @Test public void endingWorkClearsPendingButRoundResetDoesNot() {
        CountingResumeCue cue = new CountingResumeCue();
        cue.update(true, true, false);
        cue.update(true, false, true);
        cue.update(true, false, true); // Three-minute reset leaves the work session active.
        assertTrue(cue.update(true, true, false));
        cue.update(true, false, true);
        cue.update(false, false, false);
        assertFalse(cue.update(true, true, false));
    }

    @Test public void reopeningAnInterruptedWorkSessionCanAnnounceResume() {
        CountingResumeCue cue = new CountingResumeCue();
        cue.update(true, false, true);
        assertFalse(cue.update(true, false, false));
        assertTrue(cue.update(true, true, false));
    }
}
