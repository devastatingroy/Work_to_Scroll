package com.roy.worktoscroll;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.hardware.camera2.CaptureRequest;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.AtomicFile;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Size;
import android.util.Range;
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
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.components.containers.Category;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.json.JSONObject;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit foreground camera session. Preview pixels never leave the in-memory pipeline. */
public final class WorkActivity extends ComponentActivity {
    static final String EXTRA_RESUME_SHORT_EXTERNAL_RETURN = "resume_short_external_return";
    private static final Size THERMAL_STREAM_SIZE = new Size(320, 240);
    private static final Range<Integer> THERMAL_FRAME_RATE = new Range<>(10, 10);
    private static final long ANALYSIS_INTERVAL_MS = 1_000;
    private static final float MONITORING_SCREEN_BRIGHTNESS = 0.05f;
    public static final class RecoveryModel extends ViewModel {
        final CameraRecovery recovery = new CameraRecovery();
        final PreviewTrial previewTrial = new PreviewTrial();
    }
    private RecoveryModel recoveryModel;
    private CameraRecovery recovery;
    private final long createdAtElapsed = SystemClock.elapsedRealtime();
    private androidx.camera.core.Camera boundCamera;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService analyzer = Executors.newSingleThreadExecutor();
    private final WorkClock clock = new WorkClock();
    private LedgerStore ledger;
    private WorkDebugLog workDebugLog;
    private ObjectDetector detector;
    private boolean modelReady, destroyed;
    private final DetectionCue detectionCue = new DetectionCue();
    private final UnrecordedReminder unrecordedReminder = new UnrecordedReminder();
    private CuePlayer cuePlayer;
    private boolean soundsEnabled;
    private Button soundButton;
    private Button previewButton;
    private long sampledAt = -1, processingMs, sequence, lastDiagnosticWrite;
    private PersonObservation observation = new PersonObservation(-1, -1, 0);
    private final String cameraSession = UUID.randomUUID().toString();
    private ProcessCameraProvider provider;
    private Preview cameraPreview;
    private boolean trialScreenDimmed;
    private PreviewView preview;
    private TextView stateText;
    private TextView counters;
    private TextView rewardRule;
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
    private volatile boolean capturing;
    private volatile int generation;
    private long lastAnalysisElapsed;
    private int analysisWidth, analysisHeight;
    private String previewResolution = "unbound", capturePolicy = "unbound";
    private String message = "正在准备人体检测模型…";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (capturing && !available()) suspendCamera("tick_" + unavailableReason());
            long now = SystemClock.elapsedRealtime();
            if (recovery.wanted() && !modelReady && now - createdAtElapsed >= 15_000)
                failCamera("人体模型准备超时，请退出工作页后重试。");
            long staleAt = clock.expire(now);
            long weakHoldExpiredAt = staleAt >= 0 ? -1 : clock.expireWeakHold(now);
            if (staleAt >= 0) { detectionCue.reset(); cuePlayer.stop("stale_frame"); }
            if (ledger.work.active()) {
                WorkProgress base = staleAt >= 0 ? ledger.work.pause(staleAt, ledger.bootCount)
                    : weakHoldExpiredAt >= 0 ? ledger.work.pause(weakHoldExpiredAt, ledger.bootCount) : ledger.work;
                WorkProgress next = capturing && clock.counting()
                    ? base.refresh(now, ledger.bootCount) : base.pause(now, ledger.bootCount);
                if (next != ledger.work && !saveWorkWithDebug(next, "tick_refresh")) {
                    failCamera("进度保存失败，监测已暂停，请重试。");
                }
            }
            if (ledger.work.active()) workDebugLog.recordClockState(clock.state().name(), capturing,
                observation.candidates, observation.reliable, observation.score, ledger.work);
            maybePlayUnrecordedReminder(now);
            if (available() && recovery.timedOut(now)) retryCamera("持续未收到有效画面，请检查相机后重试。");
            maybeResumeCamera();
            render();
            writeDiagnostic(false);
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        recoveryModel = new ViewModelProvider(this).get(RecoveryModel.class);
        recovery = recoveryModel.recovery;
        ledger = LedgerStore.get(this);
        workDebugLog = new WorkDebugLog(this);
        if (getIntent().getBooleanExtra(EXTRA_RESUME_SHORT_EXTERNAL_RETURN, false) && ledger.work.active()) {
            recovery.request();
            message = "正在恢复监测，等待工作页可用。";
        }
        cuePlayer = new CuePlayer(this, (event, cue, detail) -> {
            if (ledger != null && workDebugLog != null)
                workDebugLog.recordAudioEvent(event, cue, detail, ledger.work);
        });
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        soundsEnabled = getSharedPreferences("ui_preferences", MODE_PRIVATE).getBoolean("presence_sound_enabled", true);
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
        rewardRule = label(column, "", 13);
        rewardFeedback = label(column, "", 15);
        rewardFeedback.setTextColor(0xFFFFC1A6);
        rewardFeedback.setMinHeight(dp(48));
        rewardFeedback.setVisibility(View.INVISIBLE);
        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);
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
        previewButton = CatUi.button(this, "机位调好，关闭预览（试验）", false, true, this::suppressPreview);
        CatUi.add(column, previewButton, -2, 8);
        LinearLayout actions = new LinearLayout(this);
        start = CatUi.button(this, "开始监测", false, true, this::requestStart);
        end = CatUi.button(this, "结束工作", false, true, this::endWork);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, -2, 1);
        startParams.rightMargin = dp(10);
        actions.addView(start, startParams);
        actions.addView(end, new LinearLayout.LayoutParams(0, -2, 1));
        CatUi.add(column, actions, -2, 20);
        soundButton = CatUi.button(this, "", false, true, () -> {
            soundsEnabled = !soundsEnabled;
            getSharedPreferences("ui_preferences", MODE_PRIVATE).edit().putBoolean("presence_sound_enabled", soundsEnabled).apply();
            detectionCue.reset(); cuePlayer.stop("sound_setting_changed"); render(); writeDiagnostic(true);
        });
        CatUi.add(column, soundButton, -2, 8);
        label(column, "临时切出暂停，回来自动恢复 · 主动暂停需继续", 12);
        if (ledger.work.active()) message = "上次工作已暂停，点击继续后重新确认在场。";
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { pauseCamera("监测已暂停。"); finish(); }
        });
        render();
        analyzer.execute(() -> {
            try {
                detector = ObjectDetector.createFromOptions(this, ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
                    .setRunningMode(RunningMode.IMAGE).setCategoryAllowlist(Collections.singletonList("person"))
                    .setScoreThreshold(0.20f).setMaxResults(10).build());
                handler.post(() -> {
                    if (destroyed) return;
                    modelReady = true;
                    message = recovery.failed() ? recovery.failureReason()
                        : recovery.wanted() ? "正在恢复监测，等待工作页可用。" : "人体检测已就绪，固定手机后点击开始/继续。";
                    maybeResumeCamera();
                    render(); writeDiagnostic(true);
                });
            } catch (Exception ex) {
                handler.post(() -> {
                    if (destroyed) return;
                    failCamera("人体模型启动失败，请退出工作页后重试。");
                    render(); writeDiagnostic(true);
                });
            }
        });
    }

    private boolean available() {
        return !destroyed && !isFinishing() && resumed && hasWindowFocus()
            && getSystemService(PowerManager.class).isInteractive()
            && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    /** Explicit pause/end clears recovery intent; automatic camera failure remains eligible for one reminder. */
    private boolean monitoringExpected() { return recovery.wanted() || recovery.failed(); }

    private String unavailableReason() {
        if (destroyed) return "activity_destroyed";
        if (isFinishing()) return "activity_finishing";
        if (!resumed) return "activity_paused";
        if (!hasWindowFocus()) return "window_focus_lost";
        if (!getSystemService(PowerManager.class).isInteractive()) return "screen_not_interactive";
        if (getSystemService(KeyguardManager.class).isKeyguardLocked()) return "keyguard_locked";
        return "unavailable";
    }

    private void recordAvailabilityEvent(String event, String trigger) {
        if (ledger == null || workDebugLog == null || !ledger.work.active()) return;
        boolean interactive = getSystemService(PowerManager.class).isInteractive();
        boolean keyguardLocked = getSystemService(KeyguardManager.class).isKeyguardLocked();
        workDebugLog.recordAvailabilityEvent(event, trigger, resumed, hasWindowFocus(), interactive,
            keyguardLocked, capturing, ledger.work);
    }

    private void requestStart() {
        if (recovery.wanted()) { pauseCamera("已暂停，已获得时间保留。"); return; }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 20);
            return;
        }
        recoveryModel.previewTrial.resetForManualStart();
        unrecordedReminder.reset();
        recovery.request();
        maybeResumeCamera();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 20) return;
        message = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
            ? "摄像头已授权，点击开始/继续监测。"
            : "摄像头未授权，未开始计时；可在系统应用设置中恢复权限。";
        render();
    }

    private void maybeResumeCamera() {
        if (!available() || !recovery.wanted()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            failCamera("摄像头权限不可用，监测已暂停，请授权后继续。");
            return;
        }
        if (recovery.beginAttempt(SystemClock.elapsedRealtime(), modelReady && !capturing)) startCamera();
    }

    private void startCamera() {
        BatterySnapshot startBattery = BatterySnapshot.capture(this);
        if (!saveWorkWithDebug(ledger.work.begin(System.currentTimeMillis(), SystemClock.elapsedRealtime(), ledger.bootCount),
                "camera_begin", startBattery)) {
            failCamera("保存失败，尚未开始计时，请稍后重试。");
            return;
        }
        workDebugLog.recordCameraEvent("CAMERA_START_REQUEST", "front_camera", ledger.work);
        ProtectionService service = ProtectionService.current();
        if (service != null) service.prepareManualChange();
        else ledger.engine.resetObservation(System.currentTimeMillis());
        clock.interrupt();
        detectionCue.reset(); cuePlayer.stop("camera_start"); sampledAt = -1; sequence = 0;
        observation = new PersonObservation(-1, -1, 0);
        capturing = true;
        message = "正在启动前置摄像头…";
        preview.setVisibility(recoveryModel.previewTrial.suppressed() ? View.INVISIBLE : View.VISIBLE);
        setTrialScreenDim(recoveryModel.previewTrial.suppressed());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int token = ++generation;
        analysisWidth = analysisHeight = 0;
        previewResolution = capturePolicy = "unbound";
        var future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (!capturing || token != generation || !available()) return;
            try {
                provider = future.get();
                CameraSelector selector = CameraSelector.DEFAULT_FRONT_CAMERA;
                if (!provider.hasCamera(selector)) {
                    failCamera("前置摄像头不可用，请退出工作页后重试。");
                    return;
                }
                provider.unbindAll();
                try {
                    bindCamera(selector, token, true);
                } catch (IllegalArgumentException unsupported) {
                    // Restore the previous working stream combination if negotiation rejects this one.
                    provider.unbindAll();
                    bindCamera(selector, token, false);
                }
                message = "等待新画面，请调整机位。";
            } catch (Exception ex) {
                retryCamera("摄像头启动失败或被占用，请关闭其他相机应用后重试。");
            }
            render();
        }, ContextCompat.getMainExecutor(this));
        render();
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private void bindCamera(CameraSelector selector, int token, boolean lowLoad) {
        Preview.Builder builder = new Preview.Builder();
        capturePolicy = lowLoad ? "low_resolution_default_fps" : "baseline_fallback";
        preview.setImplementationMode(lowLoad ? PreviewView.ImplementationMode.PERFORMANCE
            : PreviewView.ImplementationMode.COMPATIBLE);
        Range<Integer> target = THERMAL_FRAME_RATE;
        boolean requestLowFps = lowLoad
            && selector.filter(provider.getAvailableCameraInfos()).get(0).getSupportedFrameRateRanges().contains(target);
        if (lowLoad) {
            builder.setResolutionSelector(new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(THERMAL_STREAM_SIZE,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build());
            if (requestLowFps) {
                builder.setTargetFrameRate(target);
                capturePolicy = "requested_10_fps";
            }
        }
        ImageAnalysis.Builder analysisBuilder = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
        if (lowLoad) {
            // ResolutionStrategy uses sensor coordinates; the old target-resolution path selected 2448x2448 here.
            analysisBuilder.setResolutionSelector(new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(THERMAL_STREAM_SIZE,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build());
        } else {
            analysisBuilder.setTargetResolution(new Size(640, 480));
        }
        if (requestLowFps) {
            new Camera2Interop.Extender<>(analysisBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, target);
        }
        ImageAnalysis analysis = analysisBuilder.build();
        analysis.setAnalyzer(analyzer, frame -> analyze(frame, token));
        if (recoveryModel.previewTrial.suppressed()) {
            cameraPreview = null;
            boundCamera = provider.bindToLifecycle(this, selector, analysis);
            previewResolution = "unbound_after_calibration";
            capturePolicy = "analysis_only_" + capturePolicy;
            preview.setVisibility(View.INVISIBLE);
        } else {
            cameraPreview = builder.build();
            cameraPreview.setSurfaceProvider(preview.getSurfaceProvider());
            boundCamera = provider.bindToLifecycle(this, selector, cameraPreview, analysis);
            recoveryModel.previewTrial.previewBound(SystemClock.elapsedRealtime());
            var resolution = cameraPreview.getResolutionInfo();
            previewResolution = resolution == null ? "unknown" : resolution.getResolution().toString();
        }
        boundCamera.getCameraInfo().getCameraState().observe(this, state -> {
            if (!capturing || token != generation || !available() || state.getError() == null) return;
            if (state.getError().getType() == CameraState.ErrorType.CRITICAL)
                failCamera("摄像头发生错误，监测已暂停，请检查相机后重试。");
            else retryCamera("摄像头被占用或暂时不可用，请释放相机后重试。");
        });
        workDebugLog.recordCameraEvent("CAMERA_BOUND", capturePolicy + "; preview=" + (cameraPreview != null), ledger.work);
    }

    private void suppressPreview() {
        long now = SystemClock.elapsedRealtime();
        if (!capturing || provider == null || cameraPreview == null || !recoveryModel.previewTrial.canSuppress(now)) return;
        try {
            provider.unbind(cameraPreview);
            cameraPreview = null;
            if (!recoveryModel.previewTrial.suppress(now)) return;
            previewResolution = "unbound_after_calibration";
            if (!capturePolicy.startsWith("analysis_only_")) capturePolicy = "analysis_only_" + capturePolicy;
            preview.setVisibility(View.INVISIBLE);
            setTrialScreenDim(true);
            message = "自拍预览已关闭，人体监测继续。";
            workDebugLog.recordCameraEvent("PREVIEW_SUPPRESSED", capturePolicy, ledger.work);
            render();
            writeDiagnostic(true);
        } catch (RuntimeException ex) {
            message = "关闭自拍预览失败，仍按原方式监测。";
            render();
            writeDiagnostic(true);
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyze(ImageProxy frame, int token) {
        long capturedAt = SystemClock.elapsedRealtime();
        if (!capturing || token != generation || capturedAt - lastAnalysisElapsed < ANALYSIS_INTERVAL_MS || frame.getImage() == null) {
            frame.close();
            return;
        }
        lastAnalysisElapsed = capturedAt;
        int width = frame.getWidth(), height = frame.getHeight();
        Bitmap original = null, oriented = null;
        MPImage input = null;
        try {
            original = frame.toBitmap();
            Matrix rotation = new Matrix();
            rotation.postRotate(frame.getImageInfo().getRotationDegrees());
            oriented = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), rotation, true);
            input = new BitmapImageBuilder(oriented).build();
            var result = detector.detect(input);
            int candidates = 0, reliable = 0;
            float score = 0;
            for (Detection detection : result.detections()) {
                for (Category category : detection.categories()) {
                    if ("person".equals(category.categoryName())) {
                        candidates++; score = Math.max(score, category.score());
                        if (category.score() >= 0.50f) reliable++;
                        break;
                    }
                }
            }
            PersonObservation observed = new PersonObservation(candidates, reliable, score);
            long completed = SystemClock.elapsedRealtime();
            handler.post(() -> {
                if (!capturing || token != generation || !available()) return;
                sampledAt = capturedAt; processingMs = completed - capturedAt; observation = observed; sequence++;
                analysisWidth = width; analysisHeight = height;
                if (SystemClock.elapsedRealtime() - capturedAt > WorkClock.FRESH_MS) {
                    clock.interrupt();
                    detectionCue.reset(); cuePlayer.stop("stale_frame");
                    if (!saveWorkWithDebug(ledger.work.pause(capturedAt + WorkClock.FRESH_MS, ledger.bootCount), "stale_frame")) {
                        failCamera("保存失败，监测已暂停，请重试。");
                        return;
                    }
                    message = "画面处理延迟，已暂停计时。";
                    render();
                    return;
                }
                recovery.freshFrame(capturedAt);
                long delta = clock.observe(capturedAt, observed.clockSignal());
                workDebugLog.recordObservationContext(clock.state().name(), capturing, observed.candidates,
                    observed.reliable, observed.score);
                WorkProgress before = ledger.work;
                boolean counting = clock.counting();
                WorkProgress next = before.observe(delta, counting, SystemClock.elapsedRealtime(), ledger.bootCount);
                if (next != before && !saveWorkWithDebug(next, "person_observation")) {
                    failCamera("保存失败，已暂停；未确认的时间不会发放奖励。");
                    return;
                }
                workDebugLog.recordClockState(clock.state().name(), capturing, observed.candidates,
                    observed.reliable, observed.score, ledger.work);
                showRewardFeedback(before, ledger.work);
                if (soundsEnabled) cuePlayer.play(detectionCue.observe(capturedAt, SystemClock.elapsedRealtime(), observed.candidates, observed.reliable));
                render();
                writeDiagnostic(false);
            });
        } catch (RuntimeException ex) {
            handler.post(() -> {
                if (capturing && token == generation) failCamera("图像检测异常，点击继续重试。");
            });
        } finally {
            if (input != null) input.close();
            if (oriented != null && oriented != original) oriented.recycle();
            if (original != null) original.recycle();
            frame.close();
        }
    }

    private void pauseCamera(String reason) {
        MonitoringReturnTicket.clear();
        recovery.stop();
        releaseCamera(reason);
    }

    private void suspendCamera(String trigger) {
        boolean wanted = recovery.wanted();
        recordAvailabilityEvent("AVAILABILITY_LOST", trigger);
        recovery.suspend();
        releaseCamera(wanted ? "监测暂时中断，回到工作页后自动恢复。" : message);
    }

    private void retryCamera(String reason) {
        recovery.retry(SystemClock.elapsedRealtime(), reason);
        releaseCamera(recovery.failed() ? reason : "监测暂时中断，正在尝试恢复…");
    }

    private void failCamera(String reason) {
        MonitoringReturnTicket.clear();
        recovery.fail(reason);
        releaseCamera(reason);
    }

    private void releaseCamera(String reason) {
        capturing = false;
        generation++;
        clock.interrupt();
        detectionCue.reset(); cuePlayer.stop("camera_released"); sampledAt = -1;
        if (boundCamera != null) {
            boundCamera.getCameraInfo().getCameraState().removeObservers(this);
            boundCamera = null;
        }
        if (provider != null) provider.unbindAll();
        cameraPreview = null;
        setTrialScreenDim(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preview.setVisibility(View.INVISIBLE);
        rewardFeedback.animate().cancel();
        rewardFeedback.setVisibility(View.INVISIBLE);
        rewardUntilElapsed = 0;
        WorkProgress next = ledger.work.pause(SystemClock.elapsedRealtime(), ledger.bootCount);
        if (next != ledger.work && !saveWorkWithDebug(next, "camera_release")) {
            reason = "中断状态保存失败，监测已暂停，请重试。";
            recovery.fail(reason);
        }
        workDebugLog.recordCameraEvent("CAMERA_RELEASE", reason, ledger.work);
        message = reason;
        render();
        writeDiagnostic(true);
    }

    private void endWork() {
        BatterySnapshot endBattery = BatterySnapshot.capture(this);
        workDebugLog.recordEndRequested(endBattery, ledger.work);
        unrecordedReminder.reset();
        pauseCamera("本次工作已结束；下轮累计 " + ledger.work.selectedMinutes() + " 分钟开始获得奖励。");
        if (!saveWorkWithDebug(ledger.work.finish(), "work_finish", endBattery))
            message = "结束状态保存失败，计时已暂停，请重试结束。";
        render();
    }

    private void render() {
        if (stateText == null) return;
        String status = recovery.failed() ? recovery.failureReason() : message;
        if (capturing) {
            switch (clock.state()) {
                case PRESENT: status = "观察到在场 · 正在累计"; break;
                case WEAK_HOLD: status = "人体信号较弱 · 仍在累计（最多 2 分钟）"; break;
                case GRACE: status = "暂未见人物 · 短暂容错中"; break;
                case AWAY: status = "持续未见人物 · 计时暂停"; break;
                case CHECKING: status = "正在确认在场 · 尚未计时"; break;
                case UNCERTAIN:
                    status = observation.candidates > 0 && observation.reliable == 0
                        ? "人体信号持续较弱 · 已暂停，请调整机位后重新确认"
                        : "检测结果不一致 · 计时暂停";
                    break;
                case PAUSED: status = "等待有效新画面 · 计时暂停"; break;
            }
        }
        WorkProgress work = ledger.work;
        setTextIfChanged(rewardRule, WorkProgress.ruleText(work.displayedMinutes()));
        long remaining = work.resetRemainingMs(SystemClock.elapsedRealtime());
        if (remaining == 0) status += "\n中断已满 3 分钟，本轮重置；已到账奖励保留";
        else if (remaining > 0 && work.sessionValidMs() > 0)
            status += "\n还有 " + format(remaining + 999) + "，本轮进度将重置";
        setTextIfChanged(stateText, status);
        setTextIfChanged(validTime, format(work.sessionValidMs()));
        setTextIfChanged(counters, "本轮已获得 " + MainActivity.duration(work.earnedThisRound(), false) + " 娱乐");
        setTextIfChanged(start, recovery.wanted() ? "暂停" : work.active() ? "继续监测" : "开始监测");
        start.setEnabled(modelReady);
        setTextIfChanged(soundButton, soundsEnabled ? "提示音已开启 · 点击关闭" : "提示音已关闭 · 点击开启");
        end.setEnabled(work.active());
        if (!capturing) setTextIfChanged(previewHint, "摄像头已关闭");
        else if (recoveryModel.previewTrial.suppressed()) setTextIfChanged(previewHint, "自拍预览已关闭\n人体监测继续");
        previewHint.setVisibility(capturing && !recoveryModel.previewTrial.suppressed() ? View.GONE : View.VISIBLE);
        previewButton.setVisibility(capturing ? View.VISIBLE : View.GONE);
        long previewWait = recoveryModel.previewTrial.closeRemainingMs(SystemClock.elapsedRealtime());
        previewButton.setEnabled(capturing && cameraPreview != null && recoveryModel.previewTrial.canSuppress(SystemClock.elapsedRealtime()));
        setTextIfChanged(previewButton, recoveryModel.previewTrial.suppressed() ? "预览已关闭 · 监测继续"
            : previewWait > 0 ? "预览展示中 · " + ((previewWait + 999) / 1000) + "秒后可关闭"
            : cameraPreview == null ? "预览正在启动…" : "机位调好，关闭预览（试验）");
        int pose = capturing && clock.state() == WorkClock.State.AWAY ? R.drawable.cat_away
            : capturing && SystemClock.elapsedRealtime() < rewardUntilElapsed ? R.drawable.cat_reward : R.drawable.cat_work;
        setMascot(pose);
    }

    private void showRewardFeedback(WorkProgress before, WorkProgress after) {
        long reward = after.sessionRewardMs() - before.sessionRewardMs();
        if (reward <= 0) return;
        String feedback = "+" + MainActivity.duration(reward, false) + "娱乐时间";
        if (after.sessionValidMs() / after.thresholdMs() > before.sessionValidMs() / after.thresholdMs()) {
            feedback = "本轮有效工作 " + after.sessionValidMs() / WorkProgress.MINUTE_MS
                + " 分钟 · 本轮已挣得 " + format(after.earnedThisRound()) + " 娱乐时间";
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
            .setMessage("使用前置摄像头朝向你的肩部和躯干，手机可放在侧后方。无需露出人脸，背影和椅背遮挡效果仍在试用验证。\n\n新结果未见人物时播“滴～暂时离开”，看到可靠人物时播“欢迎归来”；同状态不重复，声音反映检测变化，不等于计时立即暂停或恢复。声音随媒体音量，可在工作页关闭。只要至少一个人体达到可靠标准就按在场处理；检测器不能区分是不是同一个人。已可靠确认在场后，若只剩一个或多个较弱人体候选，最多保留 2 分钟计时；候选数量变化或偶发一次可靠结果不会延长上限，超过后必须持续可靠确认约 1 秒。\n\n只观察是否在场，不证明正在工作，不识别身份或另一部手机。工作时保持亮屏；来电、电量提醒、切出、锁屏或打开说明会暂停；回到工作页后自动尝试恢复，重新确认在场后计时。主动暂停、返回首页或应用重开需手动继续；恢复失败时显示原因。任何非主动的无效记录持续 1 分钟后，提示音开启且适合播放时播一次“请注意，监控无效，监控无效”。\n\n" + WorkProgress.ruleText(ledger.work.displayedMinutes()) + "连续离开或暂停不足 3 分钟可续接；达到 3 分钟重置本轮进度。本轮重置不倒扣奖励，历史工作保留。" + ledger.expiryDescription() + "\n\n画面仅在本机处理，不保存、不上传。")
            .setPositiveButton("知道了", null).show();
    }

    private static void setTextIfChanged(TextView view, String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }

    private void maybePlayUnrecordedReminder(long now) {
        if (!unrecordedReminder.due(ledger.work, monitoringExpected(), available(), soundsEnabled, now)) return;
        long interruptedAt = ledger.work.interruptedAtElapsedMs();
        long duration = Math.max(0, now - interruptedAt);
        long cycle = unrecordedReminder.cycle(now);
        String cause = unrecordedCause();
        if (cuePlayer.isBusy()) {
            workDebugLog.recordReminderEvent("UNRECORDED_REMINDER_SUPPRESSED_BUSY", cause, duration, cycle, ledger.work);
            return;
        }
        workDebugLog.recordReminderEvent("UNRECORDED_REMINDER_DUE", cause, duration, cycle, ledger.work);
        cuePlayer.playUnrecordedReminder();
    }

    private String unrecordedCause() {
        if (!capturing) return recovery.failed() ? "camera_recovery_failed" : "camera_not_capturing";
        switch (clock.state()) {
            case AWAY: return "no_person";
            case UNCERTAIN: return PersonObservation.diagnosticKind(observation.candidates, observation.reliable);
            case CHECKING: return "presence_confirming";
            case PAUSED: return "no_fresh_frame";
            default: return "not_counting";
        }
    }
    private boolean saveWorkWithDebug(WorkProgress next, String source) {
        return saveWorkWithDebug(next, source, null);
    }

    private boolean saveWorkWithDebug(WorkProgress next, String source, BatterySnapshot batterySnapshot) {
        WorkProgress before = ledger.work;
        if (!ledger.saveWork(next)) return false;
        workDebugLog.recordCommit(before, ledger.work, source, batterySnapshot);
        return true;
    }
    private void setTrialScreenDim(boolean dimmed) {
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        float brightness = dimmed ? MONITORING_SCREEN_BRIGHTNESS : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        if (attributes.screenBrightness == brightness) return;
        attributes.screenBrightness = brightness;
        getWindow().setAttributes(attributes);
        trialScreenDimmed = dimmed;
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
        handler.removeCallbacks(tick);
        handler.post(tick);
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (recovery == null) return;
        if (!focus) suspendCamera("window_focus_lost");
        else {
            recordAvailabilityEvent("AVAILABILITY_RESTORED", "window_focus_gained");
            maybeResumeCamera();
        }
    }
    @Override protected void onPause() {
        if (recovery != null && recovery.wanted() && ledger != null && ledger.work.active())
            MonitoringReturnTicket.arm(ledger.work.startedWallMs(), SystemClock.elapsedRealtime());
        resumed = false;
        handler.removeCallbacks(tick);
        suspendCamera("activity_paused");
        super.onPause();
    }
    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacks(tick);
        if (isChangingConfigurations()) MonitoringReturnTicket.clear();
        else recovery.stop();
        cuePlayer.stop("activity_destroyed");
        analyzer.execute(() -> { if (detector != null) detector.close(); });
        analyzer.shutdown();
        super.onDestroy();
    }

    private void writeDiagnostic(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastDiagnosticWrite < 1000) return;
        lastDiagnosticWrite = now;
        boolean fresh = capturing && sampledAt >= 0 && now - sampledAt <= WorkClock.FRESH_MS;
        AtomicFile file = new AtomicFile(new File(getFilesDir(), "camera-status.json"));
        FileOutputStream stream = null;
        try {
            JSONObject data = new JSONObject();
            data.put("schema", 1).put("session", cameraSession).put("generation", generation)
                .put("heartbeat_elapsed_ms", now).put("captured_elapsed_ms", sampledAt).put("sequence", sequence)
                .put("running", capturing).put("model_ready", modelReady)
                .put("recovery_state", recovery.state().name()).put("recovery_failures", recovery.failures())
                .put("monitoring_wanted", recovery.wanted())
                .put("camera_permission", checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                .put("lens", "front")
                .put("capture_policy", capturePolicy).put("preview_resolution", previewResolution)
                .put("preview_bound", cameraPreview != null)
                .put("analysis_width", analysisWidth).put("analysis_height", analysisHeight)
                .put("analysis_interval_ms", ANALYSIS_INTERVAL_MS).put("screen_dimmed", trialScreenDimmed)
                .put("preview_close_remaining_ms", recoveryModel.previewTrial.closeRemainingMs(now))
                .put("preview_mode", preview.getImplementationMode().name())
                .put("scope", "whole_frame_person_trial").put("state", fresh ? clock.state().name() : "NO_FRESH_RESULT")
                .put("person_candidates", fresh ? observation.candidates : -1)
                .put("reliable_people", fresh ? observation.reliable : -1).put("max_score", fresh ? observation.score : 0)
                .put("observation_kind", fresh ? PersonObservation.diagnosticKind(observation.candidates, observation.reliable) : "stale_or_not_running")
                .put("processing_ms", processingMs).put("sounds_enabled", soundsEnabled)
                .put("audio_last_cue", cuePlayer.lastCue()).put("audio_status", cuePlayer.status())
                .put("audio_events", cuePlayer.events()).put("media_volume", cuePlayer.mediaVolume())
                .put("detail", message);
            workDebugLog.addStatus(data, ledger.work);
            stream = file.startWrite(); stream.write(data.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(stream);
        } catch (Exception ex) { if (stream != null) file.failWrite(stream); }
    }
}
