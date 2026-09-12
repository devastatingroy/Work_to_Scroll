package com.roy.worktoscroll;

/** Foreground-only minute reminders for one continuous interruption; never queues missed periods. */
final class UnrecordedReminder {
    static final long INTERVAL_MS = 60_000;

    private long interruptionAt = -1;
    private long lastCycle;

    boolean due(WorkProgress progress, boolean monitoringWanted, boolean available,
                boolean soundsEnabled, long now) {
        if (!progress.active() || !monitoringWanted || !available || !soundsEnabled
                || progress.interruptedAtElapsedMs() < 0) {
            reset();
            return false;
        }
        long currentInterruption = progress.interruptedAtElapsedMs();
        if (currentInterruption != interruptionAt) {
            interruptionAt = currentInterruption;
            lastCycle = 0;
        }
        long elapsed = Math.max(0, now - interruptionAt);
        long cycle = elapsed / INTERVAL_MS;
        if (cycle == 0 || cycle <= lastCycle) return false;
        // A return from the background consumes all missed periods in one alert, never a burst.
        lastCycle = cycle;
        return true;
    }

    void reset() {
        interruptionAt = -1;
        lastCycle = 0;
    }

    long cycle(long now) {
        return interruptionAt < 0 ? 0 : Math.max(0, now - interruptionAt) / INTERVAL_MS;
    }
}
