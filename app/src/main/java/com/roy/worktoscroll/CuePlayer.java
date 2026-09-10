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
    private final Context context;
    private MediaPlayer player;
    private ToneGenerator failureTone;
    private AudioFocusRequest failureFocus;
    private long events;
    private String lastCue = "none", status = "idle";

    CuePlayer(Context context) { this.context = context; }

    void play(DetectionCue.Cue cue) {
        if (cue == DetectionCue.Cue.NONE) return;
        stop();
        if (audioManager().getMode() != AudioManager.MODE_NORMAL) { status = "suppressed_call"; return; }
        events++;
        lastCue = cue == DetectionCue.Cue.AWAY ? "away" : "return";
        int resource = cue == DetectionCue.Cue.AWAY ? R.raw.person_away : R.raw.person_return;
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
                ready.start(); status = "playing";
            });
            next.setOnCompletionListener(completed -> {
                if (player != completed) return;
                player = null; completed.release(); status = "completed";
            });
            next.setOnErrorListener((failed, what, extra) -> {
                if (player == failed) { player = null; failed.release(); status = "error_" + what; }
                return true;
            });
            next.prepareAsync();
        } catch (Exception ex) {
            if (player == next) { player = null; next.release(); }
            status = "error_" + ex.getClass().getSimpleName();
        }
    }

    void stop() {
        if (failureTone != null) {
            ToneGenerator old = failureTone; failureTone = null; old.release();
        }
        if (failureFocus != null) {
            AudioFocusRequest old = failureFocus; failureFocus = null;
            audioManager().abandonAudioFocusRequest(old);
        }
        if (player != null) {
            MediaPlayer old = player; player = null; old.release();
        }
        status = "stopped";
    }

    /** One short neutral alert, never the person-away phrase and never delayed for audio focus. */
    void playFailure() {
        stop();
        AudioManager audio = audioManager();
        if (audio.getMode() != AudioManager.MODE_NORMAL || audio.isMusicActive() || mediaVolume() == 0) {
            status = "failure_alert_suppressed";
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
                stop(); status = "failure_alert_suppressed"; return;
            }
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            failureTone = tone;
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
            events++; lastCue = "monitoring_failure"; status = "failure_alert_playing";
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (failureTone == tone) { stop(); status = "failure_alert_completed"; }
            }, 600);
        } catch (RuntimeException ex) { stop(); status = "failure_alert_unavailable"; }
    }

    private AudioManager audioManager() { return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE); }
    long events() { return events; }
    String lastCue() { return lastCue; }
    String status() { return status; }
    int mediaVolume() { return ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).getStreamVolume(AudioManager.STREAM_MUSIC); }
}
