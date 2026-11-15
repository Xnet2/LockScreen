package com.custom.lockscreen;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Two modes:
 *  - set:    enter a new pattern / PIN / password twice to confirm it.
 *  - verify: enter the CURRENT one (extra "verify"); returns RESULT_OK when correct.
 *            Used to protect the settings, so nobody can turn the lock off or change it.
 */
public class SetupActivity extends Activity {

    static final String EXTRA_VERIFY = "verify";

    private LockType type;
    private boolean verify;
    private CredentialPanel panel;
    private String first;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep the PIN / pattern out of screenshots and the recent-apps preview.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        verify = getIntent().getBooleanExtra(EXTRA_VERIFY, false);
        try {
            type = verify ? Security.type(this) : LockType.valueOf(getIntent().getStringExtra("type"));
        } catch (Exception e) {
            type = LockType.NONE;
        }
        if (type == LockType.NONE) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        int bgTop = 0xFFE9EEFF, bgBottom = 0xFFF8F9FC;
        getWindow().setStatusBarColor(bgTop);
        getWindow().setNavigationBarColor(bgBottom);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bgTop, bgBottom}));

        panel = new CredentialPanel(this, type, verify, Ui.ON_LIGHT);
        panel.defaultMessage = verify ? verifyPrompt() : firstPrompt();
        panel.reset();
        panel.onSecret = verify ? this::onVerify : this::onEntered;
        root.addView(panel, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView cancel = new TextView(this);
        cancel.setText("ביטול");
        cancel.setTextSize(16f);
        cancel.setTypeface(Ui.font(500));
        cancel.setTextColor(Ui.L_ACCENT);
        cancel.setPadding(Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12));
        cancel.setBackground(Ui.ripple(null, Ui.rounded(Color.WHITE, Ui.dpf(this, 24)), 0x22000000));
        cancel.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = Ui.dp(this, 28);
        root.addView(cancel, clp);
        setContentView(root);
        if (getWindow().getInsetsController() != null) {
            int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(light, light);
        }

        if (type == LockType.PASSWORD) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            panel.focusInput();
        }
        if (verify && Security.lockoutRemainingMs(this) > 0) lockoutTick.run();
    }

    private String firstPrompt() {
        switch (type) {
            case PATTERN: return "צייר קו ביטול נעילה חדש";
            case PIN: return "בחר קוד (4 ספרות לפחות)";
            default: return "בחר סיסמה (4 תווים לפחות)";
        }
    }

    private String verifyPrompt() {
        switch (type) {
            case PATTERN: return "צייר את הקו כדי לפתוח את ההגדרות";
            case PIN: return "הזן את הקוד כדי לפתוח את ההגדרות";
            default: return "הזן את הסיסמה כדי לפתוח את ההגדרות";
        }
    }

    // ------------------------------------------------------------------ verify mode

    private boolean checking;

    private void onVerify(String s) {
        if (checking) return;
        if (Security.lockoutRemainingMs(this) > 0) {
            lockoutTick.run();
            return;
        }
        checking = true;
        panel.setBusy(true);
        Security.verifyAsync(this, s, ok -> {
            checking = false;
            if (isFinishing()) return;
            if (ok) {
                Security.onSuccess(this);
                setResult(RESULT_OK);
                finish();
                return;
            }
            panel.setBusy(false);
            Security.onFailure(this);
            panel.fail("שגוי, נסה שוב");
            if (Security.lockoutRemainingMs(this) > 0) lockoutTick.run();
        });
    }

    private final Runnable lockoutTick = new Runnable() {
        @Override public void run() {
            long rem = Security.lockoutRemainingMs(SetupActivity.this);
            if (rem <= 0) {
                panel.setInputEnabled(true);
                panel.setMessage(panel.defaultMessage, false);
                return;
            }
            panel.setInputEnabled(false);
            panel.setMessage("יותר מדי ניסיונות. נסה שוב בעוד " + ((rem + 999) / 1000) + " שניות", true);
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ set mode

    private void onEntered(String s) {
        if (first == null) {
            if (s.length() < 4) {
                panel.fail(type == LockType.PATTERN ? "חבר לפחות 4 נקודות" : "צריך לפחות 4 תווים");
                return;
            }
            first = s;
            panel.acceptAndClear(() -> panel.setMessage("שוב, לאישור", false));
        } else if (first.equals(s)) {
            panel.setBusy(true);
            Security.setAsync(this, type, s, () -> {
                Toast.makeText(this, "נשמר", Toast.LENGTH_SHORT).show();
                finish();
            });
        } else {
            first = null;
            panel.fail("לא תואם · מתחילים מחדש");
        }
    }
}
