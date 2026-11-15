package com.custom.lockscreen;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * Shows the lock screen as a full-screen overlay window (not dismissed by Home / Recents / Back).
 * One shared instance, used by both LockService and LockGuardService.
 *
 * With the "full protection" accessibility guard enabled, it also places an invisible
 * shield over the status bar so the notification shade / quick settings can't be pulled
 * down while locked.
 */
final class LockOverlay {

    private static LockOverlay instance;

    static synchronized LockOverlay get(Context c) {
        if (instance == null) instance = new LockOverlay(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final WindowManager wm;
    private LockScreenView view;
    private View shield;
    private boolean suspended;
    private boolean inCall;

    private LockOverlay(Context ctx) {
        this.ctx = ctx;
        this.wm = ctx.getSystemService(WindowManager.class);
    }

    boolean isInCall() { return inCall; }
    boolean isShowing() { return view != null; }

    void lock() {
        Prefs.setLocked(ctx, true);
        MainActivity.verifiedAt = 0; // settings need the code again after every lock
        if (suspended) return;
        if (view != null) {
            view.reset();
            updateShield();
            return;
        }
        if (!Settings.canDrawOverlays(ctx)) return;

        // Fail open on any unexpected error: a crash must never leave the phone unusable.
        try {
            LockScreenView v = new LockScreenView(ctx, this::unlock);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_SECURE, // no screenshots / screen recording / recents preview
                    PixelFormat.TRANSLUCENT);
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setFitInsetsTypes(0); // draw edge-to-edge, behind status & nav bar
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
            lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            lp.setTitle("LockScreen");
            wm.addView(v, lp);
            view = v;
            v.requestFocus();
        } catch (Exception e) {
            Log.e("LockOverlay", "failed to show lock", e);
        }
        updateShield();
    }

    void unlock() {
        Prefs.setLocked(ctx, false);
        removeShield();
        final LockScreenView v = view;
        if (v == null) return;
        view = null;
        v.animate()
                .alpha(0f)
                .setInterpolator(Ui.EASE)
                .setDuration(260)
                .withEndAction(() -> remove(v))
                .start();
    }

    // ------------------------------------------------------------------ phone calls

    /** Ringing / in a call: let the call screen through, but stay locked. */
    void onCallStarted() {
        inCall = true;
        suspended = true;
        removeShield();
        if (view != null) remove(view);
        view = null;
    }

    void onCallEnded() {
        if (!inCall) return;
        inCall = false;
        suspended = false;
        if (Prefs.isLocked(ctx)) lock();
    }

    void onScreenOn() {
        if (view != null) view.onScreenOn();
    }

    void destroy() {
        removeShield();
        if (view != null) remove(view);
        view = null;
    }

    // ------------------------------------------------------------------ status-bar shield

    /** Adds or removes the shield to match the current state (called also when the guard connects). */
    void updateShield() {
        LockGuardService g = LockGuardService.instance;
        boolean want = g != null && view != null && !suspended;
        if (want && shield == null) {
            try {
                View s = new View(g);
                s.setOnTouchListener((v, e) -> true); // swallow every touch on the status bar
                int h = statusBarHeight() + Ui.dp(ctx, 28);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, h,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = android.view.Gravity.TOP;
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                lp.setFitInsetsTypes(0);
                lp.setTitle("LockScreenShield");
                g.getSystemService(WindowManager.class).addView(s, lp);
                shield = s;
            } catch (Exception e) {
                Log.e("LockOverlay", "shield failed", e);
            }
        } else if (!want) {
            removeShield();
        }
    }

    private void removeShield() {
        View s = shield;
        shield = null;
        if (s == null) return;
        try {
            s.getContext().getSystemService(WindowManager.class).removeViewImmediate(s);
        } catch (Exception ignored) {
        }
    }

    private int statusBarHeight() {
        int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : Ui.dp(ctx, 32);
    }

    private void remove(LockScreenView v) {
        try {
            wm.removeViewImmediate(v);
        } catch (Exception ignored) {
        }
    }
}
