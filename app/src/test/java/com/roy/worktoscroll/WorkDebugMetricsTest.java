package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorkDebugMetricsTest {
    private static final int BOOT = 7;

    @Test public void totalSinceBaselineSurvivesMultipleRoundResets() {
        WorkProgress first = new WorkProgress(250_000, 240_000, 0,
            1_000, true, -1, 242_000, BOOT);
        WorkProgress reset = new WorkProgress(250_000, 0, 0,
            1_000, true, 242_000, 422_000, BOOT);
        WorkProgress second = new WorkProgress(430_000, 180_000, 0,
            1_000, true, -1, 423_000, BOOT);
        WorkProgress resetAgain = new WorkProgress(430_000, 0, 0,
            1_000, true, 423_000, 603_000, BOOT);
        WorkProgress finalRound = new WorkProgress(505_000, 75_000, 0,
            1_000, true, -1, 604_000, BOOT);

        assertTrue(WorkDebugMetrics.roundReset(first, reset));
        assertTrue(WorkDebugMetrics.roundReset(second, resetAgain));
        assertEquals(495_000, WorkDebugMetrics.validSince(10_000, finalRound));
        assertEquals(75_000, finalRound.sessionValidMs());
    }

    @Test public void interruptionTransitionsAreDistinctFromRoundReset() {
        WorkProgress active = new WorkProgress(60_000, 60_000, 0,
            100, true, -1, 61_000, BOOT);
        WorkProgress paused = new WorkProgress(60_000, 60_000, 0,
            100, true, 61_000, 61_000, BOOT);
        WorkProgress resumed = new WorkProgress(60_000, 60_000, 0,
            100, true, -1, 62_000, BOOT);

        assertTrue(WorkDebugMetrics.interruptionStarted(active, paused));
        assertTrue(WorkDebugMetrics.interruptionEnded(paused, resumed));
        assertFalse(WorkDebugMetrics.roundReset(active, paused));
    }

    @Test public void wallDurationNeverGoesNegative() {
        assertEquals(2_500, WorkDebugMetrics.wallDuration(1_000, 3_500));
        assertEquals(0, WorkDebugMetrics.wallDuration(3_500, 1_000));
    }
}
