package com.custom.lockscreen;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.animation.PathInterpolator;

final class Ui {
    /** Colours for the unlock inputs: on the lock screen (dark) or in the light setup screen. */
    static final class Palette {
        final int fg, err, accent, ripple, keyBg;

        Palette(int fg, int err, int accent, int ripple, int keyBg) {
            this.fg = fg; this.err = err; this.accent = accent; this.ripple = ripple; this.keyBg = keyBg;
        }
    }

    static final Palette ON_DARK = new Palette(Color.WHITE, 0xFFFFB4AB, 0xFFA8C0FF, 0x40FFFFFF, 0x24FFFFFF);
    static final Palette ON_LIGHT = new Palette(0xFF1B1D22, 0xFFD93025, 0xFF3B5BDB, 0x22000000, 0x0F000000);

    // Light settings app
    static final int L_BG = 0xFFF4F5F9;
    static final int L_CARD = 0xFFFFFFFF;
    static final int L_TEXT = 0xFF1B1D22;
    static final int L_SUB = 0xFF6B7280;
    static final int L_ACCENT = 0xFF3B5BDB;
    static final int L_LINE = 0xFFE6E8EF;

    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static float dpf(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    /** Roboto on Android 12+ is a variable font, so any weight 100..900 renders crisply. */
    static Typeface font(int weight) {
        return Typeface.create(Typeface.SANS_SERIF, weight, false);
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static GradientDrawable rounded(int color, float radius) {
        return rounded(color, radius, 0, 0);
    }

    static GradientDrawable rounded(int color, float radius, int strokeW, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, strokeColor);
        return g;
    }

    static RippleDrawable ripple(Drawable content, Drawable mask) {
        return ripple(content, mask, 0x40FFFFFF);
    }

    static RippleDrawable ripple(Drawable content, Drawable mask, int color) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, mask);
    }

    static void vibrate(Context ctx, boolean error) {
        try {
            VibratorManager vm = ctx.getSystemService(VibratorManager.class);
            if (vm == null) return;
            Vibrator vib = vm.getDefaultVibrator();
            VibrationEffect effect = error
                    ? VibrationEffect.createWaveform(new long[]{0, 40, 70, 40}, -1)
                    : VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
            vib.vibrate(effect);
        } catch (Exception ignored) {
        }
    }

    static void shake(View v) {
        float d = dpf(v.getContext(), 10f);
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, d, -d, d * 0.7f, -d * 0.7f, d * 0.3f, -d * 0.3f, 0f)
                .setDuration(420)
                .start();
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static int blend(int a, int b, float t) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Smooth 0..1 ramp between edges a and b. */
    static float smooth(float a, float b, float x) {
        float t = clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
