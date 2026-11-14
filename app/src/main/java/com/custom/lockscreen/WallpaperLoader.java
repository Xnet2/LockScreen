package com.custom.lockscreen;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;

import java.io.File;

final class WallpaperLoader {
    private static String key;
    private static Bitmap cached;

    private WallpaperLoader() {}

    static File customFile(Context c) { return new File(c.getFilesDir(), "custom_bg"); }

    /** Returns the background bitmap, or null when nothing is readable (use {@link #fallback()}). */
    static Bitmap load(Context ctx) {
        if ("custom".equals(Prefs.bgMode(ctx))) {
            File f = customFile(ctx);
            if (f.exists()) {
                String k = "custom:" + f.lastModified();
                if (k.equals(key) && cached != null) return cached;
                Bitmap b = decodeFile(ctx, f);
                if (b != null) return store(ctx, k, b);
            }
        }

        WallpaperManager wm = WallpaperManager.getInstance(ctx);
        int id;
        try {
            id = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } catch (Exception e) {
            id = -1;
        }
        String k = "system:" + id;
        if (id != -1 && k.equals(key) && cached != null) return cached;

        Drawable d;
        try {
            d = wm.getDrawable();
        } catch (Exception e) {
            d = null; // usually: storage permission not granted
        }
        if (d == null) return null;

        Bitmap b = null;
        if (d instanceof BitmapDrawable) b = ((BitmapDrawable) d).getBitmap();
        if (b == null) {
            try {
                DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : dm.widthPixels;
                int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : dm.heightPixels;
                b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, w, h);
                d.draw(new Canvas(b));
            } catch (Exception e) {
                b = null;
            }
        }
        return b == null ? null : store(ctx, k, b);
    }

    private static Bitmap blurredFor;
    private static Bitmap blurred;

    /** Scale down to about screen size: drawing a huge bitmap every frame costs smoothness. */
    private static Bitmap store(Context ctx, String k, Bitmap b) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float target = Math.max(dm.widthPixels, dm.heightPixels) * 1.1f;
        float s = target / Math.max(b.getWidth(), b.getHeight());
        if (s < 1f) {
            try {
                Bitmap soft = b.getConfig() == Bitmap.Config.HARDWARE ? b.copy(Bitmap.Config.ARGB_8888, false) : b;
                b = Bitmap.createScaledBitmap(soft, Math.max(1, Math.round(b.getWidth() * s)),
                        Math.max(1, Math.round(b.getHeight() * s)), true);
            } catch (Exception ignored) {
            }
        }
        key = k;
        cached = b;
        return b;
    }

    /**
     * A blurred copy of the wallpaper, computed ONCE on a tiny version of the image
     * (then stretched by the ImageView). Blurring live on every frame made animations stutter.
     */
    static Bitmap blurred(Bitmap src) {
        if (src == blurredFor && blurred != null) return blurred;
        try {
            Bitmap soft = src.getConfig() == Bitmap.Config.HARDWARE ? src.copy(Bitmap.Config.ARGB_8888, false) : src;
            int w = Math.max(8, src.getWidth() / 12), h = Math.max(8, src.getHeight() / 12);
            Bitmap small = Bitmap.createScaledBitmap(soft, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            for (int pass = 0; pass < 3; pass++) {
                boxBlur(px, w, h, 3, true);
                boxBlur(px, w, h, 3, false);
            }
            small.setPixels(px, 0, w, 0, 0, w, h);
            blurredFor = src;
            blurred = small;
            return small;
        } catch (Exception e) {
            return null;
        }
    }

    private static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        int[] out = new int[len];
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = px[idx(Math.max(0, Math.min(len - 1, i)), line, w, horizontal)];
                sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[i] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = px[idx(Math.min(len - 1, i + r + 1), line, w, horizontal)];
                int rem = px[idx(Math.max(0, i - r), line, w, horizontal)];
                sr += ((add >> 16) & 0xFF) - ((rem >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((rem >> 8) & 0xFF);
                sb += (add & 0xFF) - (rem & 0xFF);
            }
            for (int i = 0; i < len; i++) px[idx(i, line, w, horizontal)] = out[i];
        }
    }

    private static int idx(int i, int line, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    /** Seed colour of the home wallpaper (no permission needed) for tinting the clock. */
    static Integer accentColor(Context ctx) {
        try {
            WallpaperColors wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            return wc == null ? null : wc.getPrimaryColor().toArgb();
        } catch (Exception e) {
            return null;
        }
    }

    static Drawable fallback() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF26345F, 0xFF0B0F1E, 0xFF3A2251});
    }

    private static Bitmap decodeFile(Context ctx, File f) {
        try {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int target = Math.max(dm.widthPixels, dm.heightPixels);
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), (dec, info, src) -> {
                int m = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                int s = 1;
                while (m / (s * 2) >= target) s *= 2;
                dec.setTargetSampleSize(s);
            });
        } catch (Exception e) {
            return null;
        }
    }
}
