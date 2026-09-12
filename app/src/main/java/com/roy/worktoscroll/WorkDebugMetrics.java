package com.roy.worktoscroll;

/** Pure transition rules shared by the debug journal and local unit tests. */
final class WorkDebugMetrics {
    private WorkDebugMetrics() {}

    static boolean roundReset(WorkProgress before, WorkProgress after) {
        return before.active() && after.active()
            && after.totalValidMs() >= before.totalValidMs()
            && after.sessionValidMs() < before.sessionValidMs();
    }

    static boolean interruptionStarted(WorkProgress before, WorkProgress after) {
        return before.active() && after.active()
            && before.interruptedAtElapsedMs() < 0 && after.interruptedAtElapsedMs() >= 0;
    }

    static boolean interruptionEnded(WorkProgress before, WorkProgress after) {
        return before.active() && after.active()
            && before.interruptedAtElapsedMs() >= 0 && after.interruptedAtElapsedMs() < 0;
    }

    static long validSince(long baselineTotalMs, WorkProgress progress) {
        return Math.max(0, progress.totalValidMs() - baselineTotalMs);
    }

    static long wallDuration(long startedWallMs, long wallNowMs) {
        return Math.max(0, wallNowMs - startedWallMs);
    }
}
