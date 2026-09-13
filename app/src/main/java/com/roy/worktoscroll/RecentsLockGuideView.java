package com.roy.worktoscroll;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Xiaomi-only illustrated instructions. It reads no task or permission state. */
final class RecentsLockGuideView extends LinearLayout {
    private static final int CARD = 0xFFFFFDF8;
    private static final int BORDER = 0xFFE8E0D1;

    RecentsLockGuideView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        int edge = CatUi.dp(context, 4);
        setPadding(edge, 0, edge, CatUi.dp(context, 8));
        addStep(1, "打开最近任务", "从屏幕底部向上滑并停住，直到看到所有当前应用的卡片。",
            "示意图：从屏幕底部向上滑动并停住，打开最近任务");
        addStep(2, "找到并长按卡片", "找到 WorkToScroll，按住卡片中间约 1 秒，不要上滑清除。",
            "示意图：找到 WorkToScroll 卡片并长按");
        addStep(3, "点击小锁", "点击锁形图标；回到卡片列表后，标题旁出现小锁才算完成。",
            "示意图：点击锁形图标并确认卡片标题旁出现小锁");
        TextView note = text("不同小米系统版本的图标位置可能略有差异。应用无法自动确认是否已锁定。", 13, CatUi.MUTED, false);
        note.setLineSpacing(CatUi.dp(context, 3), 1);
        LayoutParams noteParams = new LayoutParams(-1, -2);
        noteParams.topMargin = CatUi.dp(context, 12);
        addView(note, noteParams);
    }

    private void addStep(int step, String title, String detail, String description) {
        Context context = getContext();
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(VERTICAL);
        block.setPadding(CatUi.dp(context, 14), CatUi.dp(context, 12), CatUi.dp(context, 14), CatUi.dp(context, 12));
        block.setBackground(CatUi.shape(context, CARD, 16, BORDER));

        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text(String.valueOf(step), 14, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(CatUi.shape(context, CatUi.RED, 16, Color.TRANSPARENT));
        heading.addView(badge, new LayoutParams(CatUi.dp(context, 30), CatUi.dp(context, 30)));
        TextView label = text(title, 16, CatUi.INK, true);
        LayoutParams labelParams = new LayoutParams(0, -2, 1);
        labelParams.leftMargin = CatUi.dp(context, 10);
        heading.addView(label, labelParams);
        block.addView(heading, new LayoutParams(-1, -2));

        TextView body = text(detail, 14, CatUi.MUTED, false);
        body.setLineSpacing(CatUi.dp(context, 3), 1);
        LayoutParams bodyParams = new LayoutParams(-1, -2);
        bodyParams.topMargin = CatUi.dp(context, 8);
        block.addView(body, bodyParams);

        Illustration illustration = new Illustration(context, step);
        illustration.setContentDescription(description);
        LayoutParams imageParams = new LayoutParams(-1, CatUi.dp(context, 112));
        imageParams.topMargin = CatUi.dp(context, 10);
        block.addView(illustration, imageParams);

        LayoutParams blockParams = new LayoutParams(-1, -2);
        blockParams.topMargin = CatUi.dp(context, step == 1 ? 4 : 12);
        addView(block, blockParams);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = CatUi.text(getContext(), value, size, color, bold);
        view.setGravity(Gravity.START);
        return view;
    }

    private static final class Illustration extends View {
        private final int step;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Illustration(Context context, int step) {
            super(context);
            this.step = step;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFF5ECDD);
            canvas.drawRoundRect(0, 0, w, h, dp(12), dp(12), paint);
            if (step == 1) drawSwipe(canvas, w, h);
            else if (step == 2) drawLongPress(canvas, w, h);
            else drawLocked(canvas, w, h);
        }

        private void drawSwipe(Canvas canvas, float w, float h) {
            float phoneLeft = w * .26f, phoneRight = w * .74f;
            outline(canvas, new RectF(phoneLeft, dp(8), phoneRight, h - dp(8)), dp(12));
            miniCard(canvas, w * .34f, dp(26), w * .47f, h - dp(30), 0xFFE5D9C5);
            miniCard(canvas, w * .44f, dp(20), w * .57f, h - dp(24), 0xFFFFFFFF);
            miniCard(canvas, w * .54f, dp(26), w * .67f, h - dp(30), 0xFFD7E2D4);
            paint.setColor(CatUi.INK); paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(w * .43f, h - dp(15), w * .57f, h - dp(15), paint);
            arrow(canvas, w * .5f, h - dp(14), w * .5f, dp(39), CatUi.RED);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0x33BB2D1C);
            canvas.drawCircle(w * .5f, h - dp(13), dp(12), paint);
        }

        private void drawLongPress(Canvas canvas, float w, float h) {
            float left = w * .19f, right = w * .81f, top = dp(15), bottom = h - dp(15);
            taskCard(canvas, left, top, right, bottom, false);
            float x = w * .63f, y = h * .58f;
            paint.setStyle(Paint.Style.FILL); paint.setColor(0x55BB2D1C);
            canvas.drawCircle(x, y, dp(18), paint);
            paint.setColor(CatUi.RED); canvas.drawCircle(x, y, dp(8), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
            canvas.drawCircle(x, y, dp(25), paint);
        }

        private void drawLocked(Canvas canvas, float w, float h) {
            float left = w * .12f, right = w * .70f, top = dp(15), bottom = h - dp(15);
            taskCard(canvas, left, top, right, bottom, true);
            float cx = w * .84f, cy = h * .52f;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, dp(27), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(BORDER);
            canvas.drawCircle(cx, cy, dp(27), paint);
            drawLock(canvas, cx, cy, CatUi.RED, dp(1.8f));
            arrow(canvas, right + dp(5), h * .45f, cx - dp(29), cy, CatUi.RED);
        }

        private void taskCard(Canvas canvas, float left, float top, float right, float bottom, boolean locked) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            canvas.drawRoundRect(left, top, right, bottom, dp(10), dp(10), paint);
            paint.setColor(0xFF6D3BE8);
            canvas.drawRoundRect(left + dp(10), top + dp(9), left + dp(28), top + dp(27), dp(5), dp(5), paint);
            paint.setColor(CatUi.INK); paint.setTextSize(dp(12)); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            canvas.drawText("WorkToScroll", left + dp(35), top + dp(23), paint);
            if (locked) drawLock(canvas, right - dp(16), top + dp(18), CatUi.MUTED, dp(1.2f));
            paint.setColor(0xFFF8F1E5);
            canvas.drawRoundRect(left + dp(10), top + dp(36), right - dp(10), bottom - dp(10), dp(7), dp(7), paint);
        }

        private void miniCard(Canvas canvas, float left, float top, float right, float bottom, int color) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(color);
            canvas.drawRoundRect(left, top, right, bottom, dp(5), dp(5), paint);
        }

        private void outline(Canvas canvas, RectF rect, float radius) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(CatUi.MUTED);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        private void drawLock(Canvas canvas, float cx, float cy, int color, float stroke) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(color);
            RectF shackle = new RectF(cx - dp(7), cy - dp(10), cx + dp(7), cy + dp(5));
            canvas.drawArc(shackle, 180, -180, false, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(cx - dp(10), cy - dp(2), cx + dp(10), cy + dp(11), dp(3), dp(3), paint);
        }

        private void arrow(Canvas canvas, float fromX, float fromY, float toX, float toY, int color) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2.2f)); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(color);
            canvas.drawLine(fromX, fromY, toX, toY, paint);
            float angle = (float) Math.atan2(toY - fromY, toX - fromX);
            float wing = dp(8);
            path.reset(); path.moveTo(toX, toY);
            path.lineTo(toX - wing * (float)Math.cos(angle - .55f), toY - wing * (float)Math.sin(angle - .55f));
            path.moveTo(toX, toY);
            path.lineTo(toX - wing * (float)Math.cos(angle + .55f), toY - wing * (float)Math.sin(angle + .55f));
            canvas.drawPath(path, paint);
        }

        private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    }
}
