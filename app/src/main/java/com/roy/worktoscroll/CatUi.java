package com.roy.worktoscroll;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared native presentation helpers; no camera, protection or ledger writes. */
final class CatUi {
    static final int PAPER = Color.rgb(252, 247, 233);
    static final int INK = Color.rgb(39, 35, 31);
    static final int MUTED = Color.rgb(113, 102, 89);
    static final int RED = Color.rgb(187, 45, 28);
    static final int LIGHT = Color.rgb(220, 220, 214);
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    static LinearLayout screen(Activity a, boolean dark) {
        int bg = dark ? Color.BLACK : PAPER;
        a.getWindow().setStatusBarColor(bg);
        a.getWindow().setNavigationBarColor(bg);
        a.getWindow().getDecorView().setSystemUiVisibility(dark ? 0
            : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        FrameLayout frame = new FrameLayout(a);
        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int width = Math.min(a.getResources().getConfiguration().screenWidthDp, 480);
        frame.addView(column, new FrameLayout.LayoutParams(dp(a, width), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(frame);
        column.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 16));
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getSystemWindowInsets();
            if (android.os.Build.VERSION.SDK_INT >= 30) bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            frame.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            column.getLayoutParams().width = Math.min(dp(a, 480), a.getResources().getDisplayMetrics().widthPixels - bars.left - bars.right);
            column.requestLayout();
            return insets;
        });
        a.setContentView(scroll);
        return column;
    }

    static TextView text(Context c, String text, int sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        v.setGravity(Gravity.CENTER); v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return v;
    }
    static void add(LinearLayout parent, View child, int height, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(parent.getContext(), height));
        p.topMargin = dp(parent.getContext(), top); parent.addView(child, p);
    }
    static GradientDrawable shape(Context c, int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(c, 1), stroke);
        return d;
    }
    static Button button(Context c, String label, boolean primary, boolean dark, Runnable action) {
        Button v = new Button(c); v.setText(label); v.setTextSize(17); v.setAllCaps(false);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setMinHeight(dp(c, 52)); v.setMinimumHeight(dp(c, 52));
        v.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        int bg = primary ? RED : dark ? Color.rgb(34, 34, 32) : Color.rgb(247, 235, 216);
        int fg = primary ? Color.WHITE : dark ? LIGHT : INK;
        v.setBackgroundTintList(null);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227D5848), shape(c, bg, 28, Color.TRANSPARENT), null));
        v.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
            new int[]{dark ? 0xFF767671 : 0xFF998F82, fg}));
        v.setStateListAnimator(null); v.setOnClickListener(view -> action.run()); return v;
    }
    static void header(Activity a, LinearLayout column, String title, String left, Runnable back, String right, Runnable next, boolean dark) {
        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL);
        if (left != null) {
            Button b = button(a, left, false, dark, back); b.setBackgroundColor(Color.TRANSPARENT); b.setTextSize(15);
            row.addView(b, new LinearLayout.LayoutParams(dp(a, 76), dp(a, 52)));
        }
        TextView heading = text(a, title, left == null ? 26 : 20, dark ? LIGHT : RED, true);
        heading.setGravity(left == null ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        heading.setMaxLines(1);
        heading.setAutoSizeTextTypeUniformWithConfiguration(16, left == null ? 26 : 20, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        row.addView(heading, new LinearLayout.LayoutParams(0, dp(a, 52), 1));
        if (right != null) {
            Button b = button(a, right, false, dark, next); b.setBackgroundColor(Color.TRANSPARENT); b.setTextSize(15);
            b.setContentDescription(right); row.addView(b, new LinearLayout.LayoutParams(dp(a, 76), dp(a, 52)));
        }
        add(column, row, -2, 0);
    }
    static ImageView image(Context c, int resource) {
        ImageView v = new ImageView(c); v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageDrawable(imageDrawable(c, resource));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return v;
    }
    static Drawable imageDrawable(Context c, int resource) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
        return new BitmapDrawable(c.getResources(), BitmapFactory.decodeResource(c.getResources(), resource, options));
    }
    /** Same presentation for the immediate overlay and the opaque blocker activity. */
    static ScrollView blocker(Context c, Runnable openApp, Runnable goHome) {
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setClickable(true);
        scroll.setBackgroundColor(PAPER);
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setClickable(true);
        column.setPadding(dp(c, 24), dp(c, 28), dp(c, 24), dp(c, 28));
        scroll.addView(column);
        add(column, text(c, c.getString(R.string.app_name), 16, MUTED, true), -2, 0);
        int imageHeight = Math.max(120, Math.min(250, c.getResources().getConfiguration().screenHeightDp - 480));
        add(column, image(c, R.drawable.cat_home), imageHeight, 22);
        add(column, text(c, "娱乐时间已用完", 28, INK, true), -2, 20);
        add(column, text(c, "先工作，攒点时间。", 18, RED, true), -2, 12);
        TextView rule = text(c, "工作 10 分钟，可获得 5 分钟娱乐时间。", 15, MUTED, false);
        rule.setLineSpacing(dp(c, 3), 1);
        add(column, rule, -2, 12);
        add(column, button(c, c.getString(R.string.return_to_app), true, false, openApp), -2, 28);
        add(column, button(c, "回到桌面", false, false, goHome), -2, 12);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getSystemWindowInsets();
            if (android.os.Build.VERSION.SDK_INT >= 30)
                bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            int available = c.getResources().getDisplayMetrics().widthPixels - bars.left - bars.right;
            int side = Math.max(dp(c, 24), (available - dp(c, 432)) / 2);
            column.setPadding(side, dp(c, 28), side, dp(c, 28));
            return insets;
        });
        return scroll;
    }
    static void setText(TextView view, CharSequence value) {
        if (!value.toString().contentEquals(view.getText())) view.setText(value);
    }
    private CatUi() {}
}
