package com.roy.worktoscroll;

/** Announce a committed return to counting, never a raw reliable candidate or a grace recovery. */
final class CountingResumeCue {
    private boolean hasCounted, awaitingResume;

    boolean update(boolean active, boolean counting, boolean confirmedPause) {
        if (!active) {
            hasCounted = awaitingResume = false;
            return false;
        }
        if (counting) {
            boolean announce = awaitingResume;
            hasCounted = true;
            awaitingResume = false;
            return announce;
        }
        if (hasCounted || confirmedPause) awaitingResume = true;
        return false;
    }

    boolean awaitingResume() { return awaitingResume; }
}
