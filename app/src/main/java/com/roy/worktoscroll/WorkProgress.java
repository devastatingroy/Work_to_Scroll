package com.roy.worktoscroll;

/** Immutable persisted checkpoint; reward delta and checkpoint must commit together. */
public record WorkProgress(long totalValidMs, long sessionValidMs, long sessionRewardMs,
                           long startedWallMs, boolean active) {
    public static final long WORK_NODE_MS = 600_000;
    public static final long REWARD_NODE_MS = 300_000;
    public static final long MINUTE_MS = 60_000;
    public static final long MINUTE_REWARD_MS = 30_000;

    public WorkProgress {
        if (totalValidMs < 0 || sessionValidMs < 0 || sessionRewardMs < 0)
            throw new IllegalArgumentException("Negative work checkpoint");
    }

    public WorkProgress begin(long wallNow) {
        return active ? this : new WorkProgress(totalValidMs, 0, 0, wallNow, true);
    }

    public WorkProgress advance(long validMs) {
        if (!active || validMs < 0) throw new IllegalArgumentException("Inactive or invalid work");
        long total = Math.addExact(totalValidMs, validMs);
        long reward = earnedFor(total) - earnedFor(totalValidMs);
        return new WorkProgress(total, Math.addExact(sessionValidMs, validMs),
            Math.addExact(sessionRewardMs, reward), startedWallMs, true);
    }

    public WorkProgress finish() {
        return new WorkProgress(totalValidMs, sessionValidMs, sessionRewardMs, startedWallMs, false);
    }

    public static long earnedFor(long validMs) {
        if (validMs < 0) throw new IllegalArgumentException("Negative valid time");
        return validMs < WORK_NODE_MS ? 0 : (validMs / MINUTE_MS) * MINUTE_REWARD_MS;
    }

    /** One-time v1 to v2 credit; never recompute or replace already consumed balance. */
    public static long legacyUpgradeCreditFor(long validMs) {
        return earnedFor(validMs) - (validMs / WORK_NODE_MS) * REWARD_NODE_MS;
    }

    public long rewardIntervalMs() { return totalValidMs < WORK_NODE_MS ? WORK_NODE_MS : MINUTE_MS; }
    public long remainderMs() { return totalValidMs % rewardIntervalMs(); }
    public long nextRewardInMs() { return rewardIntervalMs() - remainderMs(); }
}
