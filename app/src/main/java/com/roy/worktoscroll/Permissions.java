package com.roy.worktoscroll;

import android.app.AppOpsManager;
import android.content.Context;
import android.provider.Settings;

final class Permissions {
    static boolean usage(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    static boolean overlay(Context context) {
        return Settings.canDrawOverlays(context);
    }
}
