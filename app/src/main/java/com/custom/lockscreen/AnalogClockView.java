package com.custom.lockscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Minimal analog clock: tick dots, a thick hour hand and a slim minute hand. */
public class AnalogClockView extends View {
    private int primary, secondary;
    private int hour, minute;
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AnalogClockView(Context ctx) {
        super(ctx);
        hand.setStyle(Paint.Style.STROKE);
        hand.setStrokeCap(Paint.Cap.ROUND);
        ring.setStyle(Paint.Style.STROKE);
        int shadow = 0x40000000;
        hand.setShadowLayer(Ui.dpf(ctx, 6), 0, Ui.dpf(ctx, 1), shadow);
        tick.setShadowLayer(Ui.dpf(ctx, 3), 0, 0, shadow);
    }

    void setColors(int primary, int secondary) {
        this.primary = primary;
        this.secondary = secondary;
        invalidate();
    }

    void setTime(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - Ui.dpf(getContext(), 6);
        float u = r / 120f; // scale unit, so the face looks the same at any size

        ring.setColor(Ui.withAlpha(secondary, 0x40));
        ring.setStrokeWidth(1.2f * u);
        c.drawCircle(cx, cy, r, ring);

        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6);
            float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
            if (i % 5 == 0) {
                boolean quarter = i % 15 == 0;
                tick.setColor(primary);
                float rr = quarter ? 4.2f * u : 2.8f * u;
                c.drawCircle(cx + sin * (r - 12 * u), cy - cos * (r - 12 * u), rr, tick);
            } else {
                tick.setColor(Ui.withAlpha(secondary, 0x66));
                c.drawCircle(cx + sin * (r - 12 * u), cy - cos * (r - 12 * u), 1.1f * u, tick);
            }
        }

        float hourAngle = (hour % 12 + minute / 60f) * 30f;
        float minAngle = minute * 6f;

        hand.setColor(primary);
        hand.setStrokeWidth(9 * u);
        drawHand(c, cx, cy, hourAngle, r * 0.50f, 10 * u);
        hand.setColor(secondary);
        hand.setStrokeWidth(5 * u);
        drawHand(c, cx, cy, minAngle, r * 0.80f, 14 * u);

        tick.setColor(primary);
        c.drawCircle(cx, cy, 8 * u, tick);
        tick.setColor(0xFF000000);
        c.drawCircle(cx, cy, 3 * u, tick);
    }

    private void drawHand(Canvas c, float cx, float cy, float deg, float len, float tail) {
        double a = Math.toRadians(deg);
        float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
        c.drawLine(cx - sin * tail, cy + cos * tail, cx + sin * len, cy - cos * len, hand);
    }
}
