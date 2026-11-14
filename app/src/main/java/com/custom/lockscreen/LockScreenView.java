package com.custom.lockscreen;

import android.animation.ObjectAnimator;
import android.animation.TimeAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The lock screen itself. progress 0 = clock page, 1 = unlock page.
 * Swipe up opens the unlock page, swipe down returns to the clock. All motion is
 * driven by a damped spring that starts with the finger's velocity, so the
 * movement continues smoothly from the gesture instead of jumping.
 */
@SuppressLint("ViewConstructor")
public class LockScreenView extends FrameLayout {

    private static final int STATE_CLOCK = 0;
    private static final int STATE_UNLOCK = 1;
    private static final long IDLE_MS = 30_000L;

    private final Runnable onUnlock;

    private final ImageView bgSharp, bgBlur;
    private final View scrim;
    private final ClockFace clock;
    private final LinearLayout bottomLayer;
    private final TextView chargeView, hintView;
    private final ImageView chevron;
    private final FrameLayout unlockLayer;
    private final LinearLayout backHint;
    private CredentialPanel panel;

    private int state = STATE_CLOCK;
    private float progress = 0f;
    private ObjectAnimator chevronAnim;
    private int insetTop, insetBottom, imeBottom;
    private boolean clockCentered;

    // gesture
    private final int touchSlop;
    private VelocityTracker velocity;
    private boolean dragging;
    private int dragOrigin;
    private float downX, downY, touchStartY, startProgress;

    // spring
    private TimeAnimator spring;
    private float springV, springTarget;
    private Runnable springEnd;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable idleRunnable = () -> { if (state == STATE_UNLOCK) closeUnlock(0f); };
    private final Runnable lockoutTick = new Runnable() {
        @Override public void run() {
            CredentialPanel p = panel;
            if (p == null) return;
            long rem = Security.lockoutRemainingMs(getContext());
            if (rem <= 0) {
                p.setInputEnabled(true);
                p.setMessage(p.defaultMessage, false);
                return;
            }
            p.setInputEnabled(false);
            p.setMessage("יותר מדי ניסיונות. נסה שוב בעוד " + ((rem + 999) / 1000) + " שניות", true);
            handler.postDelayed(this, 500);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(a)
                    || Intent.ACTION_POWER_CONNECTED.equals(a)
                    || Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
                updateBattery();
            } else {
                clock.update();
                for (NotifCard card : cards.values()) card.refreshTime();
            }
        }
    };

    // ---- notifications
    private final LinearLayout notifList;
    private final java.util.LinkedHashMap<String, NotifCard> cards = new java.util.LinkedHashMap<>();
    private android.app.PendingIntent pendingIntent; // notification to open after unlock
    private String pendingKey;

    public LockScreenView(Context ctx, Runnable onUnlock) {
        super(ctx);
        this.onUnlock = onUnlock;
        touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(Color.BLACK);

        bgSharp = new ImageView(ctx);
        bgSharp.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgBlur = new ImageView(ctx);
        bgBlur.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgBlur.setAlpha(0f);
        scrim = new View(ctx);
        scrim.setBackgroundColor(Color.BLACK);
        addView(bgSharp, matchLp());
        addView(bgBlur, matchLp());
        addView(scrim, matchLp());

        View topShade = new View(ctx);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x50000000, 0x00000000}));
        addView(topShade, new LayoutParams(LayoutParams.MATCH_PARENT, dp(260), Gravity.TOP));
        View bottomShade = new View(ctx);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x50000000, 0x00000000}));
        addView(bottomShade, new LayoutParams(LayoutParams.MATCH_PARENT, dp(200), Gravity.BOTTOM));

        // ---- clock
        clock = new ClockFace(ctx);
        addView(clock, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // ---- bottom hint
        bottomLayer = new LinearLayout(ctx);
        bottomLayer.setOrientation(LinearLayout.VERTICAL);
        bottomLayer.setGravity(Gravity.CENTER_HORIZONTAL);
        chargeView = label(13f, 500);
        chevron = icon(R.drawable.ic_chevron_up);
        hintView = label(13f, 400);
        hintView.setAlpha(0.85f);
        LinearLayout.LayoutParams clp = wrapLp();
        clp.bottomMargin = dp(14);
        bottomLayer.addView(chargeView, clp);
        bottomLayer.addView(chevron, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout.LayoutParams hlp = wrapLp();
        hlp.topMargin = dp(2);
        bottomLayer.addView(hintView, hlp);
        addView(bottomLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // ---- notifications (stacked above the hint, growing upwards)
        notifList = new LinearLayout(ctx);
        notifList.setOrientation(LinearLayout.VERTICAL);
        android.animation.LayoutTransition lt = new android.animation.LayoutTransition();
        lt.setDuration(260);
        lt.setInterpolator(android.animation.LayoutTransition.CHANGE_APPEARING, Ui.EASE);
        lt.setInterpolator(android.animation.LayoutTransition.CHANGE_DISAPPEARING, Ui.EASE);
        notifList.setLayoutTransition(lt);
        LayoutParams nlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        nlp.leftMargin = nlp.rightMargin = dp(12);
        addView(notifList, nlp);

        // ---- unlock page
        unlockLayer = new FrameLayout(ctx);
        unlockLayer.setVisibility(INVISIBLE);
        backHint = new LinearLayout(ctx);
        backHint.setOrientation(LinearLayout.VERTICAL);
        backHint.setGravity(Gravity.CENTER_HORIZONTAL);
        backHint.setAlpha(0.7f);
        TextView backText = label(12.5f, 400);
        backText.setText("החלק למטה לחזרה");
        backHint.addView(backText, wrapLp());
        ImageView down = icon(R.drawable.ic_chevron_up);
        down.setRotation(180f);
        backHint.addView(down, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LayoutParams blp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(12);
        backHint.setVisibility(GONE); // swipe-down still works; the hint was just clutter
        addView(unlockLayer, matchLp());

        setOnApplyWindowInsetsListener((v, insets) -> {
            Insets sys = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            insetTop = sys.top;
            insetBottom = sys.bottom;
            imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
            layoutChrome();
            return insets;
        });

        reset();
    }

    // ---------------------------------------------------------------- public API

    /** Back to the clock page with fresh time / wallpaper / settings. Called on every lock. */
    void reset() {
        stopSpring();
        handler.removeCallbacksAndMessages(null);
        if (panel != null) {
            panel.hideKeyboard();
            unlockLayer.removeView(panel);
        }
        panel = null; // rebuilt on open, so a changed PIN/method is always picked up
        state = STATE_CLOCK;
        dragging = false;
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
        setTranslationY(0f);
        hintView.setText(Security.type(getContext()) == LockType.NONE
                ? "החלק למעלה לפתיחה" : "החלק למעלה לביטול הנעילה");
        loadWallpaper();
        applyClock();
        setProgress(0f);
        updateBattery();
        pendingIntent = null;
        pendingKey = null;
        updateNotifications();
    }

    void onScreenOn() {
        clock.update();
        updateBattery();
        startChevron();
    }

    // ---------------------------------------------------------------- layout

    private void applyClock() {
        Context c = getContext();
        int[] col = ClockFace.colors(c);
        clock.configure(Prefs.clockStyle(c), col[0], col[1], ClockFace.sizeFactor(c));
        clockCentered = "center".equals(Prefs.clockPos(c));
        LayoutParams lp = (LayoutParams) clock.getLayoutParams();
        lp.gravity = clockCentered ? Gravity.CENTER_VERTICAL : Gravity.TOP;
        clock.setLayoutParams(lp);
        layoutChrome();
    }

    private void layoutChrome() {
        int h = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        int cl = clock.getPaddingLeft(), cr = clock.getPaddingRight();
        if (clockCentered) clock.setPadding(cl, 0, cr, (int) (h * 0.08f));
        else clock.setPadding(cl, insetTop + (int) (h * 0.09f), cr, 0);
        bottomLayer.setPadding(0, 0, 0, insetBottom + dp(24));
        unlockLayer.setPadding(0, insetTop, 0, Math.max(insetBottom, imeBottom));
        LayoutParams nlp = (LayoutParams) notifList.getLayoutParams();
        int nb = insetBottom + dp(112);
        if (nlp.bottomMargin != nb) {
            nlp.bottomMargin = nb;
            notifList.setLayoutParams(nlp);
        }
        post(this::fitNotifications);
    }

    // ---------------------------------------------------------------- notifications

    /** Sync the cards with the current notifications, animating only what changed. */
    private void updateNotifications() {
        String mode = Prefs.notifMode(getContext());
        java.util.List<NotifService.Group> groups =
                "off".equals(mode) ? java.util.Collections.emptyList() : NotifService.groups();
        boolean hide = "private".equals(mode);

        java.util.HashSet<String> live = new java.util.HashSet<>();
        for (NotifService.Group g : groups) live.add(g.pkg);
        java.util.Iterator<java.util.Map.Entry<String, NotifCard>> it = cards.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, NotifCard> e = it.next();
            if (!live.contains(e.getKey())) {
                notifList.removeView(e.getValue());
                it.remove();
            }
        }
        for (int i = 0; i < groups.size(); i++) {
            NotifService.Group g = groups.get(i);
            NotifCard card = cards.get(g.pkg);
            if (card == null) {
                card = new NotifCard(getContext());
                card.onOpen = this::openFromNotification;
                card.onDismiss = c -> {
                    NotifService.dismiss(c.group);
                    notifList.removeView(c);
                    cards.remove(c.group.pkg);
                };
                cards.put(g.pkg, card);
            }
            card.bind(g, hide);
            // keep newest on top
            int at = notifList.indexOfChild(card);
            if (at != i) {
                if (at >= 0) notifList.removeView(card);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(8);
                notifList.addView(card, Math.min(i, notifList.getChildCount()), lp);
            }
        }
        post(this::fitNotifications);
    }

    /** Show only as many cards as fit between the clock and the bottom hint. */
    private void fitNotifications() {
        int w = notifList.getWidth();
        if (w <= 0 || getHeight() <= 0) return;
        int clockBottom = clock.getTop() + clock.getHeight();
        int avail = (getHeight() - ((LayoutParams) notifList.getLayoutParams()).bottomMargin) - clockBottom - dp(20);
        int used = 0;
        boolean full = false;
        int spec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        for (int i = 0; i < notifList.getChildCount(); i++) {
            View c = notifList.getChildAt(i);
            c.measure(spec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int h = c.getMeasuredHeight() + dp(8);
            boolean fits = !full && used + h <= avail;
            if (fits) used += h;
            else full = true;
            int vis = fits ? VISIBLE : GONE;
            if (c.getVisibility() != vis) c.setVisibility(vis);
        }
    }

    private void openFromNotification(NotifCard card) {
        NotifService.Group g = card.group;
        pendingIntent = g.intent;
        pendingKey = g.autoCancel ? g.latestKey : null;
        if (Security.type(getContext()) == LockType.NONE) {
            launchPending();
            onUnlock.run();
        } else {
            openUnlock(0f);
            if (panel != null && Security.lockoutRemainingMs(getContext()) <= 0) {
                panel.setMessage("בטל את הנעילה כדי לפתוח את ההתראה", false);
            }
        }
    }

    private void launchPending() {
        android.app.PendingIntent pi = pendingIntent;
        pendingIntent = null;
        if (pi != null) {
            try {
                pi.send();
            } catch (Exception ignored) {
            }
        }
        if (pendingKey != null) NotifService.cancel(pendingKey);
        pendingKey = null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(() -> {
            layoutChrome();
            setProgress(progress);
        });
    }

    private void loadWallpaper() {
        Bitmap bmp = WallpaperLoader.load(getContext());
        if (bmp != null) {
            bgSharp.setImageBitmap(bmp);
            bgBlur.setImageBitmap(WallpaperLoader.blurred(bmp));
        } else {
            bgSharp.setImageDrawable(WallpaperLoader.fallback());
            bgBlur.setImageDrawable(null);
        }
    }

    private void updateBattery() {
        Intent i;
        try {
            i = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception e) {
            i = null;
        }
        if (i == null) return;
        boolean plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = Math.max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int pct = level * 100 / scale;
        if (plugged) {
            chargeView.setVisibility(VISIBLE);
            chargeView.setText(pct >= 100 ? "טעון במלואו" : "בטעינה · " + pct + "%");
        } else {
            chargeView.setVisibility(GONE);
        }
    }

    private void startChevron() {
        if (chevronAnim != null) chevronAnim.cancel();
        chevron.setTranslationY(0f);
        chevronAnim = ObjectAnimator.ofFloat(chevron, View.TRANSLATION_Y, 0f, -dpf(6));
        chevronAnim.setDuration(1100);
        chevronAnim.setRepeatMode(ValueAnimator.REVERSE);
        chevronAnim.setRepeatCount(ValueAnimator.INFINITE);
        chevronAnim.setInterpolator(new PathInterpolator(0.45f, 0f, 0.55f, 1f));
        chevronAnim.start();
    }

    // ---------------------------------------------------------------- progress → visuals

    private void setProgress(float p) {
        progress = p;
        float h = Math.max(1f, getHeight());

        // Clock drifts up, shrinks a touch and fades — eased, so it starts gently.
        float cOut = Ui.smooth(0.05f, 0.7f, p);
        clock.setTranslationY(-h * 0.16f * p);
        clock.setAlpha(1f - cOut);
        float cs = 1f - 0.08f * p;
        clock.setScaleX(cs);
        clock.setScaleY(cs);

        bottomLayer.setAlpha(1f - Ui.smooth(0f, 0.35f, p));
        bottomLayer.setTranslationY(-h * 0.06f * p);
        notifList.setAlpha(1f - Ui.smooth(0f, 0.5f, p));
        notifList.setTranslationY(-h * 0.12f * p);
        notifList.setVisibility(p < 0.6f ? VISIBLE : INVISIBLE);

        // Wallpaper zooms slightly and melts into the blurred copy.
        float zoom = 1f + 0.07f * p;
        bgSharp.setScaleX(zoom);
        bgSharp.setScaleY(zoom);
        bgBlur.setScaleX(zoom);
        bgBlur.setScaleY(zoom);
        bgBlur.setAlpha(Ui.smooth(0f, 0.85f, p));
        scrim.setAlpha(0.08f + 0.38f * Ui.smooth(0f, 1f, p));

        // Unlock input rises in from below and scales up softly.
        float u = Ui.smooth(0.4f, 1f, p);
        unlockLayer.setAlpha(u);
        unlockLayer.setTranslationY((1f - u) * dpf(90));
        float us = 0.94f + 0.06f * u;
        unlockLayer.setScaleX(us);
        unlockLayer.setScaleY(us);
        unlockLayer.setVisibility(p > 0.01f ? VISIBLE : INVISIBLE);
    }

    // ---------------------------------------------------------------- spring

    /**
     * Animate progress to target with a critically-damped spring, starting from
     * velocity v (progress units / second) so a fling carries on naturally.
     */
    private void springTo(float target, float v, Runnable end) {
        stopSpring();
        springTarget = target;
        springV = v;
        springEnd = end;
        final long[] elapsed = {0};
        spring = new TimeAnimator();
        spring.setTimeListener((anim, total, deltaMs) -> {
            if (deltaMs <= 0) return;
            elapsed[0] += deltaMs;
            float dt = Math.min(deltaMs, 34) / 1000f;
            final float stiffness = 190f;
            final float damping = 2f * (float) Math.sqrt(stiffness) * 0.92f;
            float x = progress;
            int steps = 4;
            for (int i = 0; i < steps; i++) {
                float hdt = dt / steps;
                float acc = -stiffness * (x - springTarget) - damping * springV;
                springV += acc * hdt;
                x += springV * hdt;
            }
            boolean done = (Math.abs(x - springTarget) < 0.0015f && Math.abs(springV) < 0.02f)
                    || elapsed[0] > 2500;
            if (done) {
                setProgress(springTarget);
                Runnable e = springEnd;
                stopSpring();
                if (e != null) e.run();
            } else {
                setProgress(Ui.clamp(x, -0.02f, 1.02f));
            }
        });
        spring.start();
    }

    private void stopSpring() {
        if (spring != null) {
            spring.setTimeListener(null);
            spring.cancel();
            spring = null;
        }
        springEnd = null;
    }

    // ---------------------------------------------------------------- touch

    private float dragRange() {
        return Math.max(1f, getHeight() * 0.55f); // a long, deliberate swipe
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (state == STATE_CLOCK) {
            // Over a notification card: let the card get taps / sideways swipes,
            // but still take over a vertical swipe for unlocking.
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartY = ev.getRawY();
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    return !overNotifications(ev);
                case MotionEvent.ACTION_MOVE: {
                    float dy = ev.getRawY() - downY, dx = ev.getRawX() - downX;
                    if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        beginDrag(ev);
                        return true;
                    }
                    return false;
                }
            }
            return false;
        }
        // Unlock page: steal the gesture only for a clear downward swipe.
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                return false;
            case MotionEvent.ACTION_MOVE: {
                float dy = ev.getRawY() - downY, dx = ev.getRawX() - downX;
                if (dy > touchSlop && dy > Math.abs(dx) * 1.2f) {
                    beginDrag(ev);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private boolean overNotifications(MotionEvent ev) {
        if (notifList.getVisibility() != VISIBLE || notifList.getChildCount() == 0 || progress > 0.05f) return false;
        float x = ev.getX(), y = ev.getY();
        float top = notifList.getTop() + notifList.getTranslationY();
        for (int i = 0; i < notifList.getChildCount(); i++) {
            View c = notifList.getChildAt(i);
            if (c.getVisibility() != VISIBLE) continue;
            float t = top + c.getTop(), b = top + c.getBottom();
            if (y >= t && y <= b && x >= notifList.getLeft() && x <= notifList.getRight()) return true;
        }
        return false;
    }

    private void beginDrag(MotionEvent ev) {
        stopSpring();
        dragging = true;
        dragOrigin = state;
        downY = ev.getRawY();
        startProgress = progress;
        if (velocity != null) velocity.recycle();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(ev);
        if (dragOrigin == STATE_UNLOCK && panel != null) panel.hideKeyboard();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = ev.getRawY();
                downX = ev.getRawX();
                downY = ev.getRawY();
                if (state == STATE_CLOCK) beginDrag(ev);
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (!dragging) {
                    // touch on an empty part of the unlock page
                    float dy = ev.getRawY() - downY, dx = ev.getRawX() - downX;
                    if (state == STATE_UNLOCK && dy > touchSlop && dy > Math.abs(dx) * 1.2f) beginDrag(ev);
                    else return true;
                }
                velocity.addMovement(ev);
                float up = downY - ev.getRawY();
                setProgress(Ui.clamp(startProgress + up / dragRange(), 0f, 1f));
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return true;
                dragging = false;
                velocity.addMovement(ev);
                velocity.computeCurrentVelocity(1000);
                float v = -velocity.getYVelocity() / dragRange(); // progress per second, + = up
                velocity.recycle();
                velocity = null;
                boolean released = ev.getActionMasked() == MotionEvent.ACTION_UP;
                float projected = progress + v * 0.2f; // where the fling would carry it

                if (dragOrigin == STATE_CLOCK) {
                    boolean tap = Math.abs(ev.getRawY() - touchStartY) < dpf(8) && progress < 0.03f;
                    if (released && tap) {
                        springTo(0f, 3f, null); // a soft "lift me" nudge
                    } else if (released && (progress > 0.6f || (progress > 0.4f && projected > 0.8f))) {
                        // only a real, long swipe opens it; a short flick slides back
                        openUnlock(v);
                    } else {
                        springTo(0f, v, null);
                    }
                } else {
                    if (!released || projected < 0.75f) closeUnlock(v);
                    else springTo(1f, v, this::onUnlockShown);
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (state == STATE_UNLOCK) scheduleIdle();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && state == STATE_UNLOCK) closeUnlock(0f);
            return true; // back never dismisses the lock
        }
        return super.dispatchKeyEvent(event);
    }

    // ---------------------------------------------------------------- unlock flow

    private void openUnlock(float v) {
        LockType type = Security.type(getContext());
        if (type == LockType.NONE) {
            onUnlock.run();
            return;
        }
        if (panel == null) {
            CredentialPanel np = new CredentialPanel(getContext(), type, true, Ui.ON_DARK);
            np.onSecret = this::check;
            np.onActivity = this::scheduleIdle;
            unlockLayer.addView(np, 0,
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            panel = np;
        }
        if (Security.lockoutRemainingMs(getContext()) > 0) startLockoutTicker();
        springTo(1f, v, this::onUnlockShown);
    }

    private void onUnlockShown() {
        state = STATE_UNLOCK;
        scheduleIdle();
        if (panel != null) panel.focusInput();
    }

    private void closeUnlock(float v) {
        handler.removeCallbacks(idleRunnable);
        if (panel != null) panel.hideKeyboard();
        state = STATE_CLOCK;
        pendingIntent = null;
        pendingKey = null;
        springTo(0f, v, () -> {
            if (panel != null) {
                panel.resetInput();
                if (Security.lockoutRemainingMs(getContext()) <= 0) panel.setMessage(panel.defaultMessage, false);
            }
        });
    }

    private void check(String secret) {
        CredentialPanel p = panel;
        if (p == null) return;
        Context c = getContext();
        if (Security.lockoutRemainingMs(c) > 0) {
            startLockoutTicker();
            return;
        }
        if (Security.verify(c, secret)) {
            Security.onSuccess(c);
            p.hideKeyboard();
            handler.removeCallbacksAndMessages(null);
            launchPending();
            onUnlock.run();
            return;
        }
        Security.onFailure(c);
        p.fail(p.type == LockType.PIN ? "קוד שגוי"
                : p.type == LockType.PATTERN ? "קו שגוי, נסה שוב" : "סיסמה שגויה");
        if (Security.lockoutRemainingMs(c) > 0) startLockoutTicker();
    }

    private void startLockoutTicker() {
        handler.removeCallbacks(lockoutTick);
        lockoutTick.run();
    }

    private void scheduleIdle() {
        handler.removeCallbacks(idleRunnable);
        handler.postDelayed(idleRunnable, IDLE_MS);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        try {
            getContext().registerReceiver(receiver, f);
        } catch (Exception ignored) {
        }
        startChevron();
        requestFocus();
        clock.update();
        NotifService.setListener(this::updateNotifications);
        updateNotifications();
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            getContext().unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        if (chevronAnim != null) chevronAnim.cancel();
        stopSpring();
        handler.removeCallbacksAndMessages(null);
        NotifService.setListener(null);
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- helpers

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private float dpf(float v) { return Ui.dpf(getContext(), v); }

    private TextView label(float size, int weight) {
        TextView t = new TextView(getContext());
        t.setTextSize(size);
        t.setTypeface(Ui.font(weight));
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(dpf(8), 0f, dpf(1), 0x40000000);
        return t;
    }

    private ImageView icon(int res) {
        ImageView iv = new ImageView(getContext());
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        iv.setAlpha(0.85f);
        return iv;
    }

    private static LayoutParams matchLp() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
