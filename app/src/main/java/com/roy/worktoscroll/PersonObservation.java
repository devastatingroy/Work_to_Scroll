package com.roy.worktoscroll;

/** Non-image output shared by work timing, audible detection cues and local diagnostics. */
public final class PersonObservation {
    public final int candidates, reliable;
    public final float score;
    public PersonObservation(int candidates, int reliable, float score) {
        this.candidates = candidates; this.reliable = reliable; this.score = score;
    }
    public static String diagnosticKind(int candidates, int reliable) {
        if (candidates < 0 || reliable < 0 || reliable > candidates) return "invalid_observation";
        if (candidates == 0) return "no_person";
        if (reliable == 0) return candidates == 1 ? "weak_person_candidate" : "multiple_weak_candidates";
        if (reliable == 1) return candidates == 1
            ? "single_reliable_person" : "single_reliable_person_with_weak_candidates";
        return "multiple_reliable_people";
    }
    public int clockSignal() {
        if (candidates < 0 || reliable < 0 || reliable > candidates) return -1;
        if (candidates == 0) return 0;
        // Candidate count does not identify people. Any reliable person is enough evidence of presence.
        if (reliable >= 1) return 1;
        return WorkClock.WEAK_ONLY;
    }
}
