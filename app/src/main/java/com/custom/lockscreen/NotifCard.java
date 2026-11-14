package com.custom.lockscreen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.function.Consumer;

/**
 * One app's notifications on the lock screen: a dark-glass card with the app icon,
 * name, time, title and text. Swipe sideways to dismiss, tap to open after unlocking.
 */
@SuppressLint("ViewConstructor")
public class NotifCard extends LinearLayout {

    NotifService.Group group;
    Consumer<NotifCard> onOpen, onDismiss;

    private final ImageView icon;
    private final TextView header, count, title, text;
    private final int slop;

    private float downX, downY;
    private boolean swiping, gone;
    private VelocityTracker vt;

    public NotifCard(Context ctx) {
        super(ctx);
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        setOrientation(HORIZONTAL);
        setGravity(Gravity.TOP);
        setPadding(dp(14), dp(13), dp(14), dp(14));
        setBackground(Ui.ripple(Ui.rounded(0x52101418, Ui.dpf(ctx, 24), dp(1), 0x1FFFFFFF), null, 0x26FFFFFF));
        setClickable(true);
        setOnClickListener(v -> { if (onOpen != null && !gone) onOpen.accept(this); });

        icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dpf(ctx, 10));
            }
        });
        icon.setClipToOutline(true);
        LayoutParams ilp = new LayoutParams(dp(36), dp(36));
        ilp.setMarginEnd(dp(12));
        ilp.topMargin = dp(2);
        addView(icon, ilp);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);
        addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        header = text(12.5f, 500, 0xB3FFFFFF, 1);
        top.addView(header, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        count = text(11f, 700, Color.WHITE, 1);
        count.setGravity(Gravity.CENTER);
        count.setMinWidth(dp(22));
        count.setPadding(dp(7), dp(1), dp(7), dp(1));
        count.setBackground(Ui.rounded(0x33FFFFFF, Ui.dpf(ctx, 10)));
        top.addView(count, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        col.addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        title = text(15f, 600, Color.WHITE, 1);
        LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(3);
        col.addView(title, tlp);
        text = text(14f, 400, 0xD9FFFFFF, 2);
        LayoutParams xlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        xlp.topMargin = dp(1);
        col.addView(text, xlp);
    }

    void bind(NotifService.Group g, boolean hideContent) {
        group = g;
        icon.setImageDrawable(g.icon);
        int n = g.items.size();
        count.setText(String.valueOf(n));
        count.setVisibility(n > 1 ? VISIBLE : GONE);
        if (hideContent) {
            title.setText(n > 1 ? n + " התראות חדשות" : "התראה חדשה");
            text.setVisibility(GONE);
        } else {
            title.setText(g.title != null ? g.title : g.app);
            title.setVisibility(VISIBLE);
            text.setText(g.text);
            text.setVisibility(g.text != null ? VISIBLE : GONE);
        }
        refreshTime();
    }

    void refreshTime() {
        if (group == null) return;
        header.setText(group.app + "  ·  " + ago(group.when));
    }

    private String ago(long when) {
        long d = System.currentTimeMillis() - when;
        if (d < 60_000) return "עכשיו";
        if (d < 3_600_000) return "לפני " + (d / 60_000) + " דק׳";
        Calendar now = Calendar.getInstance(), then = Calendar.getInstance();
        then.setTimeInMillis(when);
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
            return DateFormat.getTimeFormat(getContext()).format(then.getTime());
        }
        return DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "dMMM"), then).toString();
    }

    // ------------------------------------------------------------------ swipe to dismiss

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (gone) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                swiping = false;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return super.onTouchEvent(e) || true;
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                if (!swiping && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                    swiping = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.onTouchEvent(cancel); // drop the press / ripple
                    cancel.recycle();
                }
                if (swiping) {
                    setTranslationX(dx);
                    setAlpha(1f - Math.min(1f, Math.abs(dx) / Math.max(1f, getWidth())) * 0.85f);
                    return true;
                }
                return super.onTouchEvent(e);
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!swiping) {
                    if (vt != null) { vt.recycle(); vt = null; }
                    return super.onTouchEvent(e);
                }
                swiping = false;
                float vx = 0;
                if (vt != null) {
                    vt.addMovement(e);
                    vt.computeCurrentVelocity(1000);
                    vx = vt.getXVelocity();
                    vt.recycle();
                    vt = null;
                }
                float dx = getTranslationX();
                boolean fling = Math.abs(vx) > Ui.dpf(getContext(), 900) && Math.signum(vx) == Math.signum(dx);
                if (e.getActionMasked() == MotionEvent.ACTION_UP && (Math.abs(dx) > getWidth() * 0.35f || fling)) {
                    gone = true;
                    float to = Math.signum(dx == 0 ? vx : dx) * getWidth() * 1.1f;
                    animate().translationX(to).alpha(0f).setDuration(220).setInterpolator(Ui.EASE)
                            .withEndAction(() -> { if (onDismiss != null) onDismiss.accept(this); })
                            .start();
                } else {
                    animate().translationX(0f).alpha(1f).setDuration(280).setInterpolator(Ui.EASE).start();
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    // ------------------------------------------------------------------ helpers

    private int dp(float v) { return Ui.dp(getContext(), v); }

    private TextView text(float size, int weight, int color, int lines) {
        TextView t = new TextView(getContext());
        t.setTextSize(size);
        t.setTypeface(Ui.font(weight));
        t.setTextColor(color);
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        t.setIncludeFontPadding(true);
        return t;
    }
}
