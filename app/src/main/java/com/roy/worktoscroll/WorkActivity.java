package com.roy.worktoscroll;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Size;
import android.view.View;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit foreground camera session. Preview pixels never leave the in-memory pipeline. */
public final class WorkActivity extends ComponentActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService analyzer = Executors.newSingleThreadExecutor();
    private final WorkClock clock = new WorkClock();
    private LedgerStore ledger;
    private FaceDetector detector;
    private ProcessCameraProvider provider;
    private PreviewView preview;
    private TextView stateText;
    private TextView counters;
    private TextView validTime;
    private TextView rewardFeedback;
    private TextView previewHint;
    private ImageView mascot;
    private final android.util.SparseArray<Bitmap> catImages = new android.util.SparseArray<>();
    private int mascotResource;
    private long rewardUntilElapsed;
    private Button start;
    private Button end;
    private boolean resumed;
    private boolean capturing;
    private int generation;
    private long lastAnalysisElapsed;
    private String message = "点击开始后授权摄像头，并调整机位。";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (capturing && !available()) pauseCamera("监测已暂停，回到工作页后点击继续。");
            long now = SystemClock.elapsedRealtime();
            long staleAt = clock.expire(now);
            if (ledger.work.active()) {
                WorkProgress base = staleAt >= 0 ? ledger.work.pause(staleAt, ledger.bootCount) : ledger.work;
                WorkProgress next = capturing && (clock.state() == WorkClock.State.PRESENT || clock.state() == WorkClock.State.GRACE)
                    ? base.refresh(now, ledger.bootCount) : base.pause(now, ledger.bootCount);
                if (next != ledger.work && !ledger.saveWork(next)) {
                    pauseCamera("进度保存失败，监测已暂停，请重试。");
                }
            }
            render();
            handler.postDelayed(this, 250);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ledger = LedgerStore.get(this);
        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f).build());
        LinearLayout column = CatUi.screen(this, true);
        CatUi.header(this, column, "猫总监工", "‹ 返回", () -> { pauseCamera("监测已暂停。"); finish(); }, "说明", this::showHelp, true);
        mascot = new ImageView(this);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mascot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        CatUi.add(column, mascot, 132, 8);
        label(column, "本轮有效工作", 16);
        validTime = CatUi.text(this, "00:00", 62, CatUi.LIGHT, true);
        validTime.setMaxLines(1);
        validTime.setAutoSizeTextTypeUniformWithConfiguration(30, 62, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        CatUi.add(column, validTime, 84, 0);
        stateText = label(column, "", 15);
        stateText.setMinHeight(dp(48));
        stateText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        counters = label(column, "", 17);
        rewardFeedback = label(column, "", 15);
        rewardFeedback.setTextColor(0xFFFFC1A6);
        rewardFeedback.setMinHeight(dp(48));
        rewardFeedback.setVisibility(View.INVISIBLE);
        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.BLACK);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackground(CatUi.shape(this, 0xFF151513, 14, Color.TRANSPARENT));
        previewFrame.setClipToOutline(true);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        previewHint = CatUi.text(this, "摄像头已关闭", 14, 0xFFB6B6AD, false);
        previewFrame.addView(previewHint, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(210), dp(130));
        previewParams.gravity = Gravity.CENTER_HORIZONTAL;
        previewParams.topMargin = dp(4);
        column.addView(previewFrame, previewParams);
        LinearLayout actions = new LinearLayout(this);
        start = CatUi.button(this, "开始监测", false, true, this::requestStart);
        end = CatUi.button(this, "结束工作", false, true, this::endWork);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, -2, 1);
        startParams.rightMargin = dp(10);
        actions.addView(start, startParams);
        actions.addView(end, new LinearLayout.LayoutParams(0, -2, 1));
        CatUi.add(column, actions, -2, 20);
        label(column, "退出页面会暂停 · 已获时间保留", 12);
        if (ledger.work.active()) message = "上次工作已暂停，点击继续后重新确认在场。";
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { pauseCamera("监测已暂停。"); finish(); }
        });
        render();
    }

    private boolean available() {
        return resumed && hasWindowFocus()
            && getSystemService(PowerManager.class).isInteractive()
            && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private void requestStart() {
        if (capturing) { pauseCamera("已暂停，已获得时间保留。"); return; }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 20);
            return;
        }
        startCamera();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 20) return;
        message = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
            ? "摄像头已授权，点击开始/继续监测。"
            : "摄像头未授权，未开始计时；可在系统应用设置中恢复权限。";
        render();
    }

    private void startCamera() {
        if (!available()) return;
        if (!ledger.saveWork(ledger.work.begin(System.currentTimeMillis(), SystemClock.elapsedRealtime(), ledger.bootCount))) {
            message = "保存失败，尚未开始计时，请稍后重试。";
            render();
            return;
        }
        ProtectionService service = ProtectionService.current();
        if (service != null) service.prepareManualChange();
        else ledger.engine.resetObservation(System.currentTimeMillis());
        clock.interrupt();
        capturing = true;
        message = "正在启动前置摄像头…";
        preview.setVisibility(View.VISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int token = ++generation;
        var future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (!capturing || token != generation || !available()) return;
            try {
                provider = future.get();
                if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    pauseCamera("未找到可用前置摄像头，未累计时间。");
                    return;
                }
                Preview cameraPreview = new Preview.Builder().build();
                cameraPreview.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(analyzer, frame -> analyze(frame, token));
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, cameraPreview, analysis);
                message = "等待新画面，请调整机位。";
            } catch (Exception ex) {
                pauseCamera("摄像头启动失败或被占用，请关闭其他相机应用后重试。");
            }
            render();
        }, ContextCompat.getMainExecutor(this));
        render();
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyze(ImageProxy frame, int token) {
        long capturedAt = SystemClock.elapsedRealtime();
        if (capturedAt - lastAnalysisElapsed < 400 || frame.getImage() == null) {
            frame.close();
            return;
        }
        lastAnalysisElapsed = capturedAt;
        try {
            InputImage input = InputImage.fromMediaImage(frame.getImage(), frame.getImageInfo().getRotationDegrees());
            detector.process(input).addOnCompleteListener(ContextCompat.getMainExecutor(this), task -> {
                frame.close();
                if (!capturing || token != generation || !available()) return;
                if (!task.isSuccessful()) {
                    pauseCamera("图像检测中断，点击继续重试。");
                    return;
                }
                if (SystemClock.elapsedRealtime() - capturedAt > WorkClock.FRESH_MS) {
                    clock.interrupt();
                    message = "画面处理延迟，已暂停计时。";
                    render();
                    return;
                }
                long delta = clock.observe(capturedAt, task.getResult().size());
                WorkProgress before = ledger.work;
                boolean counting = clock.state() == WorkClock.State.PRESENT || clock.state() == WorkClock.State.GRACE;
                WorkProgress next = before.observe(delta, counting, SystemClock.elapsedRealtime(), ledger.bootCount);
                if (next != before && !ledger.saveWork(next)) {
                    pauseCamera("保存失败，已暂停；未确认的时间不会发放奖励。");
                    return;
                }
                showRewardFeedback(before, ledger.work);
                render();
            });
        } catch (RuntimeException ex) {
            frame.close();
            handler.post(() -> {
                if (capturing && token == generation) pauseCamera("图像检测异常，点击继续重试。");
            });
        }
    }

    private void pauseCamera(String reason) {
        capturing = false;
        generation++;
        clock.interrupt();
        if (provider != null) provider.unbindAll();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preview.setVisibility(View.INVISIBLE);
        rewardFeedback.animate().cancel();
        rewardFeedback.setVisibility(View.INVISIBLE);
        rewardUntilElapsed = 0;
        WorkProgress next = ledger.work.pause(SystemClock.elapsedRealtime(), ledger.bootCount);
        message = next != ledger.work && !ledger.saveWork(next)
            ? "中断状态保存失败，监测已暂停，请重试。" : reason;
        render();
    }

    private void endWork() {
        pauseCamera("本次工作已结束，已到账奖励保留；下次重新累计 10 分钟。");
        if (!ledger.saveWork(ledger.work.finish())) message = "结束状态保存失败，计时已暂停，请重试结束。";
        render();
    }

    private void render() {
        if (stateText == null) return;
        String status = message;
        if (capturing) {
            switch (clock.state()) {
                case PRESENT: status = "观察到在场 · 正在累计"; break;
                case GRACE: status = "暂未见人脸 · 短暂容错中"; break;
                case AWAY: status = "持续未见人脸 · 计时暂停"; break;
                case CHECKING: status = "正在确认在场 · 尚未计时"; break;
                case UNCERTAIN: status = "画面状态不确定 · 计时暂停"; break;
                case PAUSED: status = "等待有效新画面 · 计时暂停"; break;
            }
        }
        WorkProgress work = ledger.work;
        long remaining = work.resetRemainingMs(SystemClock.elapsedRealtime());
        if (remaining == 0) status += "\n中断已满 3 分钟，本轮重置；已到账奖励保留";
        else if (remaining > 0 && work.sessionValidMs() > 0)
            status += "\n还有 " + format(remaining + 999) + "，本轮进度将重置";
        setTextIfChanged(stateText, status);
        setTextIfChanged(validTime, format(work.sessionValidMs()));
        setTextIfChanged(counters, "本轮已获得 " + MainActivity.duration(WorkProgress.earnedFor(work.sessionValidMs()), false) + " 娱乐");
        setTextIfChanged(start, capturing ? "暂停" : work.active() ? "继续监测" : "开始监测");
        end.setEnabled(work.active());
        previewHint.setVisibility(capturing ? View.GONE : View.VISIBLE);
        int pose = capturing && clock.state() == WorkClock.State.AWAY ? R.drawable.cat_away
            : capturing && SystemClock.elapsedRealtime() < rewardUntilElapsed ? R.drawable.cat_reward : R.drawable.cat_work;
        setMascot(pose);
    }

    private void showRewardFeedback(WorkProgress before, WorkProgress after) {
        long reward = after.sessionRewardMs() - before.sessionRewardMs();
        if (reward <= 0) return;
        String feedback = reward == WorkProgress.REWARD_NODE_MS ? "+5 分钟娱乐时间" : "+30 秒娱乐时间";
        if (after.sessionValidMs() / WorkProgress.WORK_NODE_MS > before.sessionValidMs() / WorkProgress.WORK_NODE_MS) {
            feedback = "本轮有效工作 " + after.sessionValidMs() / WorkProgress.MINUTE_MS
                + " 分钟 · 本轮已挣得 " + format(WorkProgress.earnedFor(after.sessionValidMs())) + " 娱乐时间";
        }
        rewardFeedback.animate().cancel();
        rewardFeedback.setText("准玩 · " + feedback);
        rewardUntilElapsed = SystemClock.elapsedRealtime() + 2800;
        rewardFeedback.setAlpha(1f);
        rewardFeedback.setVisibility(View.VISIBLE);
        rewardFeedback.animate().alpha(0f).setStartDelay(2200).setDuration(600)
            .withEndAction(() -> rewardFeedback.setVisibility(View.INVISIBLE)).start();
    }

    private void setMascot(int resource) {
        if (mascotResource == resource) return;
        Bitmap bitmap = catImages.get(resource);
        if (bitmap == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            bitmap = BitmapFactory.decodeResource(getResources(), resource, options);
            catImages.put(resource, bitmap);
        }
        mascot.setImageBitmap(bitmap);
        mascotResource = resource;
    }

    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("让猫总看到你")
            .setMessage("固定手机，让前置摄像头看到你的脸，点击开始监测。\n\n只观察是否在场，不证明正在工作。低头、侧脸或暗光可能影响检测；持续未见人脸时暂停。\n\n工作时保持亮屏；切出、锁屏、打开说明或采集中断会暂停，返回后手动继续。\n\n每轮先累计 10 分钟得 5 分钟娱乐，之后每分钟得 30 秒。连续离开或暂停不足 3 分钟可续接；达到 3 分钟重置本轮进度，重新过 10 分钟门槛。已到账奖励和历史工作保留，主动结束后新一轮从零开始。\n\n画面仅在本机处理，不保存、不上传。")
            .setPositiveButton("知道了", null).show();
    }

    private static void setTextIfChanged(TextView view, String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }

    private static String format(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(size >= 19 ? Color.rgb(214, 221, 216) : Color.rgb(173, 184, 176));
        view.setPadding(0, dp(6), 0, dp(8));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.post(tick);
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus && capturing) pauseCamera("工作页已离开前台，点击继续后重新确认在场。");
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        pauseCamera("监测已暂停，已获得时间保留；点击继续恢复。");
        super.onPause();
    }
    @Override protected void onDestroy() {
        detector.close();
        analyzer.shutdown();
        super.onDestroy();
    }
}
