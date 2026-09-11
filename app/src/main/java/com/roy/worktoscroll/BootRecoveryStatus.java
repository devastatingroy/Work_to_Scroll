package com.roy.worktoscroll;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/** Small, local-only boot trace. It stores no usage events, screen content, or camera data. */
final class BootRecoveryStatus {
    private static final String PREFS = "boot_recovery_status";
    private static final String OUTCOME = "outcome";
    private static final String OUTCOME_BOOT = "outcome_boot_count";
    private static final String OUTCOME_WALL = "outcome_wall_ms";
    private static final String LAST_SEEN_BOOT = "last_seen_boot_count";

    record Snapshot(BootRecoveryPolicy.Outcome outcome, int bootCount, long wallMs) {
        String description(boolean protectionEnabled, int currentBoot) {
            if (!protectionEnabled) return "保护已手动停止";
            if (currentBoot >= 0 && bootCount != currentBoot) return "本次开机尚无恢复记录";
            return switch (outcome) {
                case STARTED -> "本次开机已恢复保护";
                case RECEIVED -> "已收到开机恢复请求";
                case START_REQUESTED -> "已请求恢复，等待服务启动";
                case MANUAL_STOPPED -> "上次开机时保护已手动停止";
                case MISSING_PERMISSION -> "开机未恢复：缺少必要授权";
                case START_REJECTED -> "开机恢复被系统拒绝";
                case STORAGE_FAILED -> "开机恢复状态保存失败";
                case NO_BOOT_RECORD -> "本次开机未收到恢复记录";
                case NONE -> "尚无开机恢复记录";
            };
        }
    }

    private BootRecoveryStatus() { }

    static void inspectOnAppOpen(Context context, boolean protectionEnabled) {
        SharedPreferences prefs = preferences(context);
        int currentBoot = bootCount(context);
        int lastSeenBoot = prefs.getInt(LAST_SEEN_BOOT, -1);
        int recordedBoot = prefs.getInt(OUTCOME_BOOT, -1);
        if (BootRecoveryPolicy.missingRecordAfterNewBoot(protectionEnabled, lastSeenBoot,
                currentBoot, recordedBoot)) {
            record(context, BootRecoveryPolicy.Outcome.NO_BOOT_RECORD, currentBoot);
        }
        if (currentBoot >= 0) prefs.edit().putInt(LAST_SEEN_BOOT, currentBoot).commit();
    }

    static void record(Context context, BootRecoveryPolicy.Outcome outcome) {
        record(context, outcome, bootCount(context));
    }

    static void record(Context context, BootRecoveryPolicy.Outcome outcome, int bootCount) {
        preferences(context).edit().putString(OUTCOME, outcome.name())
            .putInt(OUTCOME_BOOT, bootCount).putLong(OUTCOME_WALL, System.currentTimeMillis())
            .commit();
    }

    static Snapshot read(Context context) {
        SharedPreferences prefs = preferences(context);
        String value = prefs.getString(OUTCOME, BootRecoveryPolicy.Outcome.NONE.name());
        BootRecoveryPolicy.Outcome outcome;
        try { outcome = BootRecoveryPolicy.Outcome.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ignored) { outcome = BootRecoveryPolicy.Outcome.NONE; }
        return new Snapshot(outcome, prefs.getInt(OUTCOME_BOOT, -1), prefs.getLong(OUTCOME_WALL, 0));
    }

    static int bootCount(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
