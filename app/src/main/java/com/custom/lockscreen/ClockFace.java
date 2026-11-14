package com.custom.lockscreen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/** The clock on the lock screen (and in the settings preview), in one of several styles. */
public class ClockFace extends LinearLayout {

    static final String[][] STYLES = {
            {"stacked", "מוערם"}, {"inline", "דק"}, {"bold", "עבה"}, {"outline", "קו מתאר"},
            {"serif", "קלאסי"}, {"side", "צדדי"}, {"duo", "דו־גוני"}, {"analog", "מחוגים"},
    };
    static final String[][] COLORS = {
            {"auto", "מהרקע"}, {"white", "לבן"}, {"sky", "תכלת"}, {"pink", "ורוד"},
            {"gold", "זהב"}, {"mint", "מנטה"}, {"lavender", "לבנדר"}, {"coral", "אלמוג"},
    };
    static final String[][] SIZES = {{"s", "קטן"}, {"m", "בינוני"}, {"l", "גדול"}};
    static final String[][] POSITIONS = {{"top", "למעלה"}, {"center", "במרכז"}};

    static int presetColor(String key) {
        switch (key) {
            case "sky": return 0xFFA8D8FF;
            case "pink": return 0xFFFFB8D9;
            case "gold": return 0xFFFFD98A;
            case "mint": return 0xFFA6F0D2;
            case "lavender": return 0xFFD0BCFF;
            case "coral": return 0xFFFFB199;
            default: return Color.WHITE;
        }
    }

    /** {primary, secondary} colours for the current settings. */
    static int[] colors(Context c) {
        String key = Prefs.clockColor(c);
        if (key.equals("white")) return new int[]{Color.WHITE, 0xF2FFFFFF};
        if (key.equals("auto")) {
            Integer acc = "system".equals(Prefs.bgMode(c)) ? WallpaperLoader.accentColor(c) : null;
            return new int[]{Color.WHITE, acc != null ? Ui.blend(acc, Color.WHITE, 0.7f) : 0xFFE6EBFF};
        }
        int p = presetColor(key);
        return new int[]{p, Ui.blend(p, Color.WHITE, 0.55f)};
    }

    static float sizeFactor(Context c) {
        switch (Prefs.clockSize(c)) {
            case "s": return 0.8f;
            case "l": return 1.16f;
            default: return 1f;
        }
    }

    private String style = "";
    private int primary = Color.WHITE, secondary = Color.WHITE;
    private float k = 1f;
    private TextView date, hours, minutes, time;
    private AnalogClockView analog;

    public ClockFace(Context ctx) {
        super(ctx);
        setOrientation(VERTICAL);
    }

    /** Rebuilds the clock. scale multiplies the user's size (used for the small preview). */
    void configure(String style, int primary, int secondary, float scale) {
        this.style = style;
        this.primary = primary;
        this.secondary = secondary;
        this.k = scale;
        removeAllViews();
        date = hours = minutes = time = null;
        analog = null;
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(0, 0, 0, 0);

        switch (style) {
            case "inline":
                time = digits(96, 250, primary);
                add(time);
                date = label(17, 500, primary);
                add(date, 2, 0);
                break;
            case "bold":
                time = digits(104, 850, primary);
                time.setLetterSpacing(-0.04f);
                add(time);
                date = label(16, 650, secondary);
                date.setLetterSpacing(0.06f);
                add(date, 0, 0);
                break;
            case "outline": {
                date = label(17, 500, primary);
                add(date, 0, 4);
                hours = digits(136, 800, primary);
                hours.getPaint().setStyle(Paint.Style.STROKE);
                hours.getPaint().setStrokeWidth(dpf(2.2f));
                hours.setShadowLayer(0, 0, 0, 0);
                add(hours);
                minutes = digits(136, 800, secondary);
                add(minutes, -(int) (hours.getTextSize() * 0.36f), 0);
                break;
            }
            case "serif":
                time = digits(100, 300, primary);
                time.setTypeface(Typeface.create(Typeface.SERIF, 300, false));
                time.setFontFeatureSettings("lnum");
                add(time);
                date = label(19, 400, secondary);
                date.setTypeface(Typeface.create(Typeface.SERIF, 400, true));
                add(date, 0, 0);
                break;
            case "side": {
                setGravity(Gravity.START);
                int pad = dp(30 * k);
                setPadding(pad, 0, pad, 0);
                hours = digits(150, 300, primary);
                add(hours);
                minutes = digits(150, 300, secondary);
                add(minutes, -(int) (hours.getTextSize() * 0.36f), 0);
                date = label(17, 500, primary);
                date.setGravity(Gravity.START);
                add(date, 6, 0);
                break;
            }
            case "duo": {
                date = label(17, 500, primary);
                add(date, 0, 2);
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                row.setLayoutDirection(LAYOUT_DIRECTION_LTR);
                hours = digits(120, 700, primary);
                minutes = digits(120, 200, secondary);
                row.addView(hours);
                LayoutParams mlp = wrap();
                mlp.leftMargin = dp(10);
                row.addView(minutes, mlp);
                add(row);
                break;
            }
            case "analog": {
                analog = new AnalogClockView(getContext());
                analog.setColors(primary, secondary);
                int s = dp(250 * k);
                addView(analog, new LayoutParams(s, s));
                date = label(17, 500, primary);
                add(date, 14, 0);
                break;
            }
            default: { // stacked
                date = label(17, 500, primary);
                add(date, 0, 4);
                hours = digits(136, 600, primary);
                add(hours);
                minutes = digits(136, 200, secondary);
                add(minutes, -(int) (hours.getTextSize() * 0.36f), 0);
            }
        }
        update();
    }

    void update() {
        Calendar cal = Calendar.getInstance();
        boolean is24 = DateFormat.is24HourFormat(getContext());
        int h24 = cal.get(Calendar.HOUR_OF_DAY);
        int h = h24;
        if (!is24) {
            h %= 12;
            if (h == 0) h = 12;
        }
        int m = cal.get(Calendar.MINUTE);
        String hh = is24 ? String.format(Locale.US, "%02d", h) : String.valueOf(h);
        String mm = String.format(Locale.US, "%02d", m);
        if (hours != null) hours.setText(hh);
        if (minutes != null) minutes.setText(mm);
        if (time != null) time.setText(hh + ":" + mm);
        if (analog != null) analog.setTime(h24, m);
        if (date != null) {
            String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
            date.setText(DateFormat.format(pattern, cal));
        }
    }

    // ------------------------------------------------------------------ helpers

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private float dpf(float v) { return Ui.dpf(getContext(), v) * k; }

    private TextView label(float size, int weight, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size * k);
        t.setTypeface(Ui.font(weight));
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(dpf(8), 0f, dpf(1), 0x40000000);
        return t;
    }

    private TextView digits(float size, int weight, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size * k); // ignore font-scale so it never overflows
        t.setTypeface(Ui.font(weight));
        t.setFontFeatureSettings("tnum"); // fixed-width digits: the clock doesn't jiggle
        t.setIncludeFontPadding(false);
        t.setTextDirection(TEXT_DIRECTION_LTR);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(dpf(14), 0f, dpf(2), 0x33000000);
        return t;
    }

    private static LayoutParams wrap() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    private void add(View v) {
        addView(v, wrap());
    }

    private void add(View v, int topMarginPx, int bottomMarginDp) {
        LayoutParams lp = wrap();
        lp.topMargin = topMarginPx < 0 ? topMarginPx : dp(topMarginPx * k);
        lp.bottomMargin = dp(bottomMarginDp * k);
        addView(v, lp);
    }
}
