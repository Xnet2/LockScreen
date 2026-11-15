package com.custom.lockscreen;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 3x3 unlock-pattern grid. Dots are numbered 0..8, left-to-right, top-to-bottom.
 * Selected dots get a thin ring that eases in around them; the connecting line
 * runs between the rings and follows the finger continuously.
 */
public class PatternView extends View {

    enum Mode { NORMAL, WRONG, CORRECT }

    private static final long RING_MS = 120; // quick, quiet emphasis

    Consumer<List<Integer>> onComplete;
    boolean inputEnabled = true;

    private final Ui.Palette pal;
    private Mode mode = Mode.NORMAL;

    private final ArrayList<Integer> selected = new ArrayList<>();
    private final boolean[] isSel = new boolean[9];
    private final long[] selTime = new long[9];
    private final float[] cx = new float[9];
    private final float[] cy = new float[9];
    private float cell;
    private float fingerX, fingerY;
    private boolean tracking;
    private float fade = 1f; // whole-pattern opacity (used to fade out a wrong pattern)
    private ValueAnimator fadeAnim;

    private final float dotR, dotSelR, ringR;
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PatternView(Context ctx, Ui.Palette pal) {
        super(ctx);
        this.pal = pal;
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        dotR = Ui.dpf(ctx, 6.5f);      // normal, comfortable dot size
        dotSelR = Ui.dpf(ctx, 8f);     // selected: just slightly larger
        ringR = Ui.dpf(ctx, 12.5f);    // small ring hugging the dot when it's selected
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(Ui.dpf(ctx, 2f));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Ui.dpf(ctx, 3f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private final android.graphics.Path path = new android.graphics.Path();

    void setMode(Mode m) {
        mode = m;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float s = Math.min(w, h);
        cell = s / 3f;
        float ox = (w - s) / 2f;
        float oy = (h - s) / 2f;
        for (int i = 0; i < 9; i++) {
            cx[i] = ox + cell * (i % 3 + 0.5f);
            cy[i] = oy + cell * (i / 3 + 0.5f);
        }
    }

    void clear() {
        if (fadeAnim != null) fadeAnim.cancel();
        selected.clear();
        Arrays.fill(isSel, false);
        tracking = false;
        fade = 1f;
        setMode(Mode.NORMAL);
    }

    /** Keep the pattern visible for a moment, then fade it out softly and clear. */
    void fadeOutAndClear(long delay, Runnable done) {
        if (fadeAnim != null) fadeAnim.cancel();
        fadeAnim = ValueAnimator.ofFloat(1f, 0f);
        fadeAnim.setStartDelay(delay);
        fadeAnim.setDuration(320);
        fadeAnim.setInterpolator(Ui.EASE);
        fadeAnim.addUpdateListener(a -> {
            fade = (Float) a.getAnimatedValue();
            invalidate();
            if (a.getAnimatedFraction() >= 1f) {
                clear();
                if (done != null) done.run();
            }
        });
        fadeAnim.start();
    }

    private int hit(float x, float y) {
        for (int i = 0; i < 9; i++) {
            // Catch a dot only when the finger is really on it, so the line doesn't snap from afar.
            if (!isSel[i] && Math.hypot(x - cx[i], y - cy[i]) < cell * 0.24f) return i;
        }
        return -1;
    }

    private void add(int i) {
        if (!selected.isEmpty()) {
            // Passing straight over a dot selects it too (like the system pattern).
            int last = selected.get(selected.size() - 1);
            int r1 = last / 3, c1 = last % 3, r2 = i / 3, c2 = i % 3;
            if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                int mid = ((r1 + r2) / 2) * 3 + (c1 + c2) / 2;
                if (!isSel[mid]) mark(mid);
            }
        }
        mark(i);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void mark(int i) {
        selected.add(i);
        isSel[i] = true;
        selTime[i] = SystemClock.uptimeMillis();
    }

    private void tryAdd(float x, float y) {
        int h = hit(x, y);
        if (h >= 0) add(h);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!inputEnabled) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                clear();
                tracking = true;
                fingerX = e.getX();
                fingerY = e.getY();
                tryAdd(fingerX, fingerY);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return true;
                // Use every intermediate touch sample so fast swipes never skip a dot.
                for (int h = 0; h < e.getHistorySize(); h++) tryAdd(e.getHistoricalX(h), e.getHistoricalY(h));
                fingerX = e.getX();
                fingerY = e.getY();
                tryAdd(fingerX, fingerY);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                if (!tracking) return true;
                tracking = false;
                invalidate();
                if (!selected.isEmpty() && onComplete != null) onComplete.accept(new ArrayList<>(selected));
                break;
            case MotionEvent.ACTION_CANCEL:
                clear();
                break;
        }
        return true;
    }

    /** 0..1 ease-out progress of a dot's ring animation. */
    private float ringT(int i, long now) {
        float t = Ui.clamp((now - selTime[i]) / (float) RING_MS, 0f, 1f);
        return t * t * (3f - 2f * t); // gentle in-and-out, no "pop"
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean animating = false;
        int color = mode == Mode.WRONG ? pal.err : mode == Mode.CORRECT ? pal.accent : pal.fg;
        int a = (int) (255 * fade);

        // One continuous, round-jointed line through the dots to the finger.
        if (!selected.isEmpty()) {
            path.rewind();
            path.moveTo(cx[selected.get(0)], cy[selected.get(0)]);
            for (int k = 1; k < selected.size(); k++) path.lineTo(cx[selected.get(k)], cy[selected.get(k)]);
            if (tracking) path.lineTo(fingerX, fingerY);
            linePaint.setColor(Ui.withAlpha(color, (int) (0x8C * fade)));
            canvas.drawPath(path, linePaint);
        }

        // Dots: a dragged dot is only gently emphasised (a touch bigger, fully opaque). No rings.
        for (int i = 0; i < 9; i++) {
            if (isSel[i]) {
                float t = ringT(i, now);
                if (t < 1f) animating = true;
                dotPaint.setColor(Ui.withAlpha(color, a));
                canvas.drawCircle(cx[i], cy[i], dotR + (dotSelR - dotR) * t, dotPaint);
            } else {
                dotPaint.setColor(Ui.withAlpha(pal.fg, (int) (0x8C * fade)));
                canvas.drawCircle(cx[i], cy[i], dotR, dotPaint);
            }
        }
        if (animating) postInvalidateOnAnimation();
    }

    /** Draws a line from (x1,y1) to (x2,y2), trimmed by r1 / r2 at each end so it meets the rings. */
    private void segment(Canvas c, float x1, float y1, float r1, float x2, float y2, float r2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len <= r1 + r2 + 1f) return;
        float ux = dx / len, uy = dy / len;
        c.drawLine(x1 + ux * r1, y1 + uy * r1, x2 - ux * r2, y2 - uy * r2, linePaint);
    }
}
