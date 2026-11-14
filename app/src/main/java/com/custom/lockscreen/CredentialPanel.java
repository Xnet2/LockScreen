package com.custom.lockscreen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

/** Title + the right input (pattern / PIN pad / password) for one LockType. */
@SuppressLint("ViewConstructor")
public class CredentialPanel extends LinearLayout {

    final LockType type;
    Consumer<String> onSecret;
    Runnable onActivity;
    String defaultMessage;

    private final Ui.Palette pal;
    private final TextView title;
    private PinPadView pin;
    private PatternView pattern;
    private EditText pwd;
    private final InputMethodManager imm;

    public CredentialPanel(Context ctx, LockType type, boolean verifyMode, Ui.Palette pal) {
        super(ctx);
        this.type = type;
        this.pal = pal;
        imm = ctx.getSystemService(InputMethodManager.class);
        switch (type) {
            case PIN: defaultMessage = "הזן קוד"; break;
            case PATTERN: defaultMessage = "צייר את קו ביטול הנעילה"; break;
            case PASSWORD: defaultMessage = "הזן סיסמה"; break;
            default: defaultMessage = "";
        }

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_lock);
        icon.setImageTintList(ColorStateList.valueOf(pal.fg));
        LayoutParams ilp = new LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24));
        ilp.bottomMargin = Ui.dp(ctx, 14);
        addView(icon, ilp);

        title = new TextView(ctx);
        title.setTextColor(pal.fg);
        title.setTextSize(18f);
        title.setTypeface(Ui.font(450));
        title.setGravity(Gravity.CENTER);
        LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = Ui.dp(ctx, 24);
        addView(title, tlp);

        switch (type) {
            case PIN: {
                pin = new PinPadView(ctx, verifyMode ? Security.pinLength(ctx) : 0, pal);
                pin.onSubmit = this::emit;
                addView(pin, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
                break;
            }
            case PATTERN: {
                pattern = new PatternView(ctx, pal);
                pattern.onComplete = ids -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i : ids) sb.append(i);
                    emit(sb.toString());
                };
                addView(pattern, new LayoutParams(Ui.dp(ctx, 300), Ui.dp(ctx, 300)));
                break;
            }
            case PASSWORD: {
                pwd = new EditText(ctx);
                pwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                pwd.setImeOptions(EditorInfo.IME_ACTION_DONE
                        | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                        | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
                pwd.setMaxLines(1);
                pwd.setGravity(Gravity.CENTER);
                pwd.setTextSize(20f);
                pwd.setTextColor(pal.fg);
                pwd.setBackground(Ui.rounded(Ui.withAlpha(pal.fg, 0x1C), Ui.dpf(ctx, 27f),
                        Ui.dp(ctx, 1), Ui.withAlpha(pal.fg, 0x33)));
                pwd.setPadding(Ui.dp(ctx, 20), 0, Ui.dp(ctx, 20), 0);
                GradientDrawable cursor = Ui.rounded(pal.fg, Ui.dpf(ctx, 1f));
                cursor.setSize(Ui.dp(ctx, 2), Ui.dp(ctx, 24));
                pwd.setTextCursorDrawable(cursor);
                pwd.setOnEditorActionListener((v, actionId, ev) -> {
                    String t = pwd.getText().toString();
                    if (!t.isEmpty()) emit(t);
                    return true;
                });
                pwd.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void afterTextChanged(Editable s) {
                        if (onActivity != null) onActivity.run();
                    }
                });
                addView(pwd, new LayoutParams(Ui.dp(ctx, 280), Ui.dp(ctx, 54)));
                break;
            }
            default:
                break;
        }
        title.setText(defaultMessage);
    }

    private void emit(String s) {
        if (onSecret != null) onSecret.accept(s);
    }

    void setMessage(String msg, boolean error) {
        title.setText(msg);
        title.setTextColor(error ? pal.err : pal.fg);
    }

    void resetInput() {
        if (pin != null) pin.clear();
        if (pattern != null) pattern.clear();
        if (pwd != null) pwd.setText("");
    }

    void reset() {
        setMessage(defaultMessage, false);
        resetInput();
        setInputEnabled(true);
    }

    void fail(String msg) {
        setMessage(msg, true);
        Ui.shake(this);
        Ui.vibrate(getContext(), true);
        if (pin != null) pin.showError();
        if (pattern != null) {
            final PatternView p = pattern;
            p.setMode(PatternView.Mode.WRONG);
            p.inputEnabled = false;
            p.fadeOutAndClear(650, () -> p.inputEnabled = true);
        }
        if (pwd != null) pwd.setText("");
    }

    void markCorrect() {
        if (pattern != null) pattern.setMode(PatternView.Mode.CORRECT);
    }

    /** Pattern: show it as accepted for a beat, then fade it away. */
    void acceptAndClear(Runnable after) {
        if (pattern != null) {
            pattern.setMode(PatternView.Mode.CORRECT);
            pattern.fadeOutAndClear(250, after);
        } else {
            resetInput();
            if (after != null) after.run();
        }
    }

    void setInputEnabled(boolean b) {
        if (pin != null) pin.setInputEnabled(b);
        if (pattern != null) {
            pattern.inputEnabled = b;
            pattern.setAlpha(b ? 1f : 0.4f);
        }
        if (pwd != null) pwd.setEnabled(b);
    }

    void focusInput() {
        if (pwd == null) return;
        pwd.requestFocus();
        pwd.postDelayed(() -> { if (imm != null) imm.showSoftInput(pwd, 0); }, 60);
    }

    void hideKeyboard() {
        if (pwd == null) return;
        if (imm != null) imm.hideSoftInputFromWindow(pwd.getWindowToken(), 0);
        pwd.clearFocus();
    }
}
