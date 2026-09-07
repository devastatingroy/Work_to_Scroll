package com.roy.worktoscroll;

import java.util.List;

/** Pure, replayable usage ledger. Only an explicit grant can increase balance. */
public final class BalanceEngine {
    public record Transition(long timeMs, boolean targetActive) {}
    public long balanceMs;
    public long consumedMs;
    public long cursorMs;
    public boolean targetActive;

    public BalanceEngine(long balanceMs, long consumedMs, long cursorMs, boolean targetActive) {
        this.balanceMs = Math.max(0, balanceMs);
        this.consumedMs = Math.max(0, consumedMs);
        this.cursorMs = cursorMs;
        this.targetActive = targetActive;
    }

    /** Events must be chronological. The committed interval is [cursor, until). */
    public void settle(List<Transition> events, long until) {
        if (until < cursorMs) throw new IllegalArgumentException("Clock moved backwards");
        for (Transition event : events) {
            if (event.timeMs < cursorMs || event.timeMs >= until) continue;
            chargeTo(event.timeMs);
            targetActive = event.targetActive;
        }
        chargeTo(until);
    }

    private void chargeTo(long timeMs) {
        if (targetActive) {
            long charged = Math.min(balanceMs, Math.max(0, timeMs - cursorMs));
            balanceMs -= charged;
            consumedMs += charged;
        }
        cursorMs = timeMs;
    }

    public void grant(long amountMs) {
        if (amountMs <= 0) throw new IllegalArgumentException("Positive grant required");
        balanceMs = Math.addExact(balanceMs, amountMs);
    }

    /** Unknown intervals must never silently erase or mint balance. */
    public void resetObservation(long now) {
        cursorMs = now;
        targetActive = false;
    }
}
