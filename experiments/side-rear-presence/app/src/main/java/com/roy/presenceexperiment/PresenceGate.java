package com.roy.presenceexperiment;

/** Whole-frame experimental presence only; no identity, seat region or reward. */
public final class PresenceGate {
    public static final long FRESH_MS = 1500, ENTER_MS = 1000, AWAY_MS = 3000;
    private long last = -1, entering = -1, missing = -1;
    private String state = "WAITING";

    public void reset() { last = entering = missing = -1; state = "WAITING"; }

    public void observe(long captured, int reliablePeople) {
        if (last >= 0 && captured <= last) return;
        if (last >= 0 && captured - last > FRESH_MS) reset();
        last = captured;
        if (reliablePeople > 1 || reliablePeople < 0) {
            entering = missing = -1;
            state = "UNCERTAIN";
        } else if (reliablePeople == 1) {
            boolean continuity = state.equals("PRESENT") ||
                (state.equals("GRACE") && captured - missing < AWAY_MS);
            missing = -1;
            if (continuity) { state = "PRESENT"; return; }
            if (entering < 0) entering = captured;
            state = captured - entering >= ENTER_MS ? "PRESENT" : "CHECKING";
        } else {
            entering = -1;
            if (state.equals("PRESENT") || state.equals("GRACE")) {
                if (missing < 0) missing = captured;
                state = captured - missing >= AWAY_MS ? "NOT_SEEN" : "GRACE";
            } else { missing = -1; state = "NOT_SEEN"; }
        }
    }

    public String state(long now) {
        if (last >= 0 && now - last > FRESH_MS) {
            reset();
            state = "NO_FRESH_RESULT";
        }
        return state;
    }
}
