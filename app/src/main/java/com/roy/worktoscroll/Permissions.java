package com.roy.worktoscroll;

import android.app.AppOpsManager;
import android.content.Context;
import android.provider.Settings;

final class Permissions {
    private static final String UI_PREFERENCES = "ui_preferences";
    private static final String TARGET_PIP_CONFIRMED = "target_pip_disabled_confirmed";

    static boolean usage(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    static boolean overlay(Context context) {
        return Settings.canDrawOverlays(context);
    }

    static boolean targetPipReady(Context context) {
        return !BuildConfig.TARGET_PIP_MUST_BE_DISABLED
            || context.getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(TARGET_PIP_CONFIRMED, false);
    }

    static void setTargetPipConfirmed(Context context, boolean confirmed) {
        context.getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE).edit()
            .putBoolean(TARGET_PIP_CONFIRMED, confirmed).apply();
    }
}
