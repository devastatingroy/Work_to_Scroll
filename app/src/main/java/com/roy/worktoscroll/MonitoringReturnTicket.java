package com.roy.worktoscroll;

/**
 * One-process navigation handoff for a short external-app interruption.
 * It never represents consent to use the camera and is deliberately not persisted.
 */
final class MonitoringReturnTicket {
    static final long MAX_AGE_MS = 90_000L;
    private static Ticket ticket;

    private record Ticket(long workStartedWallMs, long armedElapsedMs) {}

    private MonitoringReturnTicket() {}

    static synchronized void arm(long workStartedWallMs, long nowElapsedMs) {
        if (workStartedWallMs > 0) ticket = new Ticket(workStartedWallMs, nowElapsedMs);
    }

    /** Atomically consumes the ticket; an expired or mismatched ticket cannot be retried. */
    static synchronized boolean consume(long workStartedWallMs, long nowElapsedMs) {
        Ticket candidate = ticket;
        ticket = null;
        return candidate != null && candidate.workStartedWallMs == workStartedWallMs
            && nowElapsedMs >= candidate.armedElapsedMs
            && nowElapsedMs - candidate.armedElapsedMs < MAX_AGE_MS;
    }

    static synchronized void clear() { ticket = null; }
}
