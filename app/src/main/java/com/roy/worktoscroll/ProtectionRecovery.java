package com.roy.worktoscroll;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.time.ZoneId;

/** No camera access. Reminders use one inexact alarm; only system recovery events start protection. */
public final class ProtectionRecovery extends BroadcastReceiver {
    private static final String REMIND = BuildConfig.APPLICATION_ID + ".REMIND_UNPROTECTED";
    static final String EXTRA_BOOT_RECOVERY = BuildConfig.APPLICATION_ID + ".BOOT_RECOVERY";
    private static final String CHANNEL = "unprotected_reminder";
    private static final int NOTICE = 2;

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("protection_reminder", Context.MODE_PRIVATE);
    }

    private static PendingIntent alarmIntent(Context context) {
        return PendingIntent.getBroadcast(context, 2,
            new Intent(context, ProtectionRecovery.class).setAction(REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void stopped(Context context) {
        preferences(context).edit().putLong("anchor_ms", System.currentTimeMillis()).commit();
        sync(context, false);
    }

    static void sync(Context context, boolean recover) {
        sync(context, recover, false);
    }

    private static void sync(Context context, boolean recover, boolean bootRecovery) {
        LedgerStore ledger = LedgerStore.get(context);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (ledger.enabled) {
            alarms.cancel(alarmIntent(context));
            context.getSystemService(NotificationManager.class).cancel(NOTICE);
            if (!recover) return;
            if (!Permissions.usage(context) || !Permissions.overlay(context) || !Permissions.targetPipReady(context)) {
                if (bootRecovery) BootRecoveryStatus.record(context, BootRecoveryPolicy.Outcome.MISSING_PERMISSION);
                return;
            }
            if (ProtectionService.current() != null) {
                if (bootRecovery) BootRecoveryStatus.record(context, BootRecoveryPolicy.Outcome.STARTED);
                return;
            }
            if (!ledger.save()) {
                if (bootRecovery) BootRecoveryStatus.record(context, BootRecoveryPolicy.Outcome.STORAGE_FAILED);
                return;
            }
            Intent start = new Intent(context, ProtectionService.class);
            if (bootRecovery) {
                BootRecoveryStatus.record(context, BootRecoveryPolicy.Outcome.START_REQUESTED);
                start.putExtra(EXTRA_BOOT_RECOVERY, true);
            }
            try { context.startForegroundService(start); }
            catch (RuntimeException ex) {
                if (bootRecovery) BootRecoveryStatus.record(context, BootRecoveryPolicy.Outcome.START_REJECTED);
                ledger.note = bootRecovery ? "开机恢复被系统拒绝，请检查小米自启动后再试。"
                    : "系统暂未允许恢复保护，请打开应用检查状态。";
                ledger.save();
            }
            return;
        }
        long now = System.currentTimeMillis();
        SharedPreferences prefs = preferences(context);
        long anchor = prefs.getLong("anchor_ms", 0);
        if (anchor <= 0 || anchor > now) {
            anchor = now;
            if (!prefs.edit().putLong("anchor_ms", anchor).commit()) return;
        }
        long due = ProtectionPolicy.nextReminder(now, anchor, ZoneId.systemDefault());
        // Inexact by design; no exact-alarm access or repeated polling service is needed.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, alarmIntent(context));
    }

    @Override public void onReceive(Context context, Intent intent) {
        // Do not activate an installation that has not been opened under the new policy yet.
        if (context.getSharedPreferences("ledger", Context.MODE_PRIVATE)
                .getInt("protection_policy_version", 0) < 1) return;
        LedgerStore ledger = LedgerStore.get(context);
        String action = intent.getAction();
        boolean bootRecovery = Intent.ACTION_BOOT_COMPLETED.equals(action);
        if (!ledger.refreshDailyReset()) {
            if (bootRecovery) BootRecoveryStatus.record(context, BootRecoveryPolicy.Outcome.STORAGE_FAILED);
            return;
        }
        if (REMIND.equals(action) && !ledger.enabled) {
            SharedPreferences prefs = preferences(context);
            long now = System.currentTimeMillis();
            long anchor = prefs.getLong("anchor_ms", 0);
            if (ProtectionPolicy.nextReminder(now, anchor, ZoneId.systemDefault()) <= now) {
                // Reserve the slot before posting: duplicate broadcasts cannot create duplicate reminders.
                if (prefs.edit().putLong("anchor_ms", now).commit()) {
                    NotificationManager manager = context.getSystemService(NotificationManager.class);
                    NotificationChannel channel = new NotificationChannel(CHANNEL, "未保护状态提醒", NotificationManager.IMPORTANCE_LOW);
                    channel.setSound(null, null);
                    channel.enableVibration(false);
                    manager.createNotificationChannel(channel);
                    if (manager.areNotificationsEnabled()) {
                        PendingIntent open = PendingIntent.getActivity(context, 2,
                            new Intent(context, MainActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        manager.notify(NOTICE, new Notification.Builder(context, CHANNEL)
                            .setSmallIcon(R.drawable.ic_launcher)
                            .setContentTitle("目前未开启保护")
                            .setContentText(BuildConfig.TARGET_NAME + "用时不会扣减，建议回到 " + context.getString(R.string.app_name) + " 开启保护。")
                            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build());
                    }
                }
            }
        }
        if (bootRecovery) BootRecoveryStatus.record(context, ledger.enabled
            ? BootRecoveryPolicy.Outcome.RECEIVED : BootRecoveryPolicy.Outcome.MANUAL_STOPPED);
        boolean recover = bootRecovery || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        sync(context, recover, bootRecovery);
    }
}
