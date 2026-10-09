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
    static final String TARGET = BuildConfig.TARGET_PACKAGE;
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
    RelaxWindow night;
    RelaxWindow noon;
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
        night = prefs.contains("night_relax_enabled")
            ? new RelaxWindow(prefs.getBoolean("night_relax_enabled", true),
                prefs.getInt("night_relax_start_min", 22 * 60), prefs.getInt("night_relax_end_min", 8 * 60 + 30),
                prefs.getLong("night_relax_allowance_ms", 45 * 60_000L),
                prefs.getLong("night_relax_remaining_ms", 45 * 60_000L),
                prefs.getLong("night_relax_window_start_ms", 0),
                prefs.getString("night_relax_zone", zoneClock.get().getId()))
            : RelaxWindow.night(zoneClock.get());
        noon = prefs.contains("noon_relax_enabled")
            ? new RelaxWindow(prefs.getBoolean("noon_relax_enabled", true),
                prefs.getInt("noon_relax_start_min", 11 * 60), prefs.getInt("noon_relax_end_min", 13 * 60),
                prefs.getLong("noon_relax_allowance_ms", 30 * 60_000L),
                prefs.getLong("noon_relax_remaining_ms", 30 * 60_000L),
                prefs.getLong("noon_relax_window_start_ms", 0),
                prefs.getString("noon_relax_zone", zoneClock.get().getId()))
            : RelaxWindow.noon(zoneClock.get());
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

    /** Refill when a new window opens; a mid-window process restart keeps the remaining quota. */
    void refreshWindows(long now, ZoneId zone) {
        night = night.refreshed(now, zone);
        noon = noon.refreshed(now, zone);
    }

    /** The window currently granting free time, or null when none is active. */
    RelaxWindow giftWindow(long now, ZoneId zone) {
        if (night.enabled() && night.active(now, zone)) return night;
        if (noon.enabled() && noon.active(now, zone)) return noon;
        return null;
    }

    /** Writes the settled remainder back to whichever window was spent from. */
    void setWindowRemaining(RelaxWindow window, long remaining) {
        if (window == night) night = night.withRemaining(remaining);
        else if (window == noon) noon = noon.withRemaining(remaining);
    }

    /** Earliest boundary of any window after {@code now}; settles each segment as one state. */
    long nextWindowEdge(long now, ZoneId zone) {
        return Math.min(night.nextEdge(now, zone), noon.nextEdge(now, zone));
    }

    /** Human label of the window granting free time right now; empty when none is active. */
    String giftLabel(long now, ZoneId zone) {
        RelaxWindow window = giftWindow(now, zone);
        return window == null ? "" : window == night ? "夜间赠送" : "午间赠送";
    }

    boolean setNightEnabled(boolean value) { return setWindowEnabled(value, true); }

    boolean setNoonEnabled(boolean value) { return setWindowEnabled(value, false); }

    private boolean setWindowEnabled(boolean value, boolean isNight) {
        RelaxWindow before = isNight ? night : noon;
        RelaxWindow next = before.withEnabled(value);
        if (isNight) night = next; else noon = next;
        if (save()) return true;
        if (isNight) night = before; else noon = before;
        return false;
    }

    /** Developer-only reset of every balance currently available to the user. */
    boolean clearAvailableForTest() {
        if (!ensureRewardRules() || !refreshDailyReset()) return false;
        refreshWindows(wallClock.getAsLong(), zoneClock.get());
        long beforeBalance = engine.balanceMs;
        RelaxWindow beforeNight = night, beforeNoon = noon;
        engine.balanceMs = 0;
        night = night.withRemaining(0);
        noon = noon.withRemaining(0);
        if (writeSnapshot(work, 0)) return true;
        engine.balanceMs = beforeBalance;
        night = beforeNight;
        noon = beforeNoon;
        return false;
    }

    String nightDescription() {
        return windowDescription(night, "夜间放松", "每晚");
    }

    String noonDescription() {
        return windowDescription(noon, "午间放松", "每天中午");
    }

    private String windowDescription(RelaxWindow window, String name, String prefix) {
        if (!window.enabled()) return name + "已关闭，全天按余额保护。";
        return prefix + " " + hm(window.startMin()) + "–" + hm(window.endMin()) + " 送 "
            + window.allowanceMs() / 60_000 + " 分钟" + BuildConfig.TARGET_NAME + "时间；用完后按余额照常保护，"
            + hm(window.endMin()) + " 恢复。未用完的部分到 " + hm(window.endMin()) + " 作废，下次重给。";
    }

    /** Remaining gift time of the active window, with its closing time, for the home screen. */
    String giftDetail(long now, ZoneId zone) {
        RelaxWindow window = giftWindow(now, zone);
        if (window == null || window.remainingMs() <= 0) return "";
        return giftLabel(now, zone) + " " + window.remainingMs() / 60_000 + " 分钟（至 " + hm(window.endMin()) + "）";
    }

    private static String hm(int minute) {
        return String.format("%02d:%02d", minute / 60, minute % 60);
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
            .putBoolean("night_relax_enabled", night.enabled())
            .putInt("night_relax_start_min", night.startMin()).putInt("night_relax_end_min", night.endMin())
            .putLong("night_relax_allowance_ms", night.allowanceMs())
            .putLong("night_relax_remaining_ms", night.remainingMs())
            .putLong("night_relax_window_start_ms", night.windowStartMs())
            .putString("night_relax_zone", night.zone())
            .putBoolean("noon_relax_enabled", noon.enabled())
            .putInt("noon_relax_start_min", noon.startMin()).putInt("noon_relax_end_min", noon.endMin())
            .putLong("noon_relax_allowance_ms", noon.allowanceMs())
            .putLong("noon_relax_remaining_ms", noon.remainingMs())
            .putLong("noon_relax_window_start_ms", noon.windowStartMs())
            .putString("noon_relax_zone", noon.zone())
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
