package com.roy.worktoscroll;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.time.ZoneId;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.LongConsumer;

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
    DailyReset daily;
    private final LongSupplier wallClock, elapsedClock;
    private final Supplier<ZoneId> zoneClock;
    private final LongConsumer settleExpiry;

    static LedgerStore get(Context context) {
        if (instance == null) instance = new LedgerStore(context.getApplicationContext());
        return instance;
    }

    private LedgerStore(Context context) {
        this(context.getSharedPreferences("ledger", Context.MODE_PRIVATE),
            Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1),
            System::currentTimeMillis, android.os.SystemClock::elapsedRealtime, ZoneId::systemDefault,
            deadline -> {
                ProtectionService service = ProtectionService.current();
                if (service != null) service.settleBeforeExpiry(deadline);
            });
    }

    /** Injectable clocks and storage keep date/transaction regression tests off the user's ledger. */
    LedgerStore(SharedPreferences prefs, int bootCount, LongSupplier wallClock,
                LongSupplier elapsedClock, Supplier<ZoneId> zoneClock, LongConsumer settleExpiry) {
        this.prefs = prefs;
        this.bootCount = bootCount;
        this.wallClock = wallClock;
        this.elapsedClock = elapsedClock;
        this.zoneClock = zoneClock;
        this.settleExpiry = settleExpiry;
        long now = wallClock.getAsLong();
        daily = prefs.contains("daily_reset_enabled")
            ? new DailyReset(prefs.getBoolean("daily_reset_enabled", true), prefs.getLong("daily_reset_next_ms", 0),
                prefs.getString("daily_reset_zone", zoneClock.get().getId()), prefs.getLong("daily_reset_last_ms", 0),
                prefs.getBoolean("daily_reset_notice", false))
            : DailyReset.initial(now, zoneClock.get());
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
            prefs.getInt("work_checkpoint_boot_count", -1),
            validStoredMinutes("work_round_minutes"), validStoredMinutes("work_selected_minutes"));
        if (prefs.getInt("boot_count", bootCount) != bootCount
                || now < engine.cursorMs || now - engine.cursorMs > 6 * 60 * 60_000L) {
            engine.resetObservation(now);
            note = "设备重启或监测长时间中断：余额已保留，中断期间的用量未核实。";
        }
        if (ensureRewardRules()) saveWork(work.recover(elapsedClock.getAsLong(), bootCount));
    }

    private int validStoredMinutes(String key) {
        int value = prefs.getInt(key, 10);
        return WorkProgress.validMinutes(value) ? value : 10;
    }

    boolean refreshDailyReset() {
        long now = wallClock.getAsLong();
        boolean expired = daily.due(now);
        BalanceEngine.Checkpoint before = engine.checkpoint();
        try {
            if (expired) settleExpiry.accept(daily.nextMs());
        } catch (RuntimeException failure) {
            engine.restore(before);
            storageHealthy = false;
            return false;
        }
        DailyReset next = daily.advance(now, zoneClock.get(), engine.balanceMs > 0);
        if (next.equals(daily)) return true;
        long balance = expired ? 0 : engine.balanceMs;
        if (!writeSnapshot(work, balance, rewardRuleVersion, workUpgradeCreditMs, next)) {
            engine.restore(before);
            return false;
        }
        engine.balanceMs = balance;
        daily = next;
        return true;
    }

    boolean setDailyEnabled(boolean value) {
        if (!refreshDailyReset()) return false;
        DailyReset next = daily.toggle(value, wallClock.getAsLong(), zoneClock.get());
        if (!writeSnapshot(work, engine.balanceMs, rewardRuleVersion, workUpgradeCreditMs, next)) return false;
        daily = next;
        return true;
    }

    boolean selectMinutes(int minutes) {
        WorkProgress refreshed = work.refresh(elapsedClock.getAsLong(), bootCount);
        WorkProgress next = refreshed.selectMinutes(minutes).refresh(elapsedClock.getAsLong(), bootCount);
        return saveWork(next);
    }

    boolean acknowledgeReset() {
        if (!daily.notice()) return false;
        DailyReset next = daily.acknowledged();
        if (!writeSnapshot(work, engine.balanceMs, rewardRuleVersion, workUpgradeCreditMs, next)) return false;
        daily = next;
        return true;
    }

    String expiryDescription() {
        return daily.enabled() ? "每天早晨 06:00 清空剩余娱乐时间。可在设置中关闭。" : "每天清零已关闭，剩余娱乐时间继续保留。";
    }

    boolean setProtectionEnabled(boolean value) {
        boolean before = enabled;
        enabled = value;
        if (save()) return true;
        enabled = before;
        return false;
    }

    boolean save() {
        return ensureRewardRules() && refreshDailyReset() && writeSnapshot(work, engine.balanceMs);
    }

    private boolean ensureRewardRules() {
        if (rewardRuleVersion >= 3) return true;
        WorkProgress migrated = work.migrateRoundRules(elapsedClock.getAsLong(), bootCount);
        if (!writeSnapshot(migrated, engine.balanceMs, 3, workUpgradeCreditMs)) return false;
        work = migrated;
        rewardRuleVersion = 3;
        return true;
    }

    /** All callers are on the main thread, as is the protection service. */
    boolean saveWork(WorkProgress next) {
        WorkProgress beforeRules = work;
        if (!ensureRewardRules() || beforeRules != work) return false;
        if (!refreshDailyReset()) return false;
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
        return writeSnapshot(progress, balance, ruleVersion, upgradeCredit, daily);
    }

    private boolean writeSnapshot(WorkProgress progress, long balance, int ruleVersion, long upgradeCredit, DailyReset reset) {
        storageHealthy = prefs.edit()
            .putBoolean("daily_reset_enabled", reset.enabled()).putLong("daily_reset_next_ms", reset.nextMs())
            .putString("daily_reset_zone", reset.zone()).putLong("daily_reset_last_ms", reset.lastMs())
            .putBoolean("daily_reset_notice", reset.notice())
            .putInt("work_round_minutes", progress.roundMinutes()).putInt("work_selected_minutes", progress.selectedMinutes()).putLong("balance_ms", balance)
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
