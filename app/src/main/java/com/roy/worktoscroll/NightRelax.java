package com.roy.worktoscroll;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Pure nightly relax allowance. A fixed local time window (default 22:00–08:30) grants a
 * bounded free quota that is spent before earned balance; once used up, normal balance rules
 * apply. The persisted protection switch is never touched, so the pre-window state resumes on
 * its own when the window closes. Wall-clock only, mirroring {@link DailyReset}.
 */
public record NightRelax(boolean enabled, int startMin, int endMin, long allowanceMs,
                         long remainingMs, long windowStartMs, String zone) {

    static NightRelax initial(ZoneId zone) {
        long allowance = 45 * 60_000L;
        return new NightRelax(true, 22 * 60, 8 * 60 + 30, allowance, allowance, 0, zone.getId());
    }

    private static int minuteOfDay(long now, ZoneId zone) {
        var t = Instant.ofEpochMilli(now).atZone(zone).toLocalTime();
        return t.getHour() * 60 + t.getMinute();
    }

    private long atMinute(LocalDate date, int minute, ZoneId zone) {
        return date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli();
    }

    /** Pure time-window test; callers combine with {@link #enabled()} where relevant. */
    boolean active(long now, ZoneId zone) {
        int m = minuteOfDay(now, zone);
        return startMin <= endMin ? (m >= startMin && m < endMin) : (m >= startMin || m < endMin);
    }

    /** Epoch of the window-opening boundary for the window active at {@code now}. */
    long windowStartKey(long now, ZoneId zone) {
        var local = Instant.ofEpochMilli(now).atZone(zone);
        int m = local.getHour() * 60 + local.getMinute();
        LocalDate startDate = (m >= startMin) ? local.toLocalDate() : local.toLocalDate().minusDays(1);
        return atMinute(startDate, startMin, zone);
    }

    /** Next start/end boundary strictly after {@code now}; used to settle in single-state segments. */
    long nextEdge(long now, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        long best = Long.MAX_VALUE;
        for (int d = 0; d <= 1; d++) {
            LocalDate day = date.plusDays(d);
            for (int minute : new int[]{startMin, endMin}) {
                long edge = atMinute(day, minute, zone);
                if (edge > now && edge < best) best = edge;
            }
        }
        return best;
    }

    /** Refill only when a new night's window opens; a mid-window restart keeps the remaining quota. */
    NightRelax refreshed(long now, ZoneId currentZone) {
        String zid = currentZone.getId();
        boolean sameZone = zone.equals(zid);
        if (!enabled || !active(now, currentZone))
            return sameZone ? this
                : new NightRelax(enabled, startMin, endMin, allowanceMs, remainingMs, windowStartMs, zid);
        long key = windowStartKey(now, currentZone);
        if (key == windowStartMs && sameZone) return this;
        return new NightRelax(enabled, startMin, endMin, allowanceMs, allowanceMs, key, zid);
    }

    NightRelax withRemaining(long remaining) {
        long r = Math.max(0, Math.min(allowanceMs, remaining));
        return r == remainingMs ? this
            : new NightRelax(enabled, startMin, endMin, allowanceMs, r, windowStartMs, zone);
    }

    NightRelax withEnabled(boolean value) {
        if (value == enabled) return this;
        // Turning it back on forces a fresh refill the next time the window opens.
        return new NightRelax(value, startMin, endMin, allowanceMs, remainingMs, value ? 0 : windowStartMs, zone);
    }
}
