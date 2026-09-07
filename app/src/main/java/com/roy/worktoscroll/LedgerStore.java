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
    final int bootCount;
    boolean enabled;
    String note;
    long blocks;
    boolean storageHealthy = true;

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
        enabled = prefs.getBoolean("enabled", false);
        note = prefs.getString("note", "");
        blocks = prefs.getLong("blocks", 0);
        if (prefs.getInt("boot_count", bootCount) != bootCount
                || now < engine.cursorMs || now - engine.cursorMs > 6 * 60 * 60_000L) {
            engine.resetObservation(now);
            note = "设备重启或监测长时间中断：余额已保留，中断期间的用量未核实。";
        }
    }

    boolean save() {
        storageHealthy = prefs.edit().putLong("balance_ms", engine.balanceMs)
            .putLong("consumed_ms", engine.consumedMs).putLong("cursor_ms", engine.cursorMs)
            .putBoolean("target_active", engine.targetActive).putInt("boot_count", bootCount)
            .putBoolean("enabled", enabled).putString("note", note).putLong("blocks", blocks).commit();
        return storageHealthy;
    }
}
