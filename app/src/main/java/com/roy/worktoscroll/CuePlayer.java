package com.roy.worktoscroll;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.media.MediaPlayer;

/** Bundled fixed phrases: no TTS service, network, volume changes, or queued stale speech. */
final class CuePlayer {
    interface EventListener {
        void onCueEvent(String event, String cue, String detail);
    }
    private final Context context;
    private final EventListener eventsListener;
    private MediaPlayer player;
    private ToneGenerator failureTone;
    private AudioFocusRequest failureFocus;
    private long events;
    private String lastCue = "none", status = "idle";
    private String activeCue = "none";
    private String activeEventPrefix = "AUDIO_CUE";
    private boolean activeCueStarted;

    CuePlayer(Context context, EventListener eventsListener) {
        this.context = context;
        this.eventsListener = eventsListener;
    }

    void play(DetectionCue.Cue cue) {
        if (cue == DetectionCue.Cue.NONE) return;
        String cueName = cue == DetectionCue.Cue.AWAY ? "away" : "return";
        int resource = cue == DetectionCue.Cue.AWAY ? R.raw.person_away : R.raw.person_return;
        playResource(cueName, resource, "AUDIO_CUE", false);
    }

    /** A neutral foreground reminder; never interrupts calls or other active media. */
    void playUnrecordedReminder() {
        playResource("unrecorded_reminder", R.raw.unrecorded_reminder,
            "AUDIO_UNRECORDED_REMINDER", true);
    }

    boolean isBusy() { return player != null || failureTone != null; }

    private void playResource(String cueName, int resource, String eventPrefix, boolean suppressActiveMedia) {
        stop("superseded");
        AudioManager audio = audioManager();
        if (audio.getMode() != AudioManager.MODE_NORMAL || (suppressActiveMedia && (audio.isMusicActive() || mediaVolume() == 0))) {
            status = audio.getMode() != AudioManager.MODE_NORMAL ? "suppressed_call" : "suppressed_active_media";
            report(eventPrefix + "_SUPPRESSED", cueName, status);
            return;
        }
        events++;
        lastCue = activeCue = cueName;
        activeEventPrefix = eventPrefix;
        activeCueStarted = false;
        report(activeEventPrefix + "_REQUESTED", activeCue, "preparing");
        MediaPlayer next = new MediaPlayer();
        player = next;
        status = "preparing";
        try (AssetFileDescriptor source = context.getResources().openRawResourceFd(resource)) {
            next.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            next.setDataSource(source.getFileDescriptor(), source.getStartOffset(), source.getLength());
            next.setOnPreparedListener(ready -> {
                if (player != ready) return;
                try {
                    ready.start();
                    activeCueStarted = true;
                    status = "playing";
                    report(activeEventPrefix + "_STARTED", activeCue, status);
                } catch (RuntimeException failure) {
                    if (player == ready) {
                        player = null;
                        ready.release();
                        status = "error_" + failure.getClass().getSimpleName();
                        report(activeEventPrefix + "_ERROR", activeCue, status);
                        activeCue = "none";
                        activeCueStarted = false;
                    }
                }
            });
            next.setOnCompletionListener(completed -> {
                if (player != completed) return;
                String completedCue = activeCue;
                player = null; completed.release(); status = "completed";
                report(activeEventPrefix + "_COMPLETED", completedCue, status);
                activeCue = "none";
                activeCueStarted = false;
            });
            next.setOnErrorListener((failed, what, extra) -> {
                if (player == failed) {
                    String failedCue = activeCue;
                    player = null; failed.release(); status = "error_" + what + "_" + extra;
                    report(activeEventPrefix + "_ERROR", failedCue, status);
                    activeCue = "none";
                    activeCueStarted = false;
                }
                return true;
            });
            next.prepareAsync();
        } catch (Exception ex) {
            if (player == next) { player = null; next.release(); }
            status = "error_" + ex.getClass().getSimpleName();
            report(activeEventPrefix + "_ERROR", activeCue, status);
            activeCue = "none";
            activeCueStarted = false;
        }
    }

    void stop(String reason) {
        if (failureTone != null) {
            ToneGenerator old = failureTone; failureTone = null; old.release();
        }
        if (failureFocus != null) {
            AudioFocusRequest old = failureFocus; failureFocus = null;
            audioManager().abandonAudioFocusRequest(old);
        }
        if (player != null) {
            String stoppedCue = activeCue;
            boolean wasStarted = activeCueStarted;
            MediaPlayer old = player; player = null; old.release();
            report(activeEventPrefix + (wasStarted ? "_STOPPED" : "_CANCELLED"), stoppedCue, reason);
        }
        activeCue = "none";
        activeCueStarted = false;
        status = "stopped";
    }

    void stop() { stop("unspecified"); }

    /** One short neutral alert, never the person-away phrase and never delayed for audio focus. */
    void playFailure() {
        stop("failure_alert");
        AudioManager audio = audioManager();
        if (audio.getMode() != AudioManager.MODE_NORMAL || audio.isMusicActive() || mediaVolume() == 0) {
            status = "failure_alert_suppressed";
            report("AUDIO_FAILURE_SUPPRESSED", "monitoring_failure", status);
            return;
        }
        try {
            failureFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(change -> { if (change < 0 && failureTone != null) stop(); })
                .build();
            if (audio.requestAudioFocus(failureFocus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                || audio.getMode() != AudioManager.MODE_NORMAL) {
                stop("failure_focus_denied"); status = "failure_alert_suppressed";
                report("AUDIO_FAILURE_SUPPRESSED", "monitoring_failure", status); return;
            }
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            failureTone = tone;
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
            events++; lastCue = "monitoring_failure"; status = "failure_alert_playing";
            report("AUDIO_FAILURE_STARTED", lastCue, status);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (failureTone == tone) {
                    stop("failure_completed"); status = "failure_alert_completed";
                    report("AUDIO_FAILURE_COMPLETED", "monitoring_failure", status);
                }
            }, 600);
        } catch (RuntimeException ex) {
            stop("failure_exception"); status = "failure_alert_unavailable";
            report("AUDIO_FAILURE_ERROR", "monitoring_failure", status);
        }
    }

    private void report(String event, String cue, String detail) {
        if (eventsListener != null) eventsListener.onCueEvent(event, cue, detail);
    }

    private AudioManager audioManager() { return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE); }
    long events() { return events; }
    String lastCue() { return lastCue; }
    String status() { return status; }
    int mediaVolume() { return ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).getStreamVolume(AudioManager.STREAM_MUSIC); }
}
