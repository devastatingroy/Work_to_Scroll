package com.roy.worktoscroll;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtectionPolicyTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private long at(String local) { return ZonedDateTime.parse(local + "+08:00[Asia/Shanghai]").toInstant().toEpochMilli(); }

    @Test public void freshAndLegacyInstallDefaultOnButSavedOptOutWins() {
        assertTrue(ProtectionPolicy.enabledOnLoad(false, false));
        assertTrue(ProtectionPolicy.enabledOnLoad(false, true));
        assertFalse(ProtectionPolicy.enabledOnLoad(true, false));
        assertTrue(ProtectionPolicy.enabledOnLoad(true, true));
    }
    @Test public void firstTickAndNormalPollingAreNotInterruptions() {
        assertFalse(ProtectionPolicy.observationInterrupted(0, 60_000));
        assertFalse(ProtectionPolicy.observationInterrupted(10_000, 10_500));
        assertFalse(ProtectionPolicy.observationInterrupted(10_000, 15_000));
    }
    @Test public void longFreezeAndElapsedRollbackRequireReconciliation() {
        assertTrue(ProtectionPolicy.observationInterrupted(10_000, 15_001));
        assertTrue(ProtectionPolicy.observationInterrupted(10_000, 130_000));
        assertTrue(ProtectionPolicy.observationInterrupted(10_000, 9_000));
    }
    @Test public void firstReminderWaitsFourHours() {
        long now = at("2026-09-08T10:00:00");
        assertEquals(at("2026-09-08T14:00:00"), ProtectionPolicy.nextReminder(now, now, zone));
    }
    @Test public void reopeningDoesNotPostponeReminder() {
        assertEquals(at("2026-09-08T14:00:00"), ProtectionPolicy.nextReminder(
            at("2026-09-08T12:00:00"), at("2026-09-08T10:00:00"), zone));
    }
    @Test public void overdueDaytimeCanDeliverOnceThenWaitAgain() {
        long now = at("2026-09-08T15:15:00");
        assertEquals(now, ProtectionPolicy.nextReminder(now, at("2026-09-08T10:00:00"), zone));
        assertEquals(at("2026-09-08T19:15:00"), ProtectionPolicy.nextReminder(now, now, zone));
    }
    @Test public void nightAndNinePmMoveToTomorrowMorning() {
        long now = at("2026-09-08T17:00:00");
        assertEquals(at("2026-09-09T09:00:00"), ProtectionPolicy.nextReminder(now, now, zone));
        assertEquals(at("2026-09-09T09:00:00"), ProtectionPolicy.nextReminder(
            at("2026-09-08T22:00:00"), at("2026-09-08T10:00:00"), zone));
    }
    @Test public void earlyMorningNeverNotifiesBeforeNine() {
        long now = at("2026-09-08T03:00:00");
        assertEquals(at("2026-09-08T09:00:00"), ProtectionPolicy.nextReminder(now, now, zone));
    }
    @Test public void lateNightStopStillWaitsFourHours() {
        long anchor = at("2026-09-08T23:30:00");
        assertEquals(at("2026-09-09T09:00:00"), ProtectionPolicy.nextReminder(anchor, anchor, zone));
    }
    @Test public void missingAnchorAndClockRollbackStartNewWait() {
        long now = at("2026-09-08T10:00:00");
        assertEquals(at("2026-09-08T14:00:00"), ProtectionPolicy.nextReminder(now, 0, zone));
        assertEquals(at("2026-09-08T14:00:00"), ProtectionPolicy.nextReminder(now, now + 86_400_000, zone));
    }
    @Test public void timeZoneChangeUsesCurrentLocalQuietHours() {
        long now = at("2026-09-08T20:00:00");
        assertEquals(now, ProtectionPolicy.nextReminder(now, now - 14_400_000, ZoneId.of("Europe/London")));
        assertTrue(ProtectionPolicy.nextReminder(now, now - 14_400_000, ZoneId.of("Asia/Tokyo")) > now);
    }
}
