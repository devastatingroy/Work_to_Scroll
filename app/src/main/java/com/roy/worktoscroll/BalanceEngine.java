package com.roy.worktoscroll;

import java.util.List;

/** Pure, replayable usage ledger. Only an explicit grant can increase balance. */
public final class BalanceEngine {
    public record Transition(long timeMs, boolean targetActive) {}
    public long balanceMs;
    public long consumedMs;
    public long cursorMs;
    public boolean targetActive;
    private long chargeFromMs;
    private long relaxBudgetMs = -1;

    /** During a night-relax segment, target time draws from this free budget before balance. */
    void relaxBudget(long budgetMs) { relaxBudgetMs = Math.max(0, budgetMs); }
    void clearRelaxBudget() { relaxBudgetMs = -1; }
    long relaxRemaining() { return Math.max(0, relaxBudgetMs); }
    record Checkpoint(long balance, long consumed, long cursor, boolean active, long chargeFrom) {}
    Checkpoint checkpoint() { return new Checkpoint(balanceMs, consumedMs, cursorMs, targetActive, chargeFromMs); }
    void restore(Checkpoint c) {
        balanceMs = c.balance; consumedMs = c.consumed; cursorMs = c.cursor;
        targetActive = c.active; chargeFromMs = c.chargeFrom;
    }

    public BalanceEngine(long balanceMs, long consumedMs, long cursorMs, boolean targetActive) {
        this.balanceMs = Math.max(0, balanceMs);
        this.consumedMs = Math.max(0, consumedMs);
        this.cursorMs = cursorMs;
        this.targetActive = targetActive;
        this.chargeFromMs = cursorMs;
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
            long want = Math.max(0, timeMs - Math.max(cursorMs, chargeFromMs));
            if (relaxBudgetMs >= 0) {
                // Free night quota is spent first and never counts as consumed balance.
                long fromRelax = Math.min(relaxBudgetMs, want);
                relaxBudgetMs -= fromRelax;
                want -= fromRelax;
            }
            long charged = Math.min(balanceMs, want);
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
        chargeFromMs = now;
        targetActive = false;
    }

    /** Rebuild foreground state after an unobserved interval, without charging that interval. */
    public void recoverObservation(List<Transition> history, long checkpoint, long observedFrom) {
        resetObservation(checkpoint);
        chargeFromMs = Math.max(checkpoint, observedFrom);
        for (Transition event : history) {
            if (event.timeMs < checkpoint) targetActive = event.targetActive;
        }
    }
}
