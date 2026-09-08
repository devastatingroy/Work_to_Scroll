package com.roy.presenceexperiment;

/** Audible raw transitions are intentionally independent of PresenceGate's grace periods. */
public final class DetectionCue {
    public enum Cue { NONE, AWAY, RETURN }
    private Boolean lastDetected;
    private long lastFrame = -1;

    public void reset() { lastDetected = null; lastFrame = -1; }

    public Cue observe(long capturedAt, long now, int candidates, int reliablePeople) {
        if (now < capturedAt || now - capturedAt > PresenceGate.FRESH_MS) { reset(); return Cue.NONE; }
        if (capturedAt <= lastFrame) return Cue.NONE;
        if (lastFrame >= 0 && capturedAt - lastFrame > PresenceGate.FRESH_MS) reset();
        lastFrame = capturedAt;
        // Weak-only candidates are uncertainty, not a confirmed absence.
        if (candidates < 0 || reliablePeople < 0 || (candidates > 0 && reliablePeople == 0)) return Cue.NONE;
        boolean detected = reliablePeople > 0;
        if (lastDetected != null && lastDetected == detected) return Cue.NONE;
        lastDetected = detected;
        return detected ? Cue.RETURN : Cue.AWAY;
    }
}
