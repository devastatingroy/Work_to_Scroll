package com.roy.worktoscroll;

/** One foreground-safe reminder for each non-manual continuous interruption. */
final class UnrecordedReminder {
    static final long INTERVAL_MS = 60_000;

    private long interruptionAt = -1;
    private boolean notified;

    boolean due(WorkProgress progress, boolean monitoringWanted, boolean available,
                boolean soundsEnabled, long now) {
        if (!progress.active() || !monitoringWanted || progress.interruptedAtElapsedMs() < 0) {
            reset();
            return false;
        }
        long currentInterruption = progress.interruptedAtElapsedMs();
        if (currentInterruption != interruptionAt) {
            interruptionAt = currentInterruption;
            notified = false;
        }
        // Sound disabled is an explicit choice; do not replay this interruption if it is re-enabled.
        if (!soundsEnabled) { notified = true; return false; }
        // While a call, system dialog, lock screen, or other focus loss is active, defer one alert.
        if (!available || notified) return false;
        long elapsed = Math.max(0, now - interruptionAt);
        if (elapsed < INTERVAL_MS) return false;
        notified = true;
        return true;
    }

    void reset() {
        interruptionAt = -1;
        notified = false;
    }

    long cycle(long now) {
        return interruptionAt < 0 || !notified ? 0 : 1;
    }
}
