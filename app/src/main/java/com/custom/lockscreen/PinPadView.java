package com.custom.lockscreen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Numeric keypad. With autoLength > 0 it submits automatically once that many
 * digits were typed (like the stock lock screen); with 0 it shows an OK key.
 */
public class PinPadView extends LinearLayout {

    Consumer<String> onSubmit;

    private final int autoLength;
    private final StringBuilder input = new StringBuilder();
    private final DotsView dots;
    private final ArrayList<View> keys = new ArrayList<>();

    private final Ui.Palette pal;

    public PinPadView(Context ctx, int autoLength, Ui.Palette pal) {
        super(ctx);
        this.autoLength = autoLength;
        this.pal = pal;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR); // keypad stays 1-2-3 even in Hebrew

        dots = new DotsView(ctx, pal);
        dots.setSlots(autoLength);
        LayoutParams dlp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(ctx, 24));
        dlp.bottomMargin = Ui.dp(ctx, 30);
        addView(dots, dlp);

        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9",
                autoLength == 0 ? "OK" : "", "0", "DEL"};
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            row.setLayoutDirection(LAYOUT_DIRECTION_LTR);
            for (int c = 0; c < 3; c++) row.addView(makeKey(labels[r * 3 + c]));
            LayoutParams rlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            if (r > 0) rlp.topMargin = Ui.dp(ctx, 12);
            addView(row, rlp);
        }
    }

    private View makeKey(String label) {
        Context ctx = getContext();
        View v;
        if (label.equals("DEL") || label.equals("OK")) {
            ImageView iv = new ImageView(ctx);
            boolean del = label.equals("DEL");
            iv.setImageResource(del ? R.drawable.ic_backspace : R.drawable.ic_check);
            iv.setImageTintList(ColorStateList.valueOf(pal.fg));
            iv.setScaleType(ImageView.ScaleType.CENTER);
            iv.setContentDescription(del ? "מחיקה" : "אישור");
            iv.setBackground(Ui.ripple(null, Ui.oval(Color.WHITE), pal.ripple));
            if (del) {
                iv.setOnClickListener(x -> backspace());
                iv.setOnLongClickListener(x -> { clear(); return true; });
            } else {
                iv.setOnClickListener(x -> submit());
            }
            v = iv;
        } else if (label.isEmpty()) {
            v = new View(ctx);
            v.setVisibility(INVISIBLE);
        } else {
            TextView tv = new TextView(ctx);
            tv.setText(label);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(32f);
            tv.setIncludeFontPadding(false);
            tv.setTypeface(Ui.font(300));
            tv.setTextColor(pal.fg);
            tv.setBackground(Ui.ripple(Ui.oval(pal.keyBg), Ui.oval(Color.WHITE), pal.ripple));
            tv.setOnClickListener(x -> press(label));
            v = tv;
        }
        int size = Ui.dp(ctx, 76);
        LayoutParams lp = new LayoutParams(size, size);
        lp.setMarginStart(Ui.dp(ctx, 12));
        lp.setMarginEnd(Ui.dp(ctx, 12));
        v.setLayoutParams(lp);
        keys.add(v);
        return v;
    }

    private void press(String d) {
        if (input.length() >= 16) return;
        input.append(d);
        dots.setError(false);
        dots.setCount(input.length());
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (autoLength > 0 && input.length() == autoLength) postDelayed(this::submit, 90);
    }

    private void backspace() {
        if (input.length() == 0) return;
        input.deleteCharAt(input.length() - 1);
        dots.setCount(input.length());
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void submit() {
        if (input.length() == 0 || onSubmit == null) return;
        onSubmit.accept(input.toString());
    }

    void clear() {
        input.setLength(0);
        dots.setError(false);
        dots.setCount(0);
    }

    void showError() {
        dots.setError(true);
        postDelayed(this::clear, 650);
    }

    void setBusy(boolean busy) {
        for (View k : keys) k.setEnabled(!busy);
    }

    void setInputEnabled(boolean b) {
        for (View k : keys) k.setEnabled(b);
        setAlpha(b ? 1f : 0.4f);
    }
}
