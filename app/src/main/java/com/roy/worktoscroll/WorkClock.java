package com.roy.worktoscroll;

/** Monotonic observation clock. No frames, identity, or wall-clock time are retained. */
public final class WorkClock {
    public static final long FRESH_MS = 1_500;
    public static final long RETURN_MS = 1_000;
    public static final long AWAY_GRACE_MS = 3_000;
    /** Fresh weak-only observations may bridge a brief confidence dip after confirmed presence. */
    public static final int WEAK_ONLY = -2;
    public static final long WEAK_GRACE_MS = 3_000;
    public enum State { PAUSED, CHECKING, PRESENT, WEAK_GRACE, GRACE, AWAY, UNCERTAIN }
    private long last = -1;
    private long presentSince = -1;
    private long awaySince = -1;
    private long weakSince = -1;
    private State state = State.PAUSED;

    public State state() { return state; }
    public boolean counting() { return state == State.PRESENT || state == State.WEAK_GRACE || state == State.GRACE; }

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

    /** A positive signal means at least one reliably observed person, not proof of work or identity. */
    public long observe(long now, int people) {
        if (last >= 0 && now <= last) return 0;
        if (last >= 0 && now - last > FRESH_MS) interrupt();
        long previous = last;
        last = now;
        if (people == WEAK_ONLY) {
            presentSince = -1;
            if (state == State.PRESENT || state == State.WEAK_GRACE || state == State.GRACE) {
                if (weakSince < 0) weakSince = state == State.GRACE && awaySince >= 0 ? awaySince : now;
                awaySince = -1;
                long deadline = weakSince + WEAK_GRACE_MS;
                long credit = previous < 0 ? 0 : Math.max(0, Math.min(now, deadline) - previous);
                if (now < deadline) {
                    state = State.WEAK_GRACE;
                    return credit;
                }
                weakSince = -1;
                state = State.UNCERTAIN;
                return credit;
            }
            awaySince = weakSince = -1;
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
                awaySince = weakSince = -1;
                presentSince = now;
                state = State.CHECKING;
                return credit;
            }
            awaySince = -1;
            if (state == State.PRESENT || state == State.WEAK_GRACE || state == State.GRACE) {
                presentSince = weakSince = -1;
                state = State.PRESENT;
                return previous < 0 ? 0 : now - previous;
            }
            weakSince = -1;
            if (presentSince < 0) presentSince = now;
            state = now - presentSince >= RETURN_MS ? State.PRESENT : State.CHECKING;
            if (state == State.PRESENT) presentSince = -1;
            // The confirmation window is not credited retroactively.
            return 0;
        }
        presentSince = -1;
        if (state == State.PRESENT || state == State.WEAK_GRACE || state == State.GRACE) {
            if (awaySince < 0) awaySince = state == State.WEAK_GRACE && weakSince >= 0 ? weakSince : now;
            weakSince = -1;
            long deadline = awaySince + AWAY_GRACE_MS;
            state = now >= deadline ? State.AWAY : State.GRACE;
            return previous < 0 ? 0 : Math.max(0, Math.min(now, deadline) - previous);
        }
        awaySince = weakSince = -1;
        state = State.AWAY;
        return 0;
    }
}
