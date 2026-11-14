package com.custom.lockscreen;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

/**
 * Shows the lock screen as a full-screen system overlay window.
 * Unlike an Activity, an overlay is not dismissed by Home / Recents / Back.
 */
final class LockOverlay {

    private final Context ctx;
    private final WindowManager wm;
    private LockScreenView view;
    private boolean suspended;

    LockOverlay(Context ctx) {
        this.ctx = ctx;
        this.wm = ctx.getSystemService(WindowManager.class);
    }

    void lock() {
        Prefs.setLocked(ctx, true);
        if (suspended) return;
        if (view != null) {
            view.reset();
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
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
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
    }

    void unlock() {
        Prefs.setLocked(ctx, false);
        final LockScreenView v = view;
        if (v == null) return;
        view = null;
        v.animate()
                .alpha(0f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setInterpolator(Ui.EASE)
                .setDuration(340)
                .withEndAction(() -> remove(v))
                .start();
    }

    /** Temporarily hide (e.g. during a phone call) while staying locked. */
    void suspend() {
        suspended = true;
        if (view != null) remove(view);
        view = null;
    }

    void resume() {
        suspended = false;
        if (Prefs.isLocked(ctx)) lock();
    }

    void onScreenOn() {
        if (view != null) view.onScreenOn();
    }

    void destroy() {
        if (view != null) remove(view);
        view = null;
    }

    private void remove(LockScreenView v) {
        try {
            wm.removeViewImmediate(v);
        } catch (Exception ignored) {
        }
    }
}
