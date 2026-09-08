package com.roy.worktoscroll;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;

/** Bundled fixed phrases: no TTS service, network, volume changes, or queued stale speech. */
final class CuePlayer {
    private final Context context;
    private MediaPlayer player;
    private long events;
    private String lastCue = "none", status = "idle";

    CuePlayer(Context context) { this.context = context; }

    void play(DetectionCue.Cue cue) {
        if (cue == DetectionCue.Cue.NONE) return;
        stop();
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
        if (player != null) {
            MediaPlayer old = player; player = null; old.release();
        }
        status = "stopped";
    }
    long events() { return events; }
    String lastCue() { return lastCue; }
    String status() { return status; }
    int mediaVolume() { return ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).getStreamVolume(AudioManager.STREAM_MUSIC); }
}
