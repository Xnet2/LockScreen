package com.custom.lockscreen;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {

    private static final int REQ_PICK_IMAGE = 10;

    private LinearLayout root;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.L_BG);
        getWindow().setNavigationBarColor(Ui.L_BG);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.L_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(32));
        scroll.addView(root);
        setContentView(scroll);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        WindowInsetsController ic = getWindow().getInsetsController();
        if (ic != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            ic.setSystemBarsAppearance(light, light);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsVerify()) {
            // Settings are protected by the same pattern / PIN / password as the lock.
            root.removeAllViews();
            if (!verifying) {
                verifying = true;
                startActivityForResult(new Intent(this, SetupActivity.class)
                        .putExtra(SetupActivity.EXTRA_VERIFY, true), REQ_VERIFY);
            }
            return;
        }
        render();
    }

    private static final int REQ_VERIFY = 11;
    /** When the settings were last unlocked. Cleared whenever the phone locks. */
    static long verifiedAt;
    private boolean verifying;

    private boolean needsVerify() {
        if (Security.type(this) == LockType.NONE) return false;
        return android.os.SystemClock.elapsedRealtime() - verifiedAt > 5 * 60_000L || verifiedAt == 0;
    }

    /** Re-render after a change without losing the scroll position. */
    private void refresh() {
        int y = scroll.getScrollY();
        render();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    // ------------------------------------------------------------------ UI

    private void render() {
        root.removeAllViews();
        boolean overlayOk = Settings.canDrawOverlays(this);
        boolean active = Prefs.isEnabled(this) && overlayOk;

        root.addView(text("מסך נעילה", 30f, Ui.L_TEXT, 700));
        LinearLayout.LayoutParams slp = lp();
        slp.topMargin = dp(2);
        slp.bottomMargin = dp(22);
        root.addView(text(active ? "פעיל · יופיע בכל כיבוי של המסך" : "כבוי",
                15f, active ? Ui.L_ACCENT : Ui.L_SUB, 500), slp);

        // ---- main switch
        LinearLayout c = card(null);
        Switch sw = new Switch(this);
        sw.setChecked(Prefs.isEnabled(this));
        sw.setOnCheckedChangeListener((b, on) -> toggle(on));
        row(c, "הפעל את מסך הנעילה", "מחליף את מסך הנעילה של המערכת", sw, null);
        if (active) {
            LinearLayout.LayoutParams p = lp();
            p.topMargin = dp(10);
            c.addView(pill("נעל עכשיו לבדיקה", true, () -> LockService.lockNow(this)), p);
        }

        // ---- security
        c = card("אבטחה");
        boolean guard = LockGuardService.isEnabled(this);
        permRow(c, "הגנה מלאה (מומלץ מאוד)",
                "חוסם את מגש ההתראות, את ההגדרות המהירות ואת תפריט הכיבוי כשהטלפון נעול, "
                        + "וסוגר מיד כל אפליקציה שנפתחת מעל מסך הנעילה",
                guard, this::openGuardSettings);
        if (!guard) {
            LinearLayout.LayoutParams wp = lp();
            wp.topMargin = dp(4);
            c.addView(text("בלי הגנה מלאה אפשר למשוך את מגש ההתראות מעל מסך הנעילה. "
                    + "מפעילים בהגדרות ← נגישות ← \"מסך נעילה – הגנה מלאה\". "
                    + "אם ההגדרה חסומה: פרטי האפליקציה ← ⋮ ← \"התרת הגדרות מוגבלות\".", 12.5f, Ui.L_SUB, 400), wp);
        }
        LinearLayout.LayoutParams sp = lp();
        sp.topMargin = dp(8);
        c.addView(text("✓ ההגדרות מוגנות בקוד שלך   ✓ מוסתר מצילומי מסך\n"
                + "✓ הקוד שמור מוצפן (PBKDF2)   ✓ השהיה אחרי ניסיונות שגויים", 12.5f, Ui.L_SUB, 500), sp);

        // ---- clock design
        c = card("עיצוב השעון");
        clockPreview(c);
        grid(c, "סגנון", ClockFace.STYLES, Prefs.clockStyle(this), 4, false,
                k -> { Prefs.setClockStyle(this, k); refresh(); });
        grid(c, "צבע", ClockFace.COLORS, Prefs.clockColor(this), 4, true,
                k -> { Prefs.setClockColor(this, k); refresh(); });
        grid(c, "גודל", ClockFace.SIZES, Prefs.clockSize(this), 3, false,
                k -> { Prefs.setClockSize(this, k); refresh(); });
        grid(c, "מיקום", ClockFace.POSITIONS, Prefs.clockPos(this), 2, false,
                k -> { Prefs.setClockPos(this, k); refresh(); });
        grid(c, "רקע", new String[][]{{"system", "הרקע של דף הבית"}, {"custom", "תמונה משלי"}},
                Prefs.bgMode(this), 2, false, k -> {
                    if (k.equals("custom")) pickImage();
                    else {
                        Prefs.setBgMode(this, "system");
                        refresh();
                    }
                });

        // ---- notifications
        c = card("התראות");
        boolean notifOk = NotifService.isGranted(this);
        permRow(c, "גישה להתראות", "כדי להציג את ההתראות שלך במסך הנעילה", notifOk, this::openNotifAccess);
        if (!notifOk) {
            LinearLayout.LayoutParams hp = lp();
            hp.topMargin = dp(4);
            c.addView(text("אם ההגדרה חסומה (\"הגדרה מוגבלת\"): פתח את פרטי האפליקציה, לחץ על ⋮ למעלה "
                    + "ובחר \"התרת הגדרות מוגבלות\". אחר כך חזור לכאן ואפשר שוב.", 12.5f, Ui.L_SUB, 400), hp);
            LinearLayout.LayoutParams ap = lp();
            ap.topMargin = dp(10);
            c.addView(pill("פתח את פרטי האפליקציה", false, () -> startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))), ap);
        }
        grid(c, "תצוגה", new String[][]{{"show", "מלאה"}, {"private", "הסתר תוכן"}, {"off", "כבוי"}},
                Prefs.notifMode(this), 3, false, k -> { Prefs.setNotifMode(this, k); refresh(); });

        // ---- unlock method
        c = card("אמצעי ביטול נעילה");
        LockType cur = Security.type(this);
        Object[][] options = {
                {LockType.NONE, "ללא", "החלקה למעלה פותחת את הטלפון"},
                {LockType.PATTERN, "קו ביטול נעילה", "ציור תבנית על 9 נקודות"},
                {LockType.PIN, "קוד PIN", "ספרות בלבד"},
                {LockType.PASSWORD, "סיסמה", "אותיות, מספרים וסימנים"},
        };
        for (Object[] o : options) {
            LockType t = (LockType) o[0];
            boolean selected = t == cur;
            String sub = selected && t != LockType.NONE ? "מוגדר · הקש כדי לשנות" : (String) o[2];
            row(c, (String) o[1], sub, selected ? checkIcon() : null, () -> chooseMethod(t));
        }

        // ---- permissions
        c = card("הרשאות");
        permRow(c, "הצגה מעל אפליקציות אחרות", "חובה · כך מסך הנעילה מופיע מעל הכול", overlayOk,
                this::openOverlaySettings);
        permRow(c, "גישה לרקע של דף הבית", "כדי להציג את הרקע שלך מאחורי השעון",
                has(Manifest.permission.READ_EXTERNAL_STORAGE),
                () -> askPerm(Manifest.permission.READ_EXTERNAL_STORAGE));
        permRow(c, "זיהוי שיחות", "מסתיר את הנעילה בזמן שיחת טלפון",
                has(Manifest.permission.READ_PHONE_STATE),
                () -> askPerm(Manifest.permission.READ_PHONE_STATE));
        PowerManager pm = getSystemService(PowerManager.class);
        permRow(c, "ללא הגבלת סוללה", "שהמערכת לא תסגור את מסך הנעילה ברקע",
                pm.isIgnoringBatteryOptimizations(getPackageName()),
                () -> startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))));

        // ---- help
        c = card("הגדרה חד־פעמית");
        TextView steps = text("1. אשר את ההרשאות.\n"
                + "2. בחר אמצעי ביטול נעילה.\n"
                + "3. הפעל את המתג.\n"
                + "4. בטל את נעילת המערכת: הגדרות ← אבטחה ← נעילת מסך ← ללא.", 14f, Ui.L_TEXT, 400);
        steps.setLineSpacing(Ui.dpf(this, 4), 1f);
        c.addView(steps, lp());
        LinearLayout.LayoutParams bp = lp();
        bp.topMargin = dp(12);
        c.addView(pill("פתח הגדרות אבטחה", false, () -> {
            try {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }), bp);
        LinearLayout.LayoutParams np = lp();
        np.topMargin = dp(14);
        c.addView(text("שים לב: נעילה של אפליקציה מגינה מפני גישה מזדמנת, אבל היא פחות חזקה מנעילת המערכת "
                + "(למשל, הפעלה מחדש במצב בטוח עוקפת אותה).", 12.5f, Ui.L_SUB, 400), np);
    }

    /** A small phone-shaped preview of the lock screen with the chosen clock. */
    private void clockPreview(LinearLayout parent) {
        FrameLayout phone = new FrameLayout(this);
        phone.setBackground(Ui.rounded(0xFF1C2647, Ui.dpf(this, 26)));
        phone.setClipToOutline(true);
        phone.setElevation(Ui.dpf(this, 6));

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap b = WallpaperLoader.load(this);
        if (b != null) bg.setImageBitmap(b);
        else bg.setImageDrawable(WallpaperLoader.fallback());
        phone.addView(bg, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        View scrim = new View(this);
        scrim.setBackgroundColor(0x1A000000);
        phone.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        float scale = 0.42f;
        ClockFace face = new ClockFace(this);
        int[] col = ClockFace.colors(this);
        face.configure(Prefs.clockStyle(this), col[0], col[1], ClockFace.sizeFactor(this) * scale);
        boolean center = "center".equals(Prefs.clockPos(this));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, center ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        if (center) flp.bottomMargin = dp(28);
        else flp.topMargin = dp(40);
        phone.addView(face, flp);

        // A real (or sample) notification card, drawn at full size and scaled down like the clock.
        if (!"off".equals(Prefs.notifMode(this))) {
            NotifService.Group g;
            java.util.List<NotifService.Group> real = NotifService.groups();
            if (!real.isEmpty()) {
                g = real.get(0);
            } else {
                g = new NotifService.Group();
                g.app = "הודעות";
                g.icon = getApplicationInfo().loadIcon(getPackageManager());
                g.title = "דנה";
                g.text = "נפגשים ב־8 בקפה?";
                g.when = System.currentTimeMillis() - 4 * 60_000;
                g.items.add(null);
                g.items.add(null);
            }
            NotifCard card = new NotifCard(this);
            card.bind(g, "private".equals(Prefs.notifMode(this)));
            card.setClickable(false);
            card.setEnabled(false);
            int fullW = dp(176 / scale - 24);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(fullW,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            clp.bottomMargin = dp(48);
            card.setScaleX(scale);
            card.setScaleY(scale);
            card.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
                v.setPivotX(v.getWidth() / 2f);
                v.setPivotY(v.getHeight());
            });
            phone.addView(card, clp);
        }

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(176), dp(352));
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.topMargin = dp(8);
        plp.bottomMargin = dp(10);
        parent.addView(phone, plp);
    }

    private void openGuardSettings() {
        Toast.makeText(this, "בחר \"מסך נעילה – הגנה מלאה\" והפעל", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openNotifAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            new android.content.ComponentName(this, NotifService.class).flattenToString()));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    private void openOverlaySettings() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private void toggle(boolean on) {
        if (on) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "קודם צריך לאשר \"הצגה מעל אפליקציות אחרות\"", Toast.LENGTH_LONG).show();
                openOverlaySettings();
                root.post(this::refresh);
                return;
            }
            Prefs.setEnabled(this, true);
            LockService.start(this);
        } else {
            Prefs.setEnabled(this, false);
            Prefs.setLocked(this, false);
            LockService.stop(this);
        }
        root.post(this::refresh);
    }

    private void chooseMethod(LockType t) {
        if (t == LockType.NONE) {
            if (Security.type(this) == LockType.NONE) return;
            new AlertDialog.Builder(this)
                    .setTitle("להסיר את אמצעי הנעילה?")
                    .setMessage("החלקה למעלה תפתח את הטלפון בלי קוד.")
                    .setPositiveButton("הסר", (d, w) -> {
                        Security.set(this, LockType.NONE, null);
                        refresh();
                    })
                    .setNegativeButton("ביטול", null)
                    .show();
        } else {
            startActivity(new Intent(this, SetupActivity.class).putExtra("type", t.name()));
        }
    }

    // ------------------------------------------------------------------ permissions

    private boolean has(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPerm(String p) {
        if (Prefs.wasAsked(this, p) && !shouldShowRequestPermissionRationale(p)) {
            // Denied permanently: the only way left is the app's settings page.
            Toast.makeText(this, "אפשר את ההרשאה בהגדרות האפליקציה", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } else {
            Prefs.setAsked(this, p);
            requestPermissions(new String[]{p}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    // ------------------------------------------------------------------ custom background

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        startActivityForResult(i, REQ_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VERIFY) {
            verifying = false;
            if (resultCode == RESULT_OK) {
                verifiedAt = android.os.SystemClock.elapsedRealtime();
            } else {
                finish(); // cancelled / wrong: no access to the settings
            }
            return;
        }
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData());
             OutputStream out = new FileOutputStream(WallpaperLoader.customFile(this))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            Prefs.setBgMode(this, "custom");
            Toast.makeText(this, "הרקע עודכן", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "לא הצלחתי לטעון את התמונה", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    // ------------------------------------------------------------------ view helpers

    private int dp(float v) { return Ui.dp(this, v); }

    private LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String s, float size, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Ui.font(weight));
        return t;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rounded(Ui.L_CARD, Ui.dpf(this, 22), dp(1), Ui.L_LINE));
        c.setPadding(dp(18), dp(14), dp(18), dp(16));
        if (title != null) {
            LinearLayout.LayoutParams p = lp();
            p.bottomMargin = dp(4);
            c.addView(text(title, 13f, Ui.L_ACCENT, 650), p);
        }
        LinearLayout.LayoutParams p = lp();
        p.bottomMargin = dp(14);
        root.addView(c, p);
        return c;
    }

    private void row(LinearLayout parent, String title, String sub, View trailing, Runnable onClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(58));
        r.setPadding(dp(4), dp(8), dp(4), dp(8));
        if (onClick != null) {
            r.setOnClickListener(v -> onClick.run());
            r.setBackground(Ui.ripple(null, Ui.rounded(Color.WHITE, Ui.dpf(this, 12)), 0x14000000));
        }
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16f, Ui.L_TEXT, 500));
        if (sub != null) texts.addView(text(sub, 13f, Ui.L_SUB, 400));
        r.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) r.addView(trailing);
        parent.addView(r, lp());
    }

    private void permRow(LinearLayout parent, String title, String desc, boolean granted, Runnable action) {
        if (granted) row(parent, title, desc, checkIcon(), null);
        else row(parent, title, desc, pill("אפשר", true, action), action);
    }

    /** A titled grid of selectable pills, `cols` per row. */
    private void grid(LinearLayout parent, String title, String[][] options, String current, int cols,
                      boolean swatches, Consumer<String> onPick) {
        LinearLayout.LayoutParams tp = lp();
        tp.topMargin = dp(12);
        tp.bottomMargin = dp(8);
        parent.addView(text(title, 13.5f, Ui.L_SUB, 600), tp);
        LinearLayout line = null;
        for (int i = 0; i < options.length; i++) {
            if (i % cols == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams llp = lp();
                if (i > 0) llp.topMargin = dp(8);
                parent.addView(line, llp);
            }
            String key = options[i][0];
            TextView p = pill(options[i][1], key.equals(current), () -> onPick.accept(key));
            p.setTextSize(13f);
            p.setPadding(dp(6), dp(10), dp(6), dp(10));
            if (swatches) {
                GradientDrawable sw = key.equals("auto")
                        ? new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                                new int[]{0xFF8AB4FF, 0xFFFFB8D9, 0xFFFFD98A})
                        : new GradientDrawable();
                sw.setShape(GradientDrawable.OVAL);
                if (!key.equals("auto")) sw.setColor(ClockFace.presetColor(key));
                sw.setStroke(dp(1), 0x33000000);
                sw.setSize(dp(12), dp(12));
                p.setCompoundDrawablesRelativeWithIntrinsicBounds(sw, null, null, null);
                p.setCompoundDrawablePadding(dp(6));
            }
            line.addView(p, cell(i % cols == cols - 1));
        }
        // pad the last row so all cells keep the same width
        int rest = (cols - options.length % cols) % cols;
        for (int i = 0; i < rest && line != null; i++) line.addView(new View(this), cell(i == rest - 1));
    }

    private LinearLayout.LayoutParams cell(boolean last) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (!last) p.setMarginEnd(dp(8));
        return p;
    }

    private TextView pill(String label, boolean primary, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14f);
        t.setTypeface(Ui.font(600));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setTextColor(primary ? Color.WHITE : Ui.L_TEXT);
        t.setBackground(Ui.ripple(Ui.rounded(primary ? Ui.L_ACCENT : 0xFFEEF0F5, Ui.dpf(this, 22)), null,
                primary ? 0x40FFFFFF : 0x22000000));
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private ImageView checkIcon() {
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_check);
        iv.setImageTintList(ColorStateList.valueOf(Ui.L_ACCENT));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
        return iv;
    }
}
