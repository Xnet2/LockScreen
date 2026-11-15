package com.custom.lockscreen;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

/**
 * "Full protection" (enabled by the user under Settings ▸ Accessibility).
 * While the phone is locked it:
 *  - shields the status bar so the notification shade / quick settings can't be pulled down,
 *  - closes the shade or the power menu if they still open,
 *  - sends any app that pops up (e.g. Settings, which hides normal overlays) straight back,
 *  - during a call, allows only the call screen,
 *  - and locks immediately at boot, before the normal boot broadcast arrives.
 */
public class LockGuardService extends AccessibilityService {

    static volatile LockGuardService instance;

    static boolean isEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        String me = new ComponentName(c, LockGuardService.class).flattenToString();
        String meShort = new ComponentName(c, LockGuardService.class).flattenToShortString();
        for (String part : s.split(":")) {
            if (part.equalsIgnoreCase(me) || part.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            // Backup for LockService: lock even if the system killed the service.
            if (Prefs.isEnabled(c) && !LockOverlay.get(c).isInCall()) LockOverlay.get(c).lock();
        }
    };

    @Override
    protected void onServiceConnected() {
        instance = this;
        try {
            registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        } catch (Exception ignored) {
        }
        if (Prefs.isEnabled(this)) {
            // Just booted: lock right away instead of waiting for BOOT_COMPLETED.
            if (SystemClock.elapsedRealtime() < 3 * 60_000) Prefs.setLocked(this, true);
            if (Prefs.isLocked(this)) LockOverlay.get(this).lock();
            try {
                LockService.start(this);
            } catch (Exception ignored) {
            }
        }
        LockOverlay.get(this).updateShield();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!Prefs.isEnabled(this) || !Prefs.isLocked(this)) return;
        CharSequence pkgCs = e.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        String cls = e.getClassName() == null ? "" : e.getClassName().toString();
        LockOverlay overlay = LockOverlay.get(this);

        if (overlay.isInCall()) {
            // During a call: only the call screen (and the system UI) may be used.
            if (!isCallPackage(pkg) && !pkg.equals("com.android.systemui") && !isOwnOrIme(pkg)) {
                showCallScreen();
            }
            return;
        }

        if (pkg.equals("com.android.systemui")) {
            if (cls.toLowerCase().contains("globalactions")) {
                performGlobalAction(GLOBAL_ACTION_BACK); // power menu (restart → safe mode etc.)
            } else {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
            }
            return;
        }
        if (isOwnOrIme(pkg) || isLauncher(pkg)) return;

        // Any other app came to the front while locked (from the shade, a shortcut, or an app
        // like Settings that hides overlays): send it away and put the lock back on top.
        performGlobalAction(GLOBAL_ACTION_HOME);
        overlay.lock();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenOff);
        } catch (Exception ignored) {
        }
        instance = null;
        LockOverlay.get(this).updateShield();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ helpers

    private boolean isOwnOrIme(String pkg) {
        if (pkg.equals(getPackageName())) return true;
        try {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (info.getPackageName().equals(pkg)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isLauncher(String pkg) {
        try {
            ResolveInfo ri = getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            return ri != null && ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCallPackage(String pkg) {
        if (pkg.equals("com.android.server.telecom") || pkg.contains("incallui") || pkg.contains("dialer")) return true;
        try {
            TelecomManager tm = getSystemService(TelecomManager.class);
            return tm != null && pkg.equals(tm.getDefaultDialerPackage());
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("MissingPermission")
    private void showCallScreen() {
        try {
            TelecomManager tm = getSystemService(TelecomManager.class);
            if (tm != null) tm.showInCallScreen(false);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }
}
