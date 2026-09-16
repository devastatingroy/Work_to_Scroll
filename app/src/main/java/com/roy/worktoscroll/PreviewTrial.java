package com.roy.worktoscroll;

/** In-memory state for each temporary calibration preview. */
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

    /** Starts a new temporary preview after the previous one was automatically hidden. */
    boolean reopen() {
        if (!suppressed) return false;
        suppressed = false;
        closeAllowedAtElapsed = -1;
        return true;
    }

    /** Restores the hidden state if CameraX cannot bind a requested preview. */
    void cancelReopen() {
        suppressed = true;
        closeAllowedAtElapsed = -1;
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
