package com.roy.worktoscroll;

/** Immediate first alert, then repeated voice and flash at +30s, +60s, +120s... */
final class UnrecordedReminder {
    private static final long FIRST_REPEAT_MS = 30_000;
    enum Feedback {
        NONE, VISUAL, VOICE, REPEAT, MUTED_REPEAT;
        boolean flashes() { return this != NONE; }
        boolean speaks() { return this == VOICE || this == REPEAT; }
    }
    private boolean handled, abnormalPause, hasCounted;
    private long firstAlertAt, nextOffset;
    private int ordinal;

    Feedback update(boolean monitoring, boolean available, boolean counting, boolean evaluated,
                    boolean noPerson, boolean soundsEnabled, long now) {
        if (!monitoring || !available) {
            clearPause();
            return Feedback.NONE;
        }
        if (counting) {
            hasCounted = true;
            clearPause();
            return Feedback.NONE;
        }
        // Binding/initial confirmation is not a failure. Wait for an observation or a real error.
        if (handled) {
            if (!abnormalPause || nextOffset == Long.MAX_VALUE) return Feedback.NONE;
            long elapsed = Math.max(0, now - firstAlertAt);
            if (elapsed < nextOffset) return Feedback.NONE;
            // Consume every elapsed slot, but emit at most one current reminder. Never queue a burst.
            do {
                ordinal++;
                nextOffset = nextOffset > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : nextOffset * 2;
            } while (nextOffset <= elapsed && nextOffset != Long.MAX_VALUE);
            // A now-clear absence keeps the existing away behavior, not an adjustment warning.
            if (noPerson) return Feedback.NONE;
            return soundsEnabled ? Feedback.REPEAT : Feedback.MUTED_REPEAT;
        }
        if (!evaluated) return Feedback.NONE;
        handled = true;
        if (noPerson) return Feedback.NONE;
        abnormalPause = true;
        firstAlertAt = now;
        nextOffset = FIRST_REPEAT_MS;
        ordinal = 1;
        return soundsEnabled ? Feedback.VOICE : Feedback.VISUAL;
    }

    int ordinal() { return ordinal; }
    boolean abnormalPause() { return abnormalPause; }
    boolean showPaused() { return hasCounted || handled; }
    private void clearPause() {
        handled = abnormalPause = false;
        firstAlertAt = nextOffset = 0;
        ordinal = 0;
    }
    void reset() { clearPause(); hasCounted = false; }
}
