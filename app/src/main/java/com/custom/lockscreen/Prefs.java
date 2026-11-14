package com.custom.lockscreen;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("lock_prefs", Context.MODE_PRIVATE);
    }

    private static String get(Context c, String k, String def) { return sp(c).getString(k, def); }
    private static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    static boolean isEnabled(Context c) { return sp(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }

    /** Persisted so the lock comes back if the process is killed or the phone reboots. */
    static boolean isLocked(Context c) { return sp(c).getBoolean("locked", false); }
    static void setLocked(Context c, boolean v) { sp(c).edit().putBoolean("locked", v).commit(); }

    /** One of ClockFace.STYLES keys. */
    static String clockStyle(Context c) { return get(c, "clock_style", "stacked"); }
    static void setClockStyle(Context c, String v) { put(c, "clock_style", v); }

    /** One of ClockFace.COLORS keys. */
    static String clockColor(Context c) { return get(c, "clock_color", "auto"); }
    static void setClockColor(Context c, String v) { put(c, "clock_color", v); }

    /** s / m / l */
    static String clockSize(Context c) { return get(c, "clock_size", "m"); }
    static void setClockSize(Context c, String v) { put(c, "clock_size", v); }

    /** top / center */
    static String clockPos(Context c) { return get(c, "clock_pos", "top"); }
    static void setClockPos(Context c, String v) { put(c, "clock_pos", v); }

    /** Notifications on the lock screen: show / private (app only) / off */
    static String notifMode(Context c) { return get(c, "notif_mode", "show"); }
    static void setNotifMode(Context c, String v) { put(c, "notif_mode", v); }

    /** "system" (home-screen wallpaper) or "custom" (picked image) */
    static String bgMode(Context c) { return get(c, "bg_mode", "system"); }
    static void setBgMode(Context c, String v) { put(c, "bg_mode", v); }

    static boolean wasAsked(Context c, String perm) { return sp(c).getBoolean("asked_" + perm, false); }
    static void setAsked(Context c, String perm) { sp(c).edit().putBoolean("asked_" + perm, true).apply(); }
}
