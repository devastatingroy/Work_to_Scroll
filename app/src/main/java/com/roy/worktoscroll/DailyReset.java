package com.roy.worktoscroll;

import java.time.Instant;
import java.time.ZoneId;

/** Persisted local 06:00 deadline. Calendar time never participates in work timing. */
public record DailyReset(boolean enabled, long nextMs, String zone, long lastMs, boolean notice) {
    public static long nextAfter(long now, ZoneId zone) {
        var local = Instant.ofEpochMilli(now).atZone(zone);
        var next = local.toLocalDate().atTime(6, 0).atZone(zone);
        if (!next.toInstant().isAfter(local.toInstant())) next = local.toLocalDate().plusDays(1).atTime(6, 0).atZone(zone);
        return next.toInstant().toEpochMilli();
    }

    public static DailyReset initial(long now, ZoneId zone) {
        return new DailyReset(true, nextAfter(now, zone), zone.getId(), 0, false);
    }

    public boolean due(long now) { return enabled && nextMs > 0 && now >= nextMs; }

    public DailyReset advance(long now, ZoneId currentZone, boolean hadBalance) {
        boolean expired = due(now);
        long last = lastMs;
        if (expired) {
            // A multi-day recovery settles all elapsed local boundaries, not just the first
            // missed one. Keep that watermark when the clock later rolls back or zones change.
            var local = Instant.ofEpochMilli(now).atZone(currentZone);
            var boundary = local.toLocalDate().atTime(6, 0).atZone(currentZone);
            if (boundary.toInstant().isAfter(local.toInstant()))
                boundary = local.toLocalDate().minusDays(1).atTime(6, 0).atZone(currentZone);
            last = Math.max(nextMs, boundary.toInstant().toEpochMilli());
        }
        long next = nextMs;
        if (enabled && (expired || next <= 0 || !zone.equals(currentZone.getId())))
            next = nextAfter(Math.max(now, last), currentZone);
        return new DailyReset(enabled, next, currentZone.getId(), last, notice || (expired && hadBalance));
    }

    /** Caller settles the old deadline before toggling; enabling never clears immediately. */
    public DailyReset toggle(boolean value, long now, ZoneId currentZone) {
        if (value == enabled) return this;
        return new DailyReset(value, value ? nextAfter(Math.max(now, lastMs), currentZone) : 0,
            currentZone.getId(), lastMs, notice);
    }

    public DailyReset acknowledged() { return new DailyReset(enabled, nextMs, zone, lastMs, false); }
}
