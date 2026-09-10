package com.roy.worktoscroll;

import java.util.UUID;

/** Ephemeral presentation event, never a ledger write or a reason to delay protection. */
final class ExhaustionFeedback {
    static final long MAX_AGE_MS = 5_000;
    private String pendingId;
    private long createdAt;
    private boolean exhausted;

    void settled(long balanceBefore, long balanceAfter, long consumedBefore, long consumedAfter, long now) {
        if (balanceAfter > 0) clear();
        // A reset, initial zero, or repeated zero is not an actual consumption transition.
        if (balanceBefore > 0 && balanceAfter == 0 && consumedAfter >= consumedBefore
                && consumedAfter - consumedBefore == balanceBefore) {
            exhausted = true;
            pendingId = UUID.randomUUID().toString();
            createdAt = now;
        }
    }

    boolean exhausted() { return exhausted; }

    String pendingId(long now) {
        if (now < createdAt || now - createdAt > MAX_AGE_MS) discardPending();
        return pendingId;
    }

    boolean consume(String id, long now) {
        if (id == null || !id.equals(pendingId(now))) return false;
        discardPending();
        return true;
    }

    void discardPending() { pendingId = null; }
    void clear() { discardPending(); exhausted = false; }
}
