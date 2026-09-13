package com.roy.worktoscroll;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Three slow amber pulses over the app; no touch interception or brightness changes. */
final class PauseFlash extends View {
    private ValueAnimator animator;
    private final java.util.function.Consumer<String> events;

    PauseFlash(Context context, java.util.function.Consumer<String> events) {
        super(context);
        this.events = events;
        setBackgroundColor(0xFFFFC04D);
        setAlpha(0f);
        setVisibility(INVISIBLE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void start() {
        if (animator != null) return;
        setVisibility(VISIBLE);
        setAlpha(.08f);
        ValueAnimator next = ValueAnimator.ofFloat(.08f, .28f, 0f);
        animator = next;
        next.setInterpolator(new LinearInterpolator());
        next.setDuration(1_000);
        next.setRepeatCount(2);
        next.addUpdateListener(value -> setAlpha((float) value.getAnimatedValue()));
        next.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationRepeat(Animator animation) { events.accept("PULSE"); }
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != next) return;
                animator = null;
                setAlpha(0f);
                setVisibility(INVISIBLE);
                events.accept(cancelled ? "CANCELLED" : "COMPLETED");
            }
        });
        events.accept("STARTED");
        next.start();
    }

    void stop() { if (animator != null) animator.cancel(); }
    boolean running() { return animator != null; }
}
