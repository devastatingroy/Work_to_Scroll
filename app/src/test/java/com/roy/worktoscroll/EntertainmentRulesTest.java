package com.roy.worktoscroll;

import android.content.SharedPreferences;
import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EntertainmentRulesTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static long time(String local) { return LocalDateTime.parse(local).atZone(ZONE).toInstant().toEpochMilli(); }
    private long now = time("2026-09-09T12:00:00"), elapsed = 1_000;
    private ZoneId zone = ZONE;
    private final MemoryPrefs prefs = new MemoryPrefs();
    private LedgerStore load() { return new LedgerStore(prefs, 1, () -> now, () -> elapsed, () -> zone, due -> {}); }
    private WorkProgress run(WorkProgress p, long ms) {
        p = p.observe(0, true, ++elapsed, 1);
        while (ms > 0) {
            long delta = Math.min(400, ms);
            elapsed += delta;
            p = p.observe(delta, true, elapsed, 1);
            ms -= delta;
        }
        return p;
    }
    private WorkProgress start(int minutes) {
        return new WorkProgress(0, 0, 0, 0, false, -1, -1, 1)
            .selectMinutes(minutes).begin(now, elapsed, 1);
    }

    @Test public void allRatiosHaveExactThresholdAndMinuteRewards() {
        for (int m : new int[]{10, 15, 20}) {
            WorkProgress p = run(start(m), m * 60_000L - 1);
            assertEquals(m, p.roundMinutes());
            assertEquals(0, p.earnedThisRound());
            assertEquals(1, p.nextRewardInMs());
            p = run(p, 1);
            assertEquals(300_000, p.sessionRewardMs());
            assertEquals(60_000, p.nextRewardInMs());
            p = run(p, 59_999);
            assertEquals(300_000, p.earnedThisRound());
            p = run(p, 1);
            assertEquals(300_000 + 300_000 / m, p.earnedThisRound());
            assertEquals(p.earnedThisRound(), p.sessionRewardMs());
        }
    }

    @Test public void ratioChangeSurvivesShortPauseButAppliesAfterTimeout() {
        WorkProgress p = run(start(10), 599_999).selectMinutes(20);
        p = p.pause(elapsed, 1);
        assertEquals(10, p.roundMinutes());
        elapsed += 179_998;
        p = run(p, 1);
        assertEquals(300_000, p.sessionRewardMs());
        p = p.pause(elapsed, 1);
        elapsed += 180_000;
        p = p.refresh(elapsed, 1);
        assertEquals(20, p.roundMinutes());
        assertEquals(300_000, p.sessionRewardMs());
        assertEquals(0, p.earnedThisRound());
        p = run(p, 1_200_000);
        assertEquals(600_000, p.sessionRewardMs());
    }

    @Test public void finishAndRebootAdoptPendingRatioWithoutRepricing() {
        WorkProgress p = run(start(20), 1_200_000).selectMinutes(15);
        assertEquals(20, p.roundMinutes());
        WorkProgress restarted = p.recover(1, 2);
        assertEquals(15, restarted.roundMinutes());
        assertEquals(300_000, restarted.sessionRewardMs());
        p = p.finish().begin(now, ++elapsed, 1);
        assertEquals(15, p.roundMinutes());
        assertEquals(0, p.earnedThisRound());
    }

    @Test public void upgradeDefaultsOnWithoutClearingAndPersistsOptOut() {
        prefs.edit().putInt("work_reward_rule_version", 3).putLong("balance_ms", 555398)
            .putLong("consumed_ms", 2804602).putLong("work_total_ms", 7182644)
            .putInt("protection_policy_version", 1).putBoolean("enabled", false).commit();
        LedgerStore l = load();
        assertTrue(l.daily.enabled());
        assertFalse(l.enabled);
        assertEquals(time("2026-09-10T06:00:00"), l.daily.nextMs());
        assertEquals(555398, l.engine.balanceMs);
        assertEquals(7182644, l.work.totalValidMs());
        assertTrue(l.setDailyEnabled(false));
        now += 3 * 86_400_000L;
        l = load();
        assertFalse(l.daily.enabled());
        assertEquals(555398, l.engine.balanceMs);
        assertTrue(l.setDailyEnabled(true));
        assertEquals(time("2026-09-13T06:00:00"), l.daily.nextMs());
        assertEquals(555398, l.engine.balanceMs);
    }

    @Test public void expiryAtSixPreservesHistoryAndNeverDebitsNewRewards() {
        LedgerStore l = load();
        l.engine.grant(555398); l.engine.consumedMs = 123;
        assertTrue(l.selectMinutes(15));
        assertTrue(l.saveWork(l.work.begin(now, elapsed, 1)));
        assertTrue(l.saveWork(run(l.work, 899_999)));
        now = time("2026-09-10T05:59:59.999");
        assertTrue(l.refreshDailyReset());
        assertEquals(555398, l.engine.balanceMs);
        now++;
        assertTrue(l.saveWork(run(l.work, 1)));
        assertEquals(300_000, l.engine.balanceMs);
        assertEquals(123, l.engine.consumedMs);
        assertEquals(900_000, l.work.totalValidMs());
        assertTrue(l.daily.notice());
        assertTrue(l.saveWork(l.work));
        assertEquals(300_000, l.engine.balanceMs);
        l = load();
        assertEquals(300_000, l.engine.balanceMs);
        assertTrue(l.acknowledgeReset());
        assertFalse(l.acknowledgeReset());
    }

    @Test public void zeroBalanceExpiryStillMarksBoundaryAndOfflineDaysSettleOnce() {
        LedgerStore l = load();
        now = time("2026-09-13T09:00:00");
        l = load();
        assertEquals(time("2026-09-14T06:00:00"), l.daily.nextMs());
        assertFalse(l.daily.notice());
        l.engine.grant(300_000); assertTrue(l.save());
        assertTrue(l.refreshDailyReset());
        assertEquals(300_000, load().engine.balanceMs);
    }

    @Test public void failedExpiryRollsBackAndRetriesBeforeGranting() {
        LedgerStore l = load();
        l.engine.grant(555398); assertTrue(l.save());
        now = time("2026-09-10T06:00:00");
        prefs.fail = true;
        assertFalse(l.refreshDailyReset());
        assertEquals(555398, l.engine.balanceMs);
        assertEquals(time("2026-09-10T06:00:00"), l.daily.nextMs());
        prefs.fail = false;
        assertTrue(l.refreshDailyReset());
        assertEquals(0, l.engine.balanceMs);
        assertEquals(555398, prefs.beforeFailureBalance);
        assertTrue(l.daily.notice());
    }

    @Test public void ratioPersistsAcrossProcessRecreationWithoutRepricing() {
        LedgerStore l = load();
        assertTrue(l.selectMinutes(15));
        assertTrue(l.saveWork(l.work.begin(now, elapsed, 1)));
        assertTrue(l.saveWork(run(l.work, 900_000)));
        assertTrue(l.selectMinutes(20));
        l = load();
        assertEquals(15, l.work.roundMinutes());
        assertEquals(20, l.work.selectedMinutes());
        assertEquals(300_000, l.engine.balanceMs);
        assertTrue(l.saveWork(l.work.finish()));
        assertTrue(l.saveWork(l.work.begin(now, ++elapsed, 1)));
        assertEquals(20, l.work.roundMinutes());
        assertEquals(300_000, l.engine.balanceMs);
    }

    @Test public void timeRollbackAndTimezoneChangesDoNotRepeatExpiry() {
        LedgerStore l = load();
        now = time("2026-09-10T06:00:00"); assertTrue(l.refreshDailyReset());
        l.engine.grant(300_000); assertTrue(l.save());
        now = time("2026-09-10T05:00:00"); assertTrue(l.refreshDailyReset());
        now = time("2026-09-10T06:00:00"); assertTrue(l.refreshDailyReset());
        assertEquals(300_000, l.engine.balanceMs);
        zone = ZoneId.of("Asia/Tokyo"); assertTrue(l.refreshDailyReset());
        assertEquals(6, Instant.ofEpochMilli(l.daily.nextMs()).atZone(zone).getHour());
        assertTrue(l.daily.nextMs() > now);
        assertEquals(300_000, l.engine.balanceMs);
    }

    @Test public void sixAmScheduleUsesCalendarAcrossDstAndExactActivationBoundary() {
        ZoneId ny = ZoneId.of("America/New_York");
        long start = LocalDateTime.parse("2026-03-07T06:00:00").atZone(ny).toInstant().toEpochMilli();
        assertEquals(23 * 3_600_000L, DailyReset.nextAfter(start, ny) - start);
        long six = time("2026-09-09T06:00:00");
        assertEquals(six + 86_400_000L, DailyReset.initial(six, ZONE).nextMs());
    }

    @Test public void multiDayRecoveryKeepsWatermarkAcrossRollbackAndZoneChange() {
        LedgerStore l = load();
        now = time("2026-09-13T09:00:00"); assertTrue(l.refreshDailyReset());
        long settled = time("2026-09-13T06:00:00");
        assertEquals(settled, l.daily.lastMs());
        l.engine.grant(300_000); assertTrue(l.save());
        now = time("2026-09-11T09:00:00"); zone = ZoneId.of("Asia/Tokyo");
        assertTrue(l.refreshDailyReset());
        assertTrue(l.daily.nextMs() > settled);
        assertEquals(300_000, l.engine.balanceMs);
    }

    @Test public void consumptionStopsAtDeadlineAndExpiryIsNotConsumption() {
        LedgerStore[] holder = new LedgerStore[1];
        LedgerStore l = new LedgerStore(prefs, 1, () -> now, () -> elapsed, () -> zone,
            due -> holder[0].engine.settle(List.of(), due));
        holder[0] = l;
        l.engine.grant(300_000);
        l.engine.resetObservation(time("2026-09-10T05:59:00"));
        l.engine.targetActive = true;
        assertTrue(l.save());
        now = time("2026-09-10T06:02:00");
        assertTrue(l.refreshDailyReset());
        assertEquals(60_000, l.engine.consumedMs);
        assertEquals(0, l.engine.balanceMs);
        l.engine.settle(List.of(), now);
        assertEquals(60_000, l.engine.consumedMs);
        assertTrue(l.daily.notice());
    }

    @Test public void failedExpiryRestoresConsumptionCursorAndBalanceTogether() {
        LedgerStore[] holder = new LedgerStore[1];
        LedgerStore l = new LedgerStore(prefs, 1, () -> now, () -> elapsed, () -> zone,
            due -> holder[0].engine.settle(List.of(), due));
        holder[0] = l;
        long cursor = time("2026-09-10T05:59:00");
        l.engine.grant(300_000); l.engine.resetObservation(cursor); l.engine.targetActive = true;
        assertTrue(l.save());
        now = time("2026-09-10T06:02:00"); prefs.fail = true;
        assertFalse(l.refreshDailyReset());
        assertEquals(300_000, l.engine.balanceMs);
        assertEquals(0, l.engine.consumedMs);
        assertEquals(cursor, l.engine.cursorMs);
        prefs.fail = false;
        assertTrue(l.refreshDailyReset());
        assertEquals(60_000, l.engine.consumedMs);
        assertEquals(0, l.engine.balanceMs);
    }

    @Test public void failedRewardCommitCannotMintOrLosePreviouslyCommittedCredit() {
        LedgerStore l = load();
        assertTrue(l.selectMinutes(20));
        assertTrue(l.saveWork(l.work.begin(now, elapsed, 1)));
        assertTrue(l.saveWork(run(l.work, 1_199_999)));
        WorkProgress rewarded = run(l.work, 1);
        prefs.fail = true;
        assertFalse(l.saveWork(rewarded));
        assertEquals(0, l.engine.balanceMs);
        assertEquals(1_199_999, l.work.sessionValidMs());
        prefs.fail = false;
        assertTrue(l.saveWork(rewarded));
        assertTrue(l.saveWork(rewarded));
        assertEquals(300_000, l.engine.balanceMs);
        assertEquals(300_000, load().engine.balanceMs);
    }

    /** Only test-owned memory; no device files and no Android runtime calls. */
    private static final class MemoryPrefs implements SharedPreferences {
        final Map<String, Object> data = new HashMap<>();
        boolean fail;
        long beforeFailureBalance;
        public Map<String, ?> getAll() { return new HashMap<>(data); }
        public String getString(String k, String d) { return (String)data.getOrDefault(k,d); }
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String k, Set<String> d) { return (Set<String>)data.getOrDefault(k,d); }
        public int getInt(String k, int d) { return (int)data.getOrDefault(k,d); }
        public long getLong(String k, long d) { return (long)data.getOrDefault(k,d); }
        public float getFloat(String k, float d) { return (float)data.getOrDefault(k,d); }
        public boolean getBoolean(String k, boolean d) { return (boolean)data.getOrDefault(k,d); }
        public boolean contains(String k) { return data.containsKey(k); }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        public Editor edit() { return new Editor() {
            final Map<String,Object> pending = new HashMap<>(); boolean clear;
            public Editor putString(String k,String v) { pending.put(k,v); return this; }
            public Editor putStringSet(String k,Set<String> v) { pending.put(k,v); return this; }
            public Editor putInt(String k,int v) { pending.put(k,v); return this; }
            public Editor putLong(String k,long v) { pending.put(k,v); return this; }
            public Editor putFloat(String k,float v) { pending.put(k,v); return this; }
            public Editor putBoolean(String k,boolean v) { pending.put(k,v); return this; }
            public Editor remove(String k) { pending.put(k,null); return this; }
            public Editor clear() { clear=true; return this; }
            public boolean commit() {
                if (fail) { beforeFailureBalance=getLong("balance_ms",0); return false; }
                if(clear) data.clear();
                pending.forEach((k,v) -> { if(v==null) data.remove(k); else data.put(k,v); }); return true;
            }
            public void apply() { commit(); }
        }; }
    }
}
