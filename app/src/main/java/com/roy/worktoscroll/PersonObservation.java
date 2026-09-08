package com.roy.worktoscroll;

/** Non-image output shared by work timing, audible detection cues and local diagnostics. */
public final class PersonObservation {
    public final int candidates, reliable;
    public final float score;
    public PersonObservation(int candidates, int reliable, float score) {
        this.candidates = candidates; this.reliable = reliable; this.score = score;
    }
    public int clockSignal() {
        if (candidates < 0 || reliable < 0 || reliable > candidates) return -1;
        if (candidates == 0) return 0;
        return reliable == 1 ? 1 : -1;
    }
}
