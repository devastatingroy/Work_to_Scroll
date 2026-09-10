package com.roy.worktoscroll;

/** In-memory user intent and bounded recovery. Never restored from a ledger or saved Bundle. */
final class CameraRecovery {
    enum State { IDLE, WAITING, STARTING, MONITORING, FAILED }
    static final long FRAME_TIMEOUT_MS = 8_000;
    static final long STABLE_MS = 5_000;
    static final long RECOVERY_BUDGET_MS = 30_000;
    static final int MAX_FAILURES = 3;
    private State state = State.IDLE;
    private int failures;
    private long startedAt, lastFreshAt = -1, stableSince = -1, retryAt;
    private long recoveryDeadline = -1;
    private String failureReason = "";
    private boolean alertPending;

    State state() { return state; }
    int failures() { return failures; }
    String failureReason() { return failureReason; }
    boolean wanted() { return state != State.IDLE && state != State.FAILED; }
    boolean failed() { return state == State.FAILED; }

    void request() {
        state = State.WAITING;
        failures = 0;
        retryAt = 0;
        alertPending = false;
        failureReason = "";
        lastFreshAt = stableSince = -1;
        recoveryDeadline = -1;
    }

    void stop() {
        state = State.IDLE;
        alertPending = false;
        lastFreshAt = stableSince = -1;
        recoveryDeadline = -1;
    }

    void suspend() {
        if (!wanted()) return;
        state = State.WAITING;
        lastFreshAt = stableSince = -1;
        recoveryDeadline = -1;
        // A genuine interruption cancels an attempt, but does not forgive prior failures.
    }

    boolean beginAttempt(long now, boolean ready) {
        if (!ready || state != State.WAITING || now < retryAt) return false;
        state = State.STARTING;
        startedAt = now;
        if (recoveryDeadline < 0) recoveryDeadline = now + RECOVERY_BUDGET_MS;
        lastFreshAt = stableSince = -1;
        return true;
    }

    void freshFrame(long capturedAt) {
        if (state != State.STARTING && state != State.MONITORING) return;
        if (lastFreshAt >= 0 && capturedAt <= lastFreshAt) return;
        if (lastFreshAt < 0 || capturedAt - lastFreshAt > WorkClock.FRESH_MS) stableSince = capturedAt;
        lastFreshAt = capturedAt;
        state = State.MONITORING;
        // One stray frame must not allow an endless restart loop.
        if (capturedAt - stableSince >= STABLE_MS) { failures = 0; recoveryDeadline = -1; }
    }

    boolean timedOut(long now) {
        return (state == State.STARTING || state == State.MONITORING)
            && ((recoveryDeadline >= 0 && now >= recoveryDeadline)
                || now - (lastFreshAt < 0 ? startedAt : lastFreshAt) >= FRAME_TIMEOUT_MS);
    }

    void retry(long now, String reason) {
        if (!wanted()) return;
        if (++failures >= MAX_FAILURES || (recoveryDeadline >= 0 && now >= recoveryDeadline)) { fail(reason); return; }
        state = State.WAITING;
        retryAt = now + failures * 1_000L;
        lastFreshAt = stableSince = -1;
    }

    void fail(String reason) {
        if (state != State.FAILED) alertPending = true;
        state = State.FAILED;
        failureReason = reason;
        lastFreshAt = stableSince = -1;
    }

    boolean takeAlert() {
        if (!alertPending) return false;
        alertPending = false;
        return true;
    }
}
