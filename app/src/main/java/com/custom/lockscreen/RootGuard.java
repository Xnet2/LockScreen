package com.custom.lockscreen;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Root version of "full protection" (no accessibility service needed). While locked:
 *  - the notification shade / quick settings are disabled at the system level
 *    (`cmd statusbar send-disable-flag statusbar-expansion`),
 *  - a watchdog sends away any app that comes to the front over the lock
 *    (during a call: anything other than the call screen),
 *  - optionally, booting into safe mode is disabled (`settings put global safe_boot_disallowed 1`).
 * Only active in the root build (Flavor.ROOT).
 */
final class RootGuard {
    private RootGuard() {}

    private static final String PKG = "com.custom.lockscreen";
    private static Context app;
    private static HandlerThread thread;
    private static Handler bg;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile long lockedAt;
    private static long handledTs;
    private static volatile boolean inCall;

    static void onLocked(Context c) {
        if (!Flavor.ROOT) return;
        app = c.getApplicationContext();
        lockedAt = System.currentTimeMillis();
        handledTs = lockedAt;
        Root.run("cmd statusbar collapse; cmd statusbar send-disable-flag statusbar-expansion");
        startWatch();
    }

    static void onUnlocked(Context c) {
        if (!Flavor.ROOT) return;
        stopWatch();
        clearShadeBlock();
    }

    static void onCall(Context c, boolean calling) {
        if (!Flavor.ROOT) return;
        inCall = calling;
        if (calling) clearShadeBlock(); // the call notification must stay reachable (re-blocked on re-lock)
    }

    static void clearShadeBlock() {
        if (!Flavor.ROOT) return;
        Root.run("cmd statusbar send-disable-flag");
    }

    /** Apply the "block safe mode" choice (only while the lock is enabled). */
    static void applySafeMode(Context c) {
        if (!Flavor.ROOT) return;
        boolean on = Prefs.isEnabled(c) && Prefs.blockSafeMode(c);
        Root.run("settings put global safe_boot_disallowed " + (on ? 1 : 0));
    }

    /** One tap: grants every permission the app needs, using root. */
    static void grantEverything(Runnable done) {
        Root.run("appops set " + PKG + " SYSTEM_ALERT_WINDOW allow; "
                + "pm grant " + PKG + " android.permission.READ_EXTERNAL_STORAGE; "
                + "pm grant " + PKG + " android.permission.READ_PHONE_STATE; "
                + "appops set " + PKG + " GET_USAGE_STATS allow; "
                + "dumpsys deviceidle whitelist +" + PKG + "; "
                + "cmd notification allow_listener " + PKG + "/" + PKG + ".NotifService", done);
    }

    // ------------------------------------------------------------------ watchdog

    private static synchronized void startWatch() {
        if (thread == null) {
            thread = new HandlerThread("lock-watch");
            thread.start();
            bg = new Handler(thread.getLooper());
        }
        bg.removeCallbacks(tick);
        bg.post(tick);
    }

    private static synchronized void stopWatch() {
        if (bg != null) bg.removeCallbacks(tick);
    }

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            Context c = app;
            if (c == null || !Prefs.isLocked(c) || !Prefs.isEnabled(c)) return;
            try {
                PowerManager pm = c.getSystemService(PowerManager.class);
                if (pm == null || pm.isInteractive()) check(c);
            } catch (Exception ignored) {
            }
            bg.postDelayed(this, 350);
        }
    };

    private static void check(Context c) {
        UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
        if (usm == null) return;
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(Math.max(handledTs, now - 10_000), now + 1000);
        UsageEvents.Event e = new UsageEvents.Event();
        String pkg = null;
        long ts = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED && e.getTimeStamp() > handledTs) {
                pkg = e.getPackageName();
                ts = e.getTimeStamp();
            }
        }
        if (pkg == null) return;
        handledTs = ts;
        boolean calling = inCall || LockOverlay.get(c).isInCall();
        if (Apps.allowedWhileLocked(c, pkg, calling)) return;
        main.post(() -> {
            if (calling) {
                Apps.showCallScreen(c);
            } else {
                Apps.goHome(c);
                LockOverlay.get(c).lock();
            }
        });
    }
}
