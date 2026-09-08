package com.roy.presenceexperiment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Size;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Separate offline debug app; never touches WorkToScroll's service or ledger. */
public final class MainActivity extends ComponentActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService analyzer = Executors.newSingleThreadExecutor();
    private final PresenceGate gate = new PresenceGate();
    private final DetectionCue detectionCue = new DetectionCue();
    private CuePlayer cuePlayer;
    private final String session = UUID.randomUUID().toString();
    private PreviewView preview;
    private TextView status;
    private ObjectDetector detector;
    private ProcessCameraProvider provider;
    private volatile boolean running, modelReady;
    private volatile int generation;
    private boolean resumed, startRequested, destroyed;
    private int lens = CameraSelector.LENS_FACING_BACK;
    private long sampledAt = -1, resultAt = -1, sequence, lastWrite;
    private long lastAnalyze = -1, inferenceMs;
    private int people = -1, reliable = -1;
    private float score;
    private String detail = "模型准备中";
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            report(false);
            main.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        cuePlayer = new CuePlayer(this);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 64, 24, 40);
        page.setBackgroundColor(Color.rgb(15, 15, 15));
        TextView title = text("人体识别实验", 24);
        page.addView(title);
        page.addView(text("独立测试 · 不计奖励\n画面仅在本机内存处理，不保存、不上传。\n先允许相机，再等电脑上的下一步指令。", 15));
        status = text("模型准备中", 17);
        page.addView(status);
        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        page.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        Button permission = new Button(this);
        permission.setText("允许使用相机");
        permission.setOnClickListener(v -> requestCamera());
        page.addView(permission);
        Button start = new Button(this);
        start.setText("开始本次检测");
        start.setOnClickListener(v -> { startRequested = true; maybeStart(); });
        page.addView(start);
        Button stop = new Button(this);
        stop.setText("停止检测");
        stop.setOnClickListener(v -> stop("已停止"));
        page.addView(stop);
        LinearLayout soundTests = new LinearLayout(this);
        Button awayTest = new Button(this);
        awayTest.setText("试听离开声");
        awayTest.setOnClickListener(v -> audition(DetectionCue.Cue.AWAY));
        soundTests.addView(awayTest, new LinearLayout.LayoutParams(0, -2, 1));
        Button returnTest = new Button(this);
        returnTest.setText("试听归来声");
        returnTest.setOnClickListener(v -> audition(DetectionCue.Cue.RETURN));
        soundTests.addView(returnTest, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(soundTests);
        setContentView(page);
        main.post(ticker);
        analyzer.execute(() -> {
            try {
                detector = ObjectDetector.createFromOptions(this, ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setCategoryAllowlist(Collections.singletonList("person"))
                    .setScoreThreshold(0.20f).setMaxResults(10).build());
                modelReady = true;
                main.post(() -> { detail = "已就绪，等待相机授权及开始指令"; report(true); maybeStart(); });
            } catch (Exception ex) {
                main.post(() -> { detail = "模型准备失败：" + ex.getClass().getSimpleName(); report(true); });
            }
        });
        handleCommand(getIntent());
        // Permission is initiated by the user; neither installation nor launch starts the camera.
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.LTGRAY);
        view.setPadding(0, 8, 0, 8); view.setGravity(Gravity.CENTER_HORIZONTAL);
        return view;
    }

    private void requestCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 7);
        } else { detail = "相机已授权，等待开始指令"; report(true); }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleCommand(intent);
    }

    private void handleCommand(Intent intent) {
        String command = intent.getStringExtra("command");
        if (command == null) return;
        if (command.equals("test-away")) { audition(DetectionCue.Cue.AWAY); return; }
        if (command.equals("test-return")) { audition(DetectionCue.Cue.RETURN); return; }
        if (command.equals("stop")) { stop("电脑已停止本次检测"); return; }
        if (command.equals("start")) {
            int nextLens = "front".equals(intent.getStringExtra("lens"))
                ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            stop("准备新一段检测");
            lens = nextLens; startRequested = true; maybeStart();
        }
    }

    private void maybeStart() {
        if (!startRequested || running || !resumed || !hasWindowFocus() || !modelReady) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            startRequested = false; detail = "请先点击允许使用相机"; report(true); return;
        }
        startRequested = false; running = true; generation++;
        int token = generation;
        gate.reset(); detectionCue.reset(); cuePlayer.stop(); sampledAt = resultAt = -1; sequence = 0;
        people = reliable = -1; score = 0;
        detail = "摄像头启动中";
        report(true);
        var future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (!running || token != generation || !resumed) return;
            try {
                provider = future.get();
                CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lens).build();
                if (!provider.hasCamera(selector)) { stop("该镜头不可用"); return; }
                Preview cameraPreview = new Preview.Builder().build();
                cameraPreview.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(analyzer, frame -> analyze(frame, token));
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, cameraPreview, analysis);
                detail = "全画面人体初筛，尚未校准工作区域";
            } catch (Exception ex) { stop("相机启动失败：" + ex.getClass().getSimpleName()); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyze(ImageProxy frame, int token) {
        long captured = SystemClock.elapsedRealtime();
        if (!running || token != generation || (lastAnalyze >= 0 && captured - lastAnalyze < 500)) {
            frame.close(); return;
        }
        lastAnalyze = captured;
        Bitmap original = null, oriented = null;
        MPImage image = null;
        try {
            original = frame.toBitmap();
            Matrix matrix = new Matrix();
            matrix.postRotate(frame.getImageInfo().getRotationDegrees());
            oriented = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);
            image = new BitmapImageBuilder(oriented).build();
            ObjectDetectorResult result = detector.detect(image);
            int found = 0, confident = 0;
            float highest = 0;
            for (Detection detection : result.detections()) {
                for (Category category : detection.categories()) {
                    if (category.categoryName().equals("person")) {
                        found++;
                        highest = Math.max(highest, category.score());
                        if (category.score() >= 0.50f) confident++;
                        break;
                    }
                }
            }
            final int count = found, strong = confident;
            final float best = highest;
            final long completed = SystemClock.elapsedRealtime();
            main.post(() -> {
                if (!running || token != generation || !resumed || !hasWindowFocus()) return;
                sampledAt = captured; resultAt = completed; inferenceMs = completed - captured;
                sequence++; people = count; reliable = strong; score = best;
                if (SystemClock.elapsedRealtime() - captured <= PresenceGate.FRESH_MS) {
                    // A weak-only detection is uncertainty, not evidence that nobody is present.
                    gate.observe(captured, strong == 0 && count > 0 ? -1 : strong);
                    cuePlayer.play(detectionCue.observe(captured, SystemClock.elapsedRealtime(), count, strong));
                } else { gate.reset(); detectionCue.reset(); cuePlayer.stop(); }
                report(false);
            });
        } catch (Exception ex) {
            main.post(() -> { if (running && token == generation) stop("检测失败：" + ex.getClass().getSimpleName()); });
        } finally {
            if (image != null) image.close();
            if (oriented != null && oriented != original) oriented.recycle();
            if (original != null) original.recycle();
            frame.close();
        }
    }

    private void stop(String reason) {
        running = false; startRequested = false; generation++;
        detectionCue.reset();
        if (cuePlayer != null) cuePlayer.stop();
        if (provider != null) provider.unbindAll();
        gate.reset(); people = reliable = -1; score = 0; sampledAt = resultAt = -1;
        detail = reason; report(true);
    }

    private void audition(DetectionCue.Cue cue) {
        stop("声音试听：不代表检测到人物");
        cuePlayer.play(cue);
        report(true);
    }

    private void report(boolean force) {
        if (status == null) return;
        long now = SystemClock.elapsedRealtime();
        long frameAge = sampledAt < 0 ? -1 : now - sampledAt;
        boolean fresh = running && frameAge >= 0 && frameAge <= PresenceGate.FRESH_MS;
        if (running && sampledAt >= 0 && !fresh) { detectionCue.reset(); cuePlayer.stop(); }
        String state = !running ? "STOPPED" : !fresh ? "NO_FRESH_RESULT" : gate.state(now);
        String lensName = lens == CameraSelector.LENS_FACING_BACK ? "back" : "front";
        status.setText(detail + "\n" + (running ? "检测运行中" : "未运行") +
            (fresh ? " · 人体候选 " + people + " · 可靠候选 " + reliable : " · 暂无新读数"));
        if (!force && now - lastWrite < 1000) return;
        lastWrite = now;
        AtomicFile file = new AtomicFile(new File(getFilesDir(), "status.json"));
        FileOutputStream stream = null;
        try {
            JSONObject data = new JSONObject();
            data.put("schema", 1).put("session", session).put("generation", generation)
                .put("heartbeat_elapsed_ms", now).put("captured_elapsed_ms", sampledAt)
                .put("result_elapsed_ms", resultAt).put("sequence", sequence)
                .put("running", running).put("model_ready", modelReady)
                .put("camera_permission", checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                .put("lens", lensName).put("scope", "whole_frame_screening")
                .put("state", state).put("frame_age_ms", frameAge).put("fresh", fresh)
                .put("person_candidates", fresh ? people : -1)
                .put("reliable_people", fresh ? reliable : -1)
                .put("max_score", fresh ? score : 0).put("processing_ms", inferenceMs)
                .put("model", "efficientdet_lite0_int8").put("score_threshold", 0.50)
                .put("audio_last_cue", cuePlayer.lastCue()).put("audio_status", cuePlayer.status())
                .put("audio_events", cuePlayer.events()).put("media_volume", cuePlayer.mediaVolume())
                .put("detail", detail);
            stream = file.startWrite();
            stream.write(data.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (Exception ex) {
            if (stream != null) file.failWrite(stream);
            status.setText("状态写入失败，请停止本次测试");
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        detail = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ? "相机已授权，等待电脑上的开始指令" : "相机未授权";
        report(true);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; maybeStart(); }
    @Override protected void onPause() { resumed = false; stop("页面离开前台，已停止"); super.onPause(); }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus && (running || cuePlayer != null)) stop("窗口失去焦点，已停止");
        else if (focus) maybeStart();
    }
    @Override protected void onDestroy() {
        stop("页面已关闭"); destroyed = true; main.removeCallbacks(ticker);
        analyzer.execute(() -> { if (detector != null) detector.close(); });
        analyzer.shutdown(); super.onDestroy();
    }
}
