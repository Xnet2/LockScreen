package com.custom.lockscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Row of dots showing how many PIN digits were typed. */
public class DotsView extends View {
    private final Ui.Palette pal;
    private int count, slots;
    private boolean error;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DotsView(Context ctx, Ui.Palette pal) {
        super(ctx);
        this.pal = pal;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dpf(ctx, 1.5f));
    }

    void setCount(int v) { count = v; invalidate(); }
    void setSlots(int v) { slots = v; invalidate(); }
    void setError(boolean v) { error = v; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        int n = Math.max(count, slots);
        if (n == 0) return;
        float r = Ui.dpf(getContext(), 6f);
        float gap = Ui.dpf(getContext(), 20f);
        float x = getWidth() / 2f - (n - 1) * gap / 2f;
        float y = getHeight() / 2f;
        int col = error ? pal.err : pal.fg;
        fill.setColor(col);
        stroke.setColor(Ui.withAlpha(col, 0x99));
        for (int i = 0; i < n; i++) {
            if (i < count) c.drawCircle(x, y, r, fill);
            else c.drawCircle(x, y, r - stroke.getStrokeWidth() / 2f, stroke);
            x += gap;
        }
    }
}
