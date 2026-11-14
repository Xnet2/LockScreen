package com.custom.lockscreen;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Enter a new pattern / PIN / password twice to confirm it. */
public class SetupActivity extends Activity {

    private LockType type;
    private CredentialPanel panel;
    private String first;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            type = LockType.valueOf(getIntent().getStringExtra("type"));
        } catch (Exception e) {
            type = LockType.NONE;
        }
        if (type == LockType.NONE) {
            finish();
            return;
        }
        int bgTop = 0xFFE9EEFF, bgBottom = 0xFFF8F9FC;
        getWindow().setStatusBarColor(bgTop);
        getWindow().setNavigationBarColor(bgBottom);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bgTop, bgBottom}));

        panel = new CredentialPanel(this, type, false, Ui.ON_LIGHT);
        panel.defaultMessage = firstPrompt();
        panel.reset();
        panel.onSecret = this::onEntered;
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
    }

    private String firstPrompt() {
        switch (type) {
            case PATTERN: return "צייר קו ביטול נעילה חדש";
            case PIN: return "בחר קוד (4 ספרות לפחות)";
            default: return "בחר סיסמה (4 תווים לפחות)";
        }
    }

    private void onEntered(String s) {
        if (first == null) {
            if (s.length() < 4) {
                panel.fail(type == LockType.PATTERN ? "חבר לפחות 4 נקודות" : "צריך לפחות 4 תווים");
                return;
            }
            first = s;
            panel.acceptAndClear(() -> panel.setMessage("שוב, לאישור", false));
        } else if (first.equals(s)) {
            Security.set(this, type, s);
            Toast.makeText(this, "נשמר", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            first = null;
            panel.fail("לא תואם · מתחילים מחדש");
        }
    }
}
