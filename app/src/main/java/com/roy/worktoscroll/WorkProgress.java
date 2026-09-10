package com.roy.worktoscroll;

/** Immutable reward checkpoint. Session time is the current round; history never resets. */
public record WorkProgress(long totalValidMs, long sessionValidMs, long sessionRewardMs,
                           long startedWallMs, boolean active, long interruptedAtElapsedMs,
                           long checkpointElapsedMs, int checkpointBootCount, int roundMinutes, int selectedMinutes) {
    public static final long WORK_NODE_MS = 600_000;
    public static final long REWARD_NODE_MS = 300_000;
    public static final long MINUTE_MS = 60_000;
    public static final long MINUTE_REWARD_MS = 30_000;
    public static final long RESET_AFTER_MS = 180_000;

    public WorkProgress(long totalValidMs, long sessionValidMs, long sessionRewardMs,
                        long startedWallMs, boolean active, long interruptedAtElapsedMs,
                        long checkpointElapsedMs, int checkpointBootCount) {
        this(totalValidMs, sessionValidMs, sessionRewardMs, startedWallMs, active,
            interruptedAtElapsedMs, checkpointElapsedMs, checkpointBootCount, 10, 10);
    }

    public static boolean validMinutes(int minutes) { return minutes == 10 || minutes == 15 || minutes == 20; }
    public long thresholdMs() { return roundMinutes * MINUTE_MS; }
    public long minuteRewardMs() { return REWARD_NODE_MS / roundMinutes; }
    public long earnedThisRound() { return earnedFor(sessionValidMs, roundMinutes); }
    public int displayedMinutes() { return active ? roundMinutes : selectedMinutes; }
    public static String ruleText(int minutes) {
        return "每轮累计 " + minutes + " 分钟有效工作，获得 5 分钟娱乐；之后每分钟再得 "
            + (300 / minutes) + " 秒。";
    }
    public WorkProgress selectMinutes(int minutes) {
        return new WorkProgress(totalValidMs, sessionValidMs, sessionRewardMs, startedWallMs, active,
            interruptedAtElapsedMs, checkpointElapsedMs, checkpointBootCount, roundMinutes, minutes);
    }

    public WorkProgress {
        if (!validMinutes(roundMinutes) || !validMinutes(selectedMinutes))
            throw new IllegalArgumentException("Unknown reward ratio");
        if (totalValidMs < 0 || sessionValidMs < 0 || sessionRewardMs < 0)
            throw new IllegalArgumentException("Negative work checkpoint");
    }

    public WorkProgress begin(long wallNow, long elapsed, int boot) {
        if (active) return refresh(elapsed, boot);
        return new WorkProgress(totalValidMs, 0, 0, wallNow, true, elapsed, elapsed, boot, selectedMinutes, selectedMinutes);
    }

    /** Upgrading cannot infer a current round from the old lifetime reward entitlement. */
    public WorkProgress migrateRoundRules(long elapsed, int boot) {
        return new WorkProgress(totalValidMs, 0, sessionRewardMs, startedWallMs,
            active, active ? elapsed : -1, elapsed, boot, selectedMinutes, selectedMinutes);
    }

    /** A killed process cannot keep claiming presence after its last saved observation. */
    public WorkProgress recover(long elapsed, int boot) {
        if (!active) return this;
        if (clockChanged(elapsed, boot)) return resetRound(elapsed, elapsed, boot);
        WorkProgress paused = interruptedAtElapsedMs >= 0 ? this
            : new WorkProgress(totalValidMs, sessionValidMs, sessionRewardMs, startedWallMs,
                true, checkpointElapsedMs, checkpointElapsedMs, boot, roundMinutes, selectedMinutes);
        return paused.refresh(elapsed, boot);
    }

    private boolean clockChanged(long elapsed, int boot) {
        return checkpointElapsedMs < 0 || checkpointBootCount != boot || elapsed < checkpointElapsedMs;
    }

    public WorkProgress refresh(long elapsed, int boot) {
        if (!active) return this;
        if (clockChanged(elapsed, boot)) return resetRound(elapsed, elapsed, boot);
        if (interruptedAtElapsedMs < 0 && elapsed - checkpointElapsedMs >= RESET_AFTER_MS)
            return resetRound(checkpointElapsedMs, elapsed, boot);
        if (interruptedAtElapsedMs >= 0 && elapsed - interruptedAtElapsedMs >= RESET_AFTER_MS
                && (sessionValidMs > 0 || roundMinutes != selectedMinutes)) return resetRound(interruptedAtElapsedMs, elapsed, boot);
        return this;
    }

    private WorkProgress resetRound(long interrupted, long elapsed, int boot) {
        return new WorkProgress(totalValidMs, 0, sessionRewardMs, startedWallMs,
            active, interrupted, elapsed, boot, selectedMinutes, selectedMinutes);
    }

    public WorkProgress pause(long elapsed, int boot) {
        WorkProgress p = refresh(elapsed, boot);
        if (!p.active || p.interruptedAtElapsedMs >= 0) return p;
        return new WorkProgress(p.totalValidMs, p.sessionValidMs, p.sessionRewardMs,
            p.startedWallMs, true, elapsed, elapsed, boot, p.roundMinutes, p.selectedMinutes);
    }

    /** Called only with fresh WorkClock output; unobserved wall time never earns credit. */
    public WorkProgress observe(long validMs, boolean counting, long elapsed, int boot) {
        if (!active || validMs < 0) throw new IllegalArgumentException("Inactive or invalid work");
        WorkProgress p = refresh(elapsed, boot);
        if (clockChanged(elapsed, boot)) validMs = 0;
        long round = Math.addExact(p.sessionValidMs, validMs);
        long reward = earnedFor(round, p.roundMinutes) - earnedFor(p.sessionValidMs, p.roundMinutes);
        long interrupted = counting ? -1 : p.interruptedAtElapsedMs >= 0 ? p.interruptedAtElapsedMs : elapsed;
        if (validMs == 0 && !counting && interrupted == p.interruptedAtElapsedMs) return p;
        return new WorkProgress(Math.addExact(p.totalValidMs, validMs), round,
            Math.addExact(p.sessionRewardMs, reward), p.startedWallMs, true,
            interrupted, elapsed, boot, p.roundMinutes, p.selectedMinutes);
    }

    public WorkProgress finish() {
        return new WorkProgress(totalValidMs, sessionValidMs, sessionRewardMs,
            startedWallMs, false, -1, checkpointElapsedMs, checkpointBootCount, roundMinutes, selectedMinutes);
    }

    /** Resetting a round preserves sessionRewardMs, so it can never debit the wallet. */
    public long rewardSince(WorkProgress before) {
        long delta = active && before.active ? sessionRewardMs - before.sessionRewardMs : 0;
        if (delta < 0) throw new IllegalArgumentException("Previously awarded reward cannot be removed");
        return delta;
    }

    public long resetRemainingMs(long elapsed) {
        if (!active || interruptedAtElapsedMs < 0) return -1;
        return Math.max(0, RESET_AFTER_MS - Math.max(0, elapsed - interruptedAtElapsedMs));
    }

    public static long earnedFor(long roundValidMs) { return earnedFor(roundValidMs, 10); }

    public static long earnedFor(long roundValidMs, int minutes) {
        if (!validMinutes(minutes)) throw new IllegalArgumentException("Unknown reward ratio");
        if (roundValidMs < 0) throw new IllegalArgumentException("Negative valid time");
        return roundValidMs < minutes * MINUTE_MS ? 0 : (roundValidMs / MINUTE_MS) * (REWARD_NODE_MS / minutes);
    }

    public long rewardIntervalMs() { return sessionValidMs < thresholdMs() ? thresholdMs() : MINUTE_MS; }
    public long remainderMs() { return sessionValidMs % rewardIntervalMs(); }
    public long nextRewardInMs() { return rewardIntervalMs() - remainderMs(); }
}
