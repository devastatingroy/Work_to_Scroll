package com.roy.worktoscroll;

/** Monotonic observation clock. No frames, identity, or wall-clock time are retained. */
public final class WorkClock {
    public static final long FRESH_MS = 1_500;
    public static final long RETURN_MS = 1_000;
    public static final long AWAY_GRACE_MS = 3_000;
    public enum State { PAUSED, CHECKING, PRESENT, GRACE, AWAY, UNCERTAIN }
    private long last = -1;
    private long presentSince = -1;
    private long awaySince = -1;
    private State state = State.PAUSED;

    public State state() { return state; }

    public void interrupt() {
        last = presentSince = awaySince = -1;
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

    /** Exactly one face is a basic presence signal, not proof of work or identity. */
    public long observe(long now, int faces) {
        if (last >= 0 && now <= last) return 0;
        if (last >= 0 && now - last > FRESH_MS) interrupt();
        long previous = last;
        last = now;
        if (faces < 0 || faces > 1) {
            presentSince = awaySince = -1;
            state = State.UNCERTAIN;
            return 0;
        }
        if (faces == 1) {
            if (state == State.GRACE && awaySince >= 0 && now >= awaySince + AWAY_GRACE_MS) {
                long credit = previous < 0 ? 0 : Math.max(0, awaySince + AWAY_GRACE_MS - previous);
                awaySince = -1;
                presentSince = now;
                state = State.CHECKING;
                return credit;
            }
            awaySince = -1;
            if (state == State.PRESENT || state == State.GRACE) {
                state = State.PRESENT;
                return previous < 0 ? 0 : now - previous;
            }
            if (presentSince < 0) presentSince = now;
            state = now - presentSince >= RETURN_MS ? State.PRESENT : State.CHECKING;
            // The confirmation window is not credited retroactively.
            return 0;
        }
        presentSince = -1;
        if (state == State.PRESENT || state == State.GRACE) {
            if (awaySince < 0) awaySince = now;
            long deadline = awaySince + AWAY_GRACE_MS;
            state = now >= deadline ? State.AWAY : State.GRACE;
            return previous < 0 ? 0 : Math.max(0, Math.min(now, deadline) - previous);
        }
        awaySince = -1;
        state = State.AWAY;
        return 0;
    }
}
