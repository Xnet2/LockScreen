package com.custom.lockscreen;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.telecom.TelecomManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

/** Which apps may be in front while the phone is locked. */
final class Apps {
    private Apps() {}

    static boolean allowedWhileLocked(Context c, String pkg, boolean inCall) {
        if (pkg == null) return true;
        if (pkg.equals(c.getPackageName()) || pkg.equals("com.android.systemui") || pkg.equals("android")) return true;
        if (isIme(c, pkg) || isLauncher(c, pkg)) return true;
        return inCall && isCallApp(c, pkg);
    }

    static boolean isIme(Context c, String pkg) {
        try {
            InputMethodManager imm = c.getSystemService(InputMethodManager.class);
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (info.getPackageName().equals(pkg)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static boolean isLauncher(Context c, String pkg) {
        try {
            ResolveInfo ri = c.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            return ri != null && ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isCallApp(Context c, String pkg) {
        if (pkg.equals("com.android.server.telecom") || pkg.contains("incallui") || pkg.contains("dialer")) return true;
        try {
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            return tm != null && pkg.equals(tm.getDefaultDialerPackage());
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("MissingPermission")
    static void showCallScreen(Context c) {
        try {
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            if (tm != null) tm.showInCallScreen(false);
        } catch (Exception e) {
            goHome(c);
        }
    }

    static void goHome(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }
}
