package com.roy.worktoscroll;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.view.accessibility.AccessibilityManager;

final class Permissions {
    static boolean usage(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    static boolean accessibility(Context context) {
        ComponentName expected = new ComponentName(context, ProtectionService.class);
        for (AccessibilityServiceInfo info : context.getSystemService(AccessibilityManager.class)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            var service = info.getResolveInfo().serviceInfo;
            if (expected.equals(new ComponentName(service.packageName, service.name))) return true;
        }
        return false;
    }
}
