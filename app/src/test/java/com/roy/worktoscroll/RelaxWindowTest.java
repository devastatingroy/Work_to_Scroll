package com.roy.worktoscroll;

import org.junit.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class RelaxWindowTest {
    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");
    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(SH).toInstant().toEpochMilli();
    }

    @Test public void activeSpansMidnightWindow() {
        RelaxWindow n = RelaxWindow.night(SH);
        assertTrue(n.active(at(2026, 1, 15, 22, 0), SH));   // start boundary included
        assertTrue(n.active(at(2026, 1, 15, 23, 0), SH));
        assertTrue(n.active(at(2026, 1, 16, 2, 0), SH));
        assertTrue(n.active(at(2026, 1, 16, 8, 29), SH));
        assertFalse(n.active(at(2026, 1, 16, 8, 30), SH));  // end boundary excluded
        assertFalse(n.active(at(2026, 1, 15, 12, 0), SH));
    }

    @Test public void noonWindowCoversLunchHourOnly() {
        RelaxWindow n = RelaxWindow.noon(SH);
        assertEquals(11 * 60, n.startMin());
        assertEquals(13 * 60, n.endMin());
        assertEquals(30 * 60_000L, n.allowanceMs());
        assertTrue(n.enabled());
        assertFalse(n.active(at(2026, 1, 15, 10, 59), SH));
        assertTrue(n.active(at(2026, 1, 15, 11, 0), SH));   // start boundary included
        assertTrue(n.active(at(2026, 1, 15, 12, 30), SH));
        assertFalse(n.active(at(2026, 1, 15, 13, 0), SH));  // end boundary excluded
        assertFalse(n.active(at(2026, 1, 15, 22, 30), SH));
    }

    @Test public void windowStartKeyIsStableAcrossTheNight() {
        RelaxWindow n = RelaxWindow.night(SH);
        long expected = at(2026, 1, 15, 22, 0);
        assertEquals(expected, n.windowStartKey(at(2026, 1, 15, 23, 30), SH));
        assertEquals(expected, n.windowStartKey(at(2026, 1, 16, 1, 0), SH));
        assertEquals(expected, n.windowStartKey(at(2026, 1, 16, 8, 0), SH));
    }

    @Test public void nextEdgePicksTheComingBoundary() {
        RelaxWindow n = RelaxWindow.night(SH);
        assertEquals(at(2026, 1, 16, 8, 30), n.nextEdge(at(2026, 1, 15, 23, 0), SH));
        assertEquals(at(2026, 1, 15, 22, 0), n.nextEdge(at(2026, 1, 15, 12, 0), SH));
        assertEquals(at(2026, 1, 16, 8, 30), n.nextEdge(at(2026, 1, 16, 2, 0), SH));
    }

    @Test public void noonNextEdgePicksLunchBoundaries() {
        RelaxWindow n = RelaxWindow.noon(SH);
        assertEquals(at(2026, 1, 15, 11, 0), n.nextEdge(at(2026, 1, 15, 9, 0), SH));
        assertEquals(at(2026, 1, 15, 13, 0), n.nextEdge(at(2026, 1, 15, 12, 0), SH));
        assertEquals(at(2026, 1, 16, 11, 0), n.nextEdge(at(2026, 1, 15, 14, 0), SH));
    }

    @Test public void refillsOncePerNightAndKeepsRemainingOnRestart() {
        RelaxWindow n = RelaxWindow.night(SH).refreshed(at(2026, 1, 15, 23, 0), SH);
        assertEquals(2_700_000L, n.remainingMs());
        assertEquals(at(2026, 1, 15, 22, 0), n.windowStartMs());
        RelaxWindow spent = n.withRemaining(600_000);
        // Same night, later time (and a restart): remaining is preserved, not refilled.
        assertSame(spent, spent.refreshed(at(2026, 1, 16, 2, 0), SH));
        // Next night: a fresh window refills to the full allowance.
        RelaxWindow nextNight = spent.refreshed(at(2026, 1, 16, 23, 0), SH);
        assertEquals(2_700_000L, nextNight.remainingMs());
        assertEquals(at(2026, 1, 16, 22, 0), nextNight.windowStartMs());
    }

    @Test public void noonRefillsOncePerDayAndDropsUnusedRemainder() {
        RelaxWindow n = RelaxWindow.noon(SH).refreshed(at(2026, 1, 15, 12, 0), SH);
        assertEquals(1_800_000L, n.remainingMs());
        assertEquals(at(2026, 1, 15, 11, 0), n.windowStartMs());
        RelaxWindow spent = n.withRemaining(300_000);
        // After the window closes the unused remainder is not refilled, only the date moves on.
        RelaxWindow afterClose = spent.refreshed(at(2026, 1, 15, 14, 0), SH);
        assertEquals(300_000L, afterClose.remainingMs());
        // Next day at noon: a fresh window refills to the full allowance, remainder dropped.
        RelaxWindow nextDay = afterClose.refreshed(at(2026, 1, 16, 12, 0), SH);
        assertEquals(1_800_000L, nextDay.remainingMs());
        assertEquals(at(2026, 1, 16, 11, 0), nextDay.windowStartMs());
    }

    @Test public void outsideWindowDoesNotRefillOrChange() {
        RelaxWindow spent = RelaxWindow.night(SH).refreshed(at(2026, 1, 15, 23, 0), SH).withRemaining(0);
        assertSame(spent, spent.refreshed(at(2026, 1, 16, 12, 0), SH));
    }

    @Test public void remainingClampsToAllowance() {
        RelaxWindow n = RelaxWindow.night(SH);
        assertEquals(2_700_000L, n.withRemaining(9_999_999L).remainingMs());
        assertEquals(0, n.withRemaining(-5).remainingMs());
    }

    @Test public void reenablingForcesRefillNextWindow() {
        RelaxWindow off = RelaxWindow.night(SH).refreshed(at(2026, 1, 15, 23, 0), SH)
            .withRemaining(120_000).withEnabled(false);
        assertFalse(off.enabled());
        RelaxWindow on = off.withEnabled(true);
        assertEquals(0, on.windowStartMs());   // forces refill on next entry
        RelaxWindow refilled = on.refreshed(at(2026, 1, 15, 23, 30), SH);
        assertEquals(2_700_000L, refilled.remainingMs());
    }

    @Test public void disabledWindowStaysInactiveForRouting() {
        RelaxWindow off = RelaxWindow.night(SH).withEnabled(false);
        // active() is a pure time test; callers gate with enabled(), which is false here.
        assertFalse(off.enabled());
    }
}
