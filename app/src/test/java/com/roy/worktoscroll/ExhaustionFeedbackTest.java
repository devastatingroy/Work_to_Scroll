package com.roy.worktoscroll;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExhaustionFeedbackTest {
    @Test public void onlyAnExactConsumptionTransitionCreatesAnEvent() {
        ExhaustionFeedback f = new ExhaustionFeedback();
        f.settled(0, 0, 100, 100, 1);
        assertNull(f.pendingId(1));
        f.settled(100, 0, 100, 100, 2); // Daily reset/manual clear.
        assertFalse(f.exhausted());
        f.settled(100, 0, 100, 150, 3); // Some consumption plus another balance change.
        assertNull(f.pendingId(3));
        f.settled(100, 0, 100, 200, 4);
        assertTrue(f.exhausted());
        assertNotNull(f.pendingId(4));
    }

    @Test public void repeatedPollsAndPageRecreationCannotReplay() {
        ExhaustionFeedback f = exhausted(1_000);
        String id = f.pendingId(1_000);
        f.settled(0, 0, 200, 200, 1_500);
        assertEquals(id, f.pendingId(1_500));
        assertFalse(f.consume("old-page", 1_500));
        assertTrue(f.consume(id, 1_500));
        assertFalse(f.consume(id, 1_600));
        assertTrue(f.exhausted()); // Reopen can keep the correct static reason.
    }

    @Test public void lateOrInterruptedPresentationStaysStatic() {
        ExhaustionFeedback f = exhausted(1_000);
        String id = f.pendingId(1_000);
        assertFalse(f.consume(id, 1_000 + ExhaustionFeedback.MAX_AGE_MS + 1));
        assertTrue(f.exhausted());
        f = exhausted(1_000);
        id = f.pendingId(1_000);
        f.discardPending(); // User navigated away before the page had focus.
        assertFalse(f.consume(id, 1_100));
        assertTrue(f.exhausted());
    }

    @Test public void creditResetOrRecoveryInvalidatesTheOldEvent() {
        ExhaustionFeedback f = exhausted(1_000);
        String id = f.pendingId(1_000);
        f.settled(300, 200, 200, 300, 1_100);
        assertFalse(f.exhausted());
        assertFalse(f.consume(id, 1_200));
        f.settled(200, 0, 300, 500, 1_300);
        String next = f.pendingId(1_300);
        assertNotEquals(id, next);
        assertFalse(f.consume(id, 1_300));
        f.clear(); // Deadline/reset, stop, observation failure or service recovery.
        assertFalse(f.consume(next, 1_400));
        assertFalse(f.exhausted());
    }

    @Test public void processRestartDoesNotTrustAnOldIntent() {
        ExhaustionFeedback old = exhausted(1_000);
        ExhaustionFeedback fresh = new ExhaustionFeedback();
        assertFalse(fresh.consume(old.pendingId(1_000), 1_100));
        assertFalse(fresh.exhausted());
    }

    @Test public void clockRollbackAndNullIdsAreRejected() {
        ExhaustionFeedback f = exhausted(1_000);
        assertFalse(f.consume(null, 1_000));
        String id = f.pendingId(1_000);
        assertFalse(f.consume(id, 999));
    }

    @Test public void realLedgerSettlementDrivesTheEventWithoutChangingAccounting() {
        BalanceEngine e = new BalanceEngine(600, 400, 10_000, true);
        ExhaustionFeedback f = new ExhaustionFeedback();
        long balance = e.balanceMs, consumed = e.consumedMs;
        e.settle(List.of(), 10_500);
        f.settled(balance, e.balanceMs, consumed, e.consumedMs, 20_000);
        assertNull(f.pendingId(20_000));
        balance = e.balanceMs; consumed = e.consumedMs;
        e.settle(List.of(), 11_000);
        f.settled(balance, e.balanceMs, consumed, e.consumedMs, 20_500);
        assertTrue(f.consume(f.pendingId(20_500), 20_500));
        assertEquals(0, e.balanceMs);
        assertEquals(1_000, e.consumedMs);
        assertEquals(11_000, e.cursorMs);
    }

    private ExhaustionFeedback exhausted(long now) {
        ExhaustionFeedback f = new ExhaustionFeedback();
        f.settled(100, 0, 100, 200, now);
        return f;
    }
}
