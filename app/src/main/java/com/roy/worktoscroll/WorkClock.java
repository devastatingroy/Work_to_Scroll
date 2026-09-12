package com.roy.worktoscroll;

/** Monotonic observation clock. No frames, identity, or wall-clock time are retained. */
public final class WorkClock {
    public static final long FRESH_MS = 1_500;
    public static final long RETURN_MS = 1_000;
    public static final long AWAY_GRACE_MS = 3_000;
    /** One or more person candidates below the reliable threshold. They never establish presence. */
    public static final int WEAK_ONLY = -2;
    /** A bounded guard against an indefinitely stable false positive. */
    public static final long WEAK_HOLD_MS = 120_000;
    public enum State { PAUSED, CHECKING, PRESENT, WEAK_HOLD, GRACE, AWAY, UNCERTAIN }
    private long last = -1;
    private long presentSince = -1;
    private long awaySince = -1;
    private long weakSince = -1;
    private State state = State.PAUSED;

    public State state() { return state; }
    public boolean counting() { return state == State.PRESENT || state == State.WEAK_HOLD || state == State.GRACE; }

    public void interrupt() {
        last = presentSince = awaySince = weakSince = -1;
        state = State.PAUSED;
    }

    /** Returns when freshness expired, so a delayed UI tick cannot restart the pause deadline. */
    public long expire(long now) {
        if (last >= 0 && now - last > FRESH_MS) {
            long expiredAt = last + FRESH_MS;
            interrupt();
            return expiredAt;
        }
        return -1;
    }

    /** Ends a weak-signal hold at its fixed deadline even if the next inference is delayed. */
    public long expireWeakHold(long now) {
        if (state == State.WEAK_HOLD && weakSince >= 0 && now > weakSince + WEAK_HOLD_MS) {
            long expiredAt = weakSince + WEAK_HOLD_MS;
            weakSince = -1;
            state = State.UNCERTAIN;
            return expiredAt;
        }
        return -1;
    }

    /** A positive signal means at least one reliably observed person, not proof of work or identity. */
    public long observe(long now, int people) {
        if (last >= 0 && now <= last) return 0;
        if (last >= 0 && now - last > FRESH_MS) interrupt();
        long previous = last;
        last = now;
        if (people == WEAK_ONLY) {
            presentSince = -1;
            if (state == State.PRESENT || state == State.WEAK_HOLD || state == State.GRACE) {
                if (weakSince < 0) weakSince = state == State.GRACE && awaySince >= 0 ? awaySince : now;
                awaySince = -1;
                long deadline = weakSince + WEAK_HOLD_MS;
                long credit = previous < 0 ? 0 : Math.max(0, Math.min(now, deadline) - previous);
                if (now <= deadline) {
                    state = State.WEAK_HOLD;
                    return credit;
                }
                weakSince = -1;
                state = State.UNCERTAIN;
                return credit;
            }
            weakSince = -1;
            state = State.UNCERTAIN;
            return 0;
        }
        if (people < 0) {
            presentSince = awaySince = weakSince = -1;
            state = State.UNCERTAIN;
            return 0;
        }
        if (people > 0) {
            if (state == State.GRACE && awaySince >= 0 && now >= awaySince + AWAY_GRACE_MS) {
                long credit = previous < 0 ? 0 : Math.max(0, awaySince + AWAY_GRACE_MS - previous);
                awaySince = -1;
                weakSince = -1;
                presentSince = now;
                state = State.CHECKING;
                return credit;
            }
            awaySince = -1;
            if (state == State.PRESENT || state == State.GRACE) {
                presentSince = weakSince = -1;
                state = State.PRESENT;
                return previous < 0 ? 0 : now - previous;
            }
            if (state == State.WEAK_HOLD && weakSince >= 0) {
                long deadline = weakSince + WEAK_HOLD_MS;
                long credit = previous < 0 ? 0 : Math.max(0, Math.min(now, deadline) - previous);
                if (presentSince < 0) presentSince = now;
                if (now - presentSince >= RETURN_MS) {
                    presentSince = weakSince = -1;
                    state = State.PRESENT;
                    return credit;
                }
                if (now <= deadline) {
                    state = State.WEAK_HOLD;
                    return credit;
                }
                weakSince = -1;
                state = State.CHECKING;
                return credit;
            }
            weakSince = -1;
            if (presentSince < 0) presentSince = now;
            state = now - presentSince >= RETURN_MS ? State.PRESENT : State.CHECKING;
            if (state == State.PRESENT) presentSince = -1;
            // The confirmation window is not credited retroactively.
            return 0;
        }
        presentSince = -1;
        if (state == State.PRESENT || state == State.WEAK_HOLD || state == State.GRACE) {
            if (weakSince < 0) weakSince = now;
            if (awaySince < 0) awaySince = now;
            long deadline = awaySince + AWAY_GRACE_MS;
            state = now >= deadline ? State.AWAY : State.GRACE;
            long credit = previous < 0 ? 0 : Math.max(0, Math.min(now, deadline) - previous);
            if (state == State.AWAY) weakSince = -1;
            return credit;
        }
        awaySince = weakSince = -1;
        state = State.AWAY;
        return 0;
    }
}
