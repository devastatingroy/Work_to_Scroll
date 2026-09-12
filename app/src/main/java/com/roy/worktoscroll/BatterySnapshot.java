package com.roy.worktoscroll;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import org.json.JSONObject;

/** One cached BatteryService reading; does not schedule polling or keep the device awake. */
final class BatterySnapshot {
    static final int UNKNOWN = Integer.MIN_VALUE;

    final long wallMs;
    final long elapsedMs;
    final int temperatureTenthsC;
    final int levelPercent;
    final int voltageMv;
    final int status;
    final int plugged;
    final String error;

    BatterySnapshot(long wallMs, long elapsedMs, int temperatureTenthsC, int levelPercent,
                    int voltageMv, int status, int plugged, String error) {
        this.wallMs = wallMs;
        this.elapsedMs = elapsedMs;
        this.temperatureTenthsC = temperatureTenthsC;
        this.levelPercent = levelPercent;
        this.voltageMv = voltageMv;
        this.status = status;
        this.plugged = plugged;
        this.error = error == null ? "" : error;
    }

    static BatterySnapshot capture(Context context) {
        long wall = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return unavailable(wall, elapsed, "battery_status_unavailable");
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN);
            int percent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : UNKNOWN;
            return new BatterySnapshot(wall, elapsed,
                battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, UNKNOWN), percent,
                battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, UNKNOWN),
                battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
                battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0), "");
        } catch (RuntimeException failure) {
            return unavailable(wall, elapsed, failure.getClass().getSimpleName());
        }
    }

    static BatterySnapshot unavailable(long wall, long elapsed, String error) {
        return new BatterySnapshot(wall, elapsed, UNKNOWN, UNKNOWN, UNKNOWN,
            BatteryManager.BATTERY_STATUS_UNKNOWN, 0, error);
    }

    boolean temperatureAvailable() { return temperatureTenthsC != UNKNOWN; }

    JSONObject json(String prefix) throws Exception {
        return new JSONObject()
            .put(prefix + "battery_sample_wall_ms", wallMs)
            .put(prefix + "battery_sample_elapsed_ms", elapsedMs)
            .put(prefix + "battery_temperature_available", temperatureAvailable())
            .put(prefix + "battery_temperature_tenths_c", temperatureAvailable() ? temperatureTenthsC : JSONObject.NULL)
            .put(prefix + "battery_temperature_c", temperatureAvailable() ? temperatureTenthsC / 10.0 : JSONObject.NULL)
            .put(prefix + "battery_level_percent", levelPercent == UNKNOWN ? JSONObject.NULL : levelPercent)
            .put(prefix + "battery_voltage_mv", voltageMv == UNKNOWN ? JSONObject.NULL : voltageMv)
            .put(prefix + "battery_status", statusName(status))
            .put(prefix + "battery_plugged", plugged != 0)
            .put(prefix + "battery_power_source", pluggedName(plugged))
            .put(prefix + "battery_read_error", error);
    }

    static int temperatureDeltaTenths(BatterySnapshot start, BatterySnapshot end) {
        if (start == null || end == null || !start.temperatureAvailable() || !end.temperatureAvailable()) return UNKNOWN;
        return end.temperatureTenthsC - start.temperatureTenthsC;
    }

    static String statusName(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "not_charging";
            default: return "unknown";
        }
    }

    static String pluggedName(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "ac";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "usb";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "wireless";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_DOCK) != 0) return "dock";
        return "none";
    }
}
