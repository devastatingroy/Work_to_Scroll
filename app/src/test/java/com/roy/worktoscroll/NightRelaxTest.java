package com.roy.worktoscroll;

import org.junit.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class NightRelaxTest {
    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");
    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(SH).toInstant().toEpochMilli();
    }

    @Test public void activeSpansMidnightWindow() {
        NightRelax n = NightRelax.initial(SH);
        assertTrue(n.active(at(2026, 1, 15, 22, 0), SH));   // start boundary included
        assertTrue(n.active(at(2026, 1, 15, 23, 0), SH));
        assertTrue(n.active(at(2026, 1, 16, 2, 0), SH));
        assertTrue(n.active(at(2026, 1, 16, 8, 29), SH));
        assertFalse(n.active(at(2026, 1, 16, 8, 30), SH));  // end boundary excluded
        assertFalse(n.active(at(2026, 1, 15, 12, 0), SH));
    }

    @Test public void windowStartKeyIsStableAcrossTheNight() {
        NightRelax n = NightRelax.initial(SH);
        long expected = at(2026, 1, 15, 22, 0);
        assertEquals(expected, n.windowStartKey(at(2026, 1, 15, 23, 30), SH));
        assertEquals(expected, n.windowStartKey(at(2026, 1, 16, 1, 0), SH));
        assertEquals(expected, n.windowStartKey(at(2026, 1, 16, 8, 0), SH));
    }

    @Test public void nextEdgePicksTheComingBoundary() {
        NightRelax n = NightRelax.initial(SH);
        assertEquals(at(2026, 1, 16, 8, 30), n.nextEdge(at(2026, 1, 15, 23, 0), SH));
        assertEquals(at(2026, 1, 15, 22, 0), n.nextEdge(at(2026, 1, 15, 12, 0), SH));
        assertEquals(at(2026, 1, 16, 8, 30), n.nextEdge(at(2026, 1, 16, 2, 0), SH));
    }

    @Test public void refillsOncePerNightAndKeepsRemainingOnRestart() {
        NightRelax n = NightRelax.initial(SH).refreshed(at(2026, 1, 15, 23, 0), SH);
        assertEquals(2_700_000L, n.remainingMs());
        assertEquals(at(2026, 1, 15, 22, 0), n.windowStartMs());
        NightRelax spent = n.withRemaining(600_000);
        // Same night, later time (and a restart): remaining is preserved, not refilled.
        assertSame(spent, spent.refreshed(at(2026, 1, 16, 2, 0), SH));
        // Next night: a fresh window refills to the full allowance.
        NightRelax nextNight = spent.refreshed(at(2026, 1, 16, 23, 0), SH);
        assertEquals(2_700_000L, nextNight.remainingMs());
        assertEquals(at(2026, 1, 16, 22, 0), nextNight.windowStartMs());
    }

    @Test public void outsideWindowDoesNotRefillOrChange() {
        NightRelax spent = NightRelax.initial(SH).refreshed(at(2026, 1, 15, 23, 0), SH).withRemaining(0);
        assertSame(spent, spent.refreshed(at(2026, 1, 16, 12, 0), SH));
    }

    @Test public void remainingClampsToAllowance() {
        NightRelax n = NightRelax.initial(SH);
        assertEquals(2_700_000L, n.withRemaining(9_999_999L).remainingMs());
        assertEquals(0, n.withRemaining(-5).remainingMs());
    }

    @Test public void reenablingForcesRefillNextWindow() {
        NightRelax off = NightRelax.initial(SH).refreshed(at(2026, 1, 15, 23, 0), SH)
            .withRemaining(120_000).withEnabled(false);
        assertFalse(off.enabled());
        NightRelax on = off.withEnabled(true);
        assertEquals(0, on.windowStartMs());   // forces refill on next entry
        NightRelax refilled = on.refreshed(at(2026, 1, 15, 23, 30), SH);
        assertEquals(2_700_000L, refilled.remainingMs());
    }

    @Test public void disabledWindowStaysInactiveForRouting() {
        NightRelax off = NightRelax.initial(SH).withEnabled(false);
        // active() is a pure time test; callers gate with enabled(), which is false here.
        assertFalse(off.enabled());
    }
}
