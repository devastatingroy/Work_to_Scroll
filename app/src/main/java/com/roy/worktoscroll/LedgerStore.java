package com.roy.worktoscroll;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/** MainActivity and service run in the same process and share one ledger on the main thread. */
final class LedgerStore {
    static final String TARGET = "com.xingin.xhs";
    private static LedgerStore instance;
    final SharedPreferences prefs;
    final BalanceEngine engine;
    WorkProgress work;
    final int bootCount;
    boolean enabled;
    String note;
    long blocks;
    long observationGaps;
    long lastObservationGapMs;
    boolean storageHealthy = true;
    private int rewardRuleVersion;
    long workUpgradeCreditMs;

    static LedgerStore get(Context context) {
        if (instance == null) instance = new LedgerStore(context.getApplicationContext());
        return instance;
    }

    private LedgerStore(Context context) {
        prefs = context.getSharedPreferences("ledger", Context.MODE_PRIVATE);
        bootCount = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        long now = System.currentTimeMillis();
        engine = new BalanceEngine(prefs.getLong("balance_ms", 0), prefs.getLong("consumed_ms", 0),
            prefs.getLong("cursor_ms", now), prefs.getBoolean("target_active", false));
        enabled = ProtectionPolicy.enabledOnLoad(prefs.getInt("protection_policy_version", 0) >= 1,
            prefs.getBoolean("enabled", false));
        note = prefs.getString("note", "");
        blocks = prefs.getLong("blocks", 0);
        observationGaps = prefs.getLong("observation_gaps", 0);
        lastObservationGapMs = prefs.getLong("last_observation_gap_ms", 0);
        rewardRuleVersion = prefs.getInt("work_reward_rule_version", 1);
        workUpgradeCreditMs = prefs.getLong("work_upgrade_credit_ms", 0);
        work = new WorkProgress(prefs.getLong("work_total_ms", 0),
            prefs.getLong("work_session_ms", 0), prefs.getLong("work_reward_ms", 0),
            prefs.getLong("work_started_wall_ms", 0), prefs.getBoolean("work_active", false),
            prefs.getLong("work_interrupted_elapsed_ms", -1), prefs.getLong("work_checkpoint_elapsed_ms", -1),
            prefs.getInt("work_checkpoint_boot_count", -1));
        if (prefs.getInt("boot_count", bootCount) != bootCount
                || now < engine.cursorMs || now - engine.cursorMs > 6 * 60 * 60_000L) {
            engine.resetObservation(now);
            note = "设备重启或监测长时间中断：余额已保留，中断期间的用量未核实。";
        }
        if (ensureRewardRules()) saveWork(work.recover(android.os.SystemClock.elapsedRealtime(), bootCount));
    }

    boolean setProtectionEnabled(boolean value) {
        boolean before = enabled;
        enabled = value;
        if (save()) return true;
        enabled = before;
        return false;
    }

    boolean save() {
        return ensureRewardRules() && writeSnapshot(work, engine.balanceMs);
    }

    private boolean ensureRewardRules() {
        if (rewardRuleVersion >= 3) return true;
        WorkProgress migrated = work.migrateRoundRules(android.os.SystemClock.elapsedRealtime(), bootCount);
        if (!writeSnapshot(migrated, engine.balanceMs, 3, workUpgradeCreditMs)) return false;
        work = migrated;
        rewardRuleVersion = 3;
        return true;
    }

    /** All callers are on the main thread, as is the protection service. */
    boolean saveWork(WorkProgress next) {
        WorkProgress beforeRules = work;
        if (!ensureRewardRules() || beforeRules != work) return false;
        long reward = next.rewardSince(work);
        long newBalance = Math.addExact(engine.balanceMs, reward);
        if (!writeSnapshot(next, newBalance)) return false;
        // Publish only after the balance and work checkpoint have committed together.
        work = next;
        engine.balanceMs = newBalance;
        return true;
    }

    private boolean writeSnapshot(WorkProgress progress, long balance) {
        return writeSnapshot(progress, balance, rewardRuleVersion, workUpgradeCreditMs);
    }

    private boolean writeSnapshot(WorkProgress progress, long balance, int ruleVersion, long upgradeCredit) {
        storageHealthy = prefs.edit().putLong("balance_ms", balance)
            .putInt("protection_policy_version", 1)
            .putInt("work_reward_rule_version", ruleVersion)
            .putLong("work_upgrade_credit_ms", upgradeCredit)
            .putLong("consumed_ms", engine.consumedMs).putLong("cursor_ms", engine.cursorMs)
            .putBoolean("target_active", engine.targetActive).putInt("boot_count", bootCount)
            .putBoolean("enabled", enabled).putString("note", note).putLong("blocks", blocks)
            .putLong("observation_gaps", observationGaps)
            .putLong("last_observation_gap_ms", lastObservationGapMs)
            .putLong("work_total_ms", progress.totalValidMs())
            .putLong("work_session_ms", progress.sessionValidMs())
            .putLong("work_reward_ms", progress.sessionRewardMs())
            .putLong("work_started_wall_ms", progress.startedWallMs())
            .putBoolean("work_active", progress.active())
            .putLong("work_interrupted_elapsed_ms", progress.interruptedAtElapsedMs())
            .putLong("work_checkpoint_elapsed_ms", progress.checkpointElapsedMs())
            .putInt("work_checkpoint_boot_count", progress.checkpointBootCount()).commit();
        return storageHealthy;
    }
}
