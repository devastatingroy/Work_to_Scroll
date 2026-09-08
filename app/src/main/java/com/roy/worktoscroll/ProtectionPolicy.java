package com.roy.worktoscroll;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Pure policy: persisted opt-out wins; reminders only during local daytime. */
final class ProtectionPolicy {
    static final long REMINDER_INTERVAL_MS = 4 * 60 * 60_000L;
    static final long MAX_OBSERVATION_GAP_MS = 5_000;

    static boolean observationInterrupted(long previousElapsed, long nowElapsed) {
        return previousElapsed > 0 && (nowElapsed < previousElapsed
            || nowElapsed - previousElapsed > MAX_OBSERVATION_GAP_MS);
    }

    static boolean enabledOnLoad(boolean migrated, boolean savedEnabled) {
        return !migrated || savedEnabled;
    }

    static long nextReminder(long now, long anchor, ZoneId zone) {
        if (anchor <= 0 || anchor > now) anchor = now;
        long earliest = Math.max(now, anchor + REMINDER_INTERVAL_MS);
        ZonedDateTime local = Instant.ofEpochMilli(earliest).atZone(zone);
        if (local.getHour() < 9) local = local.toLocalDate().atTime(9, 0).atZone(zone);
        else if (local.getHour() >= 21) local = local.toLocalDate().plusDays(1).atTime(9, 0).atZone(zone);
        return local.toInstant().toEpochMilli();
    }
}
