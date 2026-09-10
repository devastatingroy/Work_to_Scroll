package com.roy.worktoscroll;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Native counterpart of the approved one-scene preview. Contains no accounting or audio. */
final class BlockerView extends ScrollView {
    private static final int PAPER = 0xfff6f0e5;
    private static final int RED = 0xffb72e2b;
    private final ImageView portrait;
    private final TextView headline;
    private final TextView fact;
    private final View line;
    private ValueAnimator animator;

    BlockerView(Context c, Runnable openApp, Runnable goHome, boolean exhausted) {
        super(c);
        setFillViewport(true);
        setClickable(true);
        setBackgroundColor(PAPER);
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(24), dp(20), dp(24), dp(20));
        column.setClickable(true);
        addView(column);

        TextView name = CatUi.text(c, "猫总监工", 16, CatUi.INK, true);
        name.setGravity(Gravity.START);
        CatUi.add(column, name, -2, 0);

        FrameLayout stage = new FrameLayout(c);
        stage.setClipChildren(true);
        int height = Math.max(180, Math.min(350, c.getResources().getConfiguration().screenHeightDp - 360));
        portrait = CatUi.image(c, R.drawable.cat_time_up);
        stage.addView(portrait, new FrameLayout.LayoutParams(-1, -1));
        View fade = new View(c);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x00f6f0e5, PAPER}));
        stage.addView(fade, new FrameLayout.LayoutParams(-1, dp(76), Gravity.BOTTOM));
        headline = CatUi.text(c, "", 56, RED, true);
        headline.setMaxLines(1);
        headline.setAutoSizeTextTypeUniformWithConfiguration(24, 56, 1, TypedValue.COMPLEX_UNIT_SP);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, dp(78), Gravity.BOTTOM);
        titleParams.bottomMargin = dp(8);
        stage.addView(headline, titleParams);
        line = new View(c);
        line.setBackgroundColor(RED);
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        stage.addView(line, new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM));
        CatUi.add(column, stage, height, 8);

        LinearLayout balance = new LinearLayout(c);
        balance.setGravity(Gravity.CENTER_VERTICAL);
        balance.setPadding(0, dp(14), 0, dp(14));
        TextView label = CatUi.text(c, "可用娱乐时间", 14, CatUi.MUTED, false);
        label.setGravity(Gravity.START);
        balance.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = CatUi.text(c, "00:00", 40, CatUi.INK, true);
        amount.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        balance.addView(amount, new LinearLayout.LayoutParams(-2, -2));
        CatUi.add(column, balance, -2, 8);
        fact = CatUi.text(c, "", 18, CatUi.INK, true);
        CatUi.add(column, fact, -2, 0);
        CatUi.add(column, CatUi.text(c, "工作获得时间后，即可继续娱乐。", 14, CatUi.MUTED, false), -2, 8);

        Button back = CatUi.button(c, c.getString(R.string.return_to_app), false, false, openApp);
        back.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33ffffff),
            CatUi.shape(c, CatUi.INK, 12, Color.TRANSPARENT), null));
        back.setTextColor(Color.WHITE);
        CatUi.add(column, back, -2, 22);
        CatUi.add(column, CatUi.button(c, "回到桌面", false, false, goHome), -2, 8);
        setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getSystemWindowInsets();
            if (android.os.Build.VERSION.SDK_INT >= 30)
                bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            setPadding(bars.left, bars.top, bars.right, bars.bottom);
            int available = c.getResources().getDisplayMetrics().widthPixels - bars.left - bars.right;
            int side = Math.max(dp(24), (available - dp(380)) / 2);
            column.setPadding(side, dp(20), side, dp(20));
            return insets;
        });
        showReason(exhausted);
    }

    private int dp(int n) { return CatUi.dp(getContext(), n); }

    void showReason(boolean exhausted) {
        stopAnimation();
        headline.setText(exhausted ? "时间到了！" : "暂无可用时间");
        fact.setText(exhausted ? "娱乐时间已用完" : "暂无可用娱乐时间");
    }

    void playExhaustion() {
        showReason(true);
        if (!isAttachedToWindow() || !hasWindowFocus() || !ValueAnimator.areAnimatorsEnabled()) return;
        animator = ValueAnimator.ofFloat(0, 1550);
        animator.setDuration(1550);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> pose((float) a.getAnimatedValue()));
        animator.start();
    }

    private static float ease(float n) {
        float x = Math.max(0, Math.min(1, n));
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private void pose(float t) {
        float entry = ease(t / 190), exit = ease((t - 1100) / 450);
        float scale = t < 190 ? 1.18f - .18f * entry : 1 - .17f * exit;
        headline.setScaleX(scale); headline.setScaleY(scale);
        headline.setTranslationY(t < 190 ? dp(35) * (1 - entry) : 0);
        headline.setAlpha(t < 190 ? .12f + .88f * entry : 1);
        portrait.setPivotY(portrait.getHeight() * .43f);
        portrait.setScaleX(1 + .075f * entry * (1 - exit));
        portrait.setScaleY(portrait.getScaleX());
        line.setPivotX(0); line.setScaleX(entry); line.setAlpha(entry * (1 - exit));
    }

    void stopAnimation() {
        if (animator != null) { animator.cancel(); animator.removeAllUpdateListeners(); animator = null; }
        pose(1550);
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }
}
