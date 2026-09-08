package com.roy.worktoscroll;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
            clock.expire(SystemClock.elapsedRealtime());
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
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            column.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            return insets;
        });
        setContentView(scroll);
        label(column, "摄像头工作计时", 26);
        label(column, "首次累计 10 分钟得 5 分钟，此后每分钟得 30 秒", 16);
        stateText = label(column, "", 21);
        label(column, "本次有效时间", 16);
        validTime = label(column, "", 36);
        counters = label(column, "", 17);
        rewardFeedback = label(column, "", 15);
        rewardFeedback.setMinHeight(dp(54));
        rewardFeedback.setVisibility(View.INVISIBLE);
        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.BLACK);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        column.addView(preview, new LinearLayout.LayoutParams(-1, dp(240)));
        start = button(column, "开始工作", this::requestStart);
        end = button(column, "结束工作并保留奖励", this::endWork);
        button(column, "返回首页", () -> { pauseCamera("监测已暂停。"); finish(); });
        label(column, "把手机固定在桌面，让前置摄像头看到你的脸。\n"
            + "仅做在场观察，不能证明正在工作，也不识别身份或另一部手机。"
            + "低头、侧脸、暗光可能影响检测。\n"
            + "短暂未见人脸有 3 秒容错；持续离开暂停，重新在场约 1 秒后继续。\n"
            + "工作时自动保持亮屏；切出、锁屏或采集中断即暂停，返回后手动继续。\n"
            + "画面只在手机内处理，不保存录像、照片或上传画面。", 14);
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
        if (!ledger.saveWork(ledger.work.begin(System.currentTimeMillis()))) {
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
                if (delta > 0) {
                    WorkProgress before = ledger.work;
                    if (!ledger.saveWork(before.advance(delta))) {
                        pauseCamera("保存失败，已暂停；未确认的时间不会发放奖励。");
                        return;
                    }
                    showRewardFeedback(before, ledger.work);
                }
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
        message = reason;
        render();
    }

    private void endWork() {
        pauseCamera("本次工作已结束，奖励和不足下次兑换的余量已保留。");
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
        setTextIfChanged(stateText, status);
        WorkProgress work = ledger.work;
        setTextIfChanged(validTime, format(work.sessionValidMs()));
        setTextIfChanged(counters, "本次获得娱乐时间  " + format(work.sessionRewardMs())
            + "\n距下次奖励还需有效工作  " + ((work.nextRewardInMs() + 59_999) / 60_000) + " 分钟以内"
            + "\n可用娱乐余额  " + format(ledger.engine.balanceMs)
            + (ledger.workUpgradeCreditMs > 0 ? "\n升级补齐  " + format(ledger.workUpgradeCreditMs) + "（已计入余额）" : ""));
        setTextIfChanged(start, capturing ? "暂停监测" : work.active() ? "继续监测" : "开始工作");
        end.setEnabled(work.active());
    }

    private void showRewardFeedback(WorkProgress before, WorkProgress after) {
        long reward = after.sessionRewardMs() - before.sessionRewardMs();
        if (reward <= 0) return;
        String feedback = reward == WorkProgress.REWARD_NODE_MS ? "+5 分钟娱乐时间" : "+30 秒娱乐时间";
        if (after.totalValidMs() / WorkProgress.WORK_NODE_MS > before.totalValidMs() / WorkProgress.WORK_NODE_MS) {
            feedback = "累计有效工作 " + after.totalValidMs() / WorkProgress.MINUTE_MS
                + " 分钟 · 已挣得 " + format(WorkProgress.earnedFor(after.totalValidMs())) + " 娱乐时间";
        }
        rewardFeedback.animate().cancel();
        rewardFeedback.setText(feedback);
        rewardFeedback.setAlpha(1f);
        rewardFeedback.setVisibility(View.VISIBLE);
        rewardFeedback.animate().alpha(0f).setStartDelay(2200).setDuration(600)
            .withEndAction(() -> rewardFeedback.setVisibility(View.INVISIBLE)).start();
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
        view.setTextColor(size >= 19 ? Color.rgb(214, 221, 216) : Color.rgb(173, 184, 176));
        view.setPadding(0, dp(6), 0, dp(8));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private Button button(LinearLayout parent, String text, Runnable action) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(29, 35, 31)));
        view.setTextColor(new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {Color.rgb(110, 120, 113), Color.rgb(214, 221, 216)}));
        view.setOnClickListener(v -> action.run());
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
