package com.roy.worktoscroll;

/** In-memory state for the analysis-only camera trial. */
final class PreviewTrial {
    static final long MINIMUM_VISIBLE_MS = 10_000;
    private boolean suppressed;
    private long closeAllowedAtElapsed = -1;

    void previewBound(long elapsed) {
        if (!suppressed) closeAllowedAtElapsed = elapsed + MINIMUM_VISIBLE_MS;
    }

    boolean suppress(long elapsed) {
        if (!canSuppress(elapsed)) return false;
        suppressed = true;
        return true;
    }

    boolean canSuppress(long elapsed) {
        return !suppressed && closeAllowedAtElapsed >= 0 && elapsed >= closeAllowedAtElapsed;
    }

    long closeRemainingMs(long elapsed) {
        if (suppressed || closeAllowedAtElapsed < 0) return 0;
        return Math.max(0, closeAllowedAtElapsed - elapsed);
    }

    void resetForManualStart() {
        suppressed = false;
        closeAllowedAtElapsed = -1;
    }

    boolean suppressed() {
        return suppressed;
    }
}
