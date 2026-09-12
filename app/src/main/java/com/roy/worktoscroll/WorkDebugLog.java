package com.roy.worktoscroll;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Bounded debug-only event journal; camera pixels and page content are never recorded. */
final class WorkDebugLog {
    static final String EVENTS_FILE = "work-session-events.jsonl";
    static final String LATEST_FILE = "work-session-latest.json";
    private static final String STATE_PREFS = "work_debug_state";
    private static final long CHECKPOINT_MS = 60_000;
    private static final long MAX_EVENTS_BYTES = 512 * 1024;

    private final File directory;
    private final SharedPreferences state;
    private final LongSupplier wallClock;
    private final LongSupplier elapsedClock;
    private final boolean enabled;
    private String lastClockState = "";
    private String lastLoggedClockState = "";
    private String lastObservationKind = "invalid_observation";
    private int lastCandidates = Integer.MIN_VALUE;
    private int lastReliable = Integer.MIN_VALUE;
    private float lastScore;
    private boolean lastCapturing;
    private String lastError = "";

    WorkDebugLog(Context context) {
        Context app = context.getApplicationContext();
        directory = app.getFilesDir();
        state = app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        wallClock = System::currentTimeMillis;
        elapsedClock = android.os.SystemClock::elapsedRealtime;
        enabled = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    synchronized void recordCommit(WorkProgress before, WorkProgress after, String source) {
        recordCommit(before, after, source, null);
    }

    synchronized void recordCommit(WorkProgress before, WorkProgress after, String source,
                                   BatterySnapshot batterySnapshot) {
        if (!enabled) return;
        long wall = wallClock.getAsLong();
        long elapsed = elapsedClock.getAsLong();
        try {
            if (!before.active() && after.active()) {
                start(after, wall, elapsed, source, false, batterySnapshot);
                return;
            }
            if (before.active() && !after.active()) {
                if (!matches(before)) start(before, wall, elapsed, "attach_before_finish", true, null);
                BatterySnapshot endBattery = batterySnapshot == null
                    ? BatterySnapshot.unavailable(wall, elapsed, "end_snapshot_not_provided") : batterySnapshot;
                JSONObject extra = new JSONObject().put("outcome", "finished");
                addStoredStartBattery(extra);
                addEndBattery(extra, endBattery);
                append("WORK_FINISH", after, wall, elapsed, source, extra);
                writeLatest("finished", after, wall, elapsed, endBattery);
                persistActive(false);
                return;
            }
            if (!after.active()) return;
            if (!matches(after)) start(after, wall, elapsed, "attach_active_session", true, batterySnapshot);

            boolean changed = false;
            if (WorkDebugMetrics.roundReset(before, after)) {
                state.edit().putInt("reset_count", resetCount() + 1).commit();
                JSONObject extra = new JSONObject()
                    .put("previous_round_valid_ms", before.sessionValidMs())
                    .put("new_round_valid_ms", after.sessionValidMs());
                append("ROUND_RESET", after, wall, elapsed, source, extra);
                changed = true;
            }
            if (WorkDebugMetrics.interruptionStarted(before, after)) {
                state.edit().putInt("interruption_count", interruptionCount() + 1).commit();
                append("INTERRUPTION_START", after, wall, elapsed, source, new JSONObject());
                changed = true;
            } else if (WorkDebugMetrics.interruptionEnded(before, after)) {
                JSONObject extra = new JSONObject().put("interruption_duration_ms",
                    Math.max(0, elapsed - before.interruptedAtElapsedMs()));
                append("INTERRUPTION_END", after, wall, elapsed, source, extra);
                changed = true;
            }

            long lastTotal = state.getLong("last_logged_total_ms", baselineTotal());
            long lastElapsed = state.getLong("last_checkpoint_elapsed_ms", startedElapsed());
            if (!changed && (after.totalValidMs() - lastTotal >= CHECKPOINT_MS
                    || elapsed - lastElapsed >= CHECKPOINT_MS)) {
                append("CHECKPOINT", after, wall, elapsed, source, new JSONObject());
                state.edit().putLong("last_logged_total_ms", after.totalValidMs())
                    .putLong("last_checkpoint_elapsed_ms", elapsed).commit();
            }
        } catch (Exception failure) {
            lastError = failure.getClass().getSimpleName();
        }
    }

    synchronized void recordObservationContext(String clockState, boolean capturing, int candidates,
                                               int reliable, float score) {
        lastClockState = clockState;
        lastCapturing = capturing;
        lastCandidates = candidates;
        lastReliable = reliable;
        lastScore = score;
        lastObservationKind = PersonObservation.diagnosticKind(candidates, reliable);
    }

    synchronized void recordClockState(String clockState, boolean capturing, int candidates,
                                       int reliable, float score, WorkProgress progress) {
        if (!enabled || !progress.active() || !matches(progress)) return;
        recordObservationContext(clockState, capturing, candidates, reliable, score);
        if (clockState.equals(lastLoggedClockState)) return;
        lastLoggedClockState = clockState;
        try {
            JSONObject extra = new JSONObject().put("clock_state", clockState)
                .put("capturing", capturing).put("person_candidates", candidates)
                .put("reliable_people", reliable).put("max_score", score)
                .put("observation_kind", lastObservationKind);
            append("CLOCK_STATE", progress, wallClock.getAsLong(), elapsedClock.getAsLong(), "work_clock", extra);
        } catch (Exception failure) {
            lastError = failure.getClass().getSimpleName();
        }
    }

    synchronized void recordCameraEvent(String event, String detail, WorkProgress progress) {
        if (!enabled || !progress.active() || !matches(progress)) return;
        try {
            append(event, progress, wallClock.getAsLong(), elapsedClock.getAsLong(), "camera",
                new JSONObject().put("detail", detail));
        } catch (Exception failure) {
            lastError = failure.getClass().getSimpleName();
        }
    }

    synchronized void recordAvailabilityEvent(String event, String trigger, boolean resumed,
                                              boolean focused, boolean interactive, boolean keyguardLocked,
                                              boolean capturing, WorkProgress progress) {
        if (!enabled || !progress.active() || !matches(progress)) return;
        try {
            JSONObject extra = new JSONObject().put("trigger", trigger).put("resumed", resumed)
                .put("window_focused", focused).put("interactive", interactive)
                .put("keyguard_locked", keyguardLocked).put("capturing", capturing);
            append(event, progress, wallClock.getAsLong(), elapsedClock.getAsLong(), "availability", extra);
        } catch (Exception failure) {
            lastError = failure.getClass().getSimpleName();
        }
    }

    synchronized void recordAudioEvent(String event, String cue, String detail, WorkProgress progress) {
        if (!enabled || !progress.active() || !matches(progress)) return;
        try {
            append(event, progress, wallClock.getAsLong(), elapsedClock.getAsLong(), "audio",
                new JSONObject().put("cue", cue).put("detail", detail));
        } catch (Exception failure) {
            lastError = failure.getClass().getSimpleName();
        }
    }

    synchronized void recordReminderEvent(String event, String cause, long interruptionDurationMs,
                                          long cycle, WorkProgress progress) {
        if (!enabled || !progress.active() || !matches(progress)) return;
        try {
            JSONObject extra = new JSONObject().put("cause", cause)
                .put("interruption_duration_ms", interruptionDurationMs).put("reminder_cycle", cycle);
            append(event, progress, wallClock.getAsLong(), elapsedClock.getAsLong(), "unrecorded_reminder", extra);
        } catch (Exception failure) {
            lastError = failure.getClass().getSimpleName();
        }
    }

    synchronized void recordEndRequested(BatterySnapshot batterySnapshot, WorkProgress progress) {
        if (!enabled || !progress.active() || !matches(progress)) return;
        long wall = wallClock.getAsLong();
        long elapsed = elapsedClock.getAsLong();
        try {
            JSONObject extra = new JSONObject().put("outcome", "end_requested");
            addStoredStartBattery(extra);
            addEndBattery(extra, batterySnapshot);
            append("WORK_END_REQUESTED", progress, wall, elapsed, "end_button", extra);
            writeLatest("end_requested", progress, wall, elapsed, batterySnapshot);
        } catch (Exception failure) {
            lastError = failure.getClass().getSimpleName();
        }
    }

    synchronized void addStatus(JSONObject data, WorkProgress progress) {
        try {
            boolean active = enabled && matches(progress);
            data.put("work_debug_enabled", enabled)
                .put("work_debug_session", active ? sessionId() : "")
                .put("work_debug_valid_ms", active ? WorkDebugMetrics.validSince(baselineTotal(), progress) : 0)
                .put("work_debug_reset_count", active ? resetCount() : 0)
                .put("work_debug_interruption_count", active ? interruptionCount() : 0)
                .put("work_debug_error", lastError);
        } catch (Exception ignored) {}
    }

    private void start(WorkProgress progress, long wall, long elapsed, String source, boolean attached,
                       BatterySnapshot batterySnapshot) throws Exception {
        long baseline = attached ? Math.max(0, progress.totalValidMs() - progress.sessionValidMs()) : progress.totalValidMs();
        String id = UUID.randomUUID().toString();
        BatterySnapshot startBattery = batterySnapshot == null
            ? BatterySnapshot.unavailable(wall, elapsed, "start_snapshot_not_provided") : batterySnapshot;
        boolean saved = state.edit().putBoolean("active", true).putString("session_id", id)
            .putLong("started_wall_ms", progress.startedWallMs() > 0 ? progress.startedWallMs() : wall)
            .putLong("started_elapsed_ms", elapsed).putLong("baseline_total_ms", baseline)
            .putLong("last_logged_total_ms", progress.totalValidMs()).putLong("last_checkpoint_elapsed_ms", elapsed)
            .putLong("start_battery_sample_wall_ms", startBattery.wallMs)
            .putLong("start_battery_sample_elapsed_ms", startBattery.elapsedMs)
            .putInt("start_battery_temperature_tenths_c", startBattery.temperatureTenthsC)
            .putInt("start_battery_level_percent", startBattery.levelPercent)
            .putInt("start_battery_voltage_mv", startBattery.voltageMv)
            .putInt("start_battery_status", startBattery.status)
            .putInt("start_battery_plugged", startBattery.plugged)
            .putString("start_battery_read_error", startBattery.error)
            .putString("start_battery_sample_kind", attached ? "attached_session" : "work_begin")
            .putInt("reset_count", 0).putInt("interruption_count", 0).commit();
        if (!saved) throw new IllegalStateException("debug_state_save_failed");
        lastClockState = "";
        lastLoggedClockState = "";
        lastObservationKind = "invalid_observation";
        lastCandidates = Integer.MIN_VALUE;
        lastReliable = Integer.MIN_VALUE;
        lastScore = 0;
        lastCapturing = false;
        JSONObject extra = new JSONObject().put("attached_to_existing", attached)
            .put("baseline_kind", attached ? "current_round_approximation" : "exact_session_start");
        merge(extra, startBattery.json(""));
        extra.put("battery_sample_stage", attached ? "attached_session" : "work_begin");
        append(attached ? "SESSION_ATTACH" : "WORK_BEGIN", progress, wall, elapsed, source, extra);
        writeLatest("active", progress, wall, elapsed, null);
    }

    private boolean matches(WorkProgress progress) {
        return state.getBoolean("active", false)
            && state.getLong("started_wall_ms", -1) == progress.startedWallMs();
    }

    private void append(String event, WorkProgress progress, long wall, long elapsed,
                        String source, JSONObject extra) throws Exception {
        rotateIfNeeded();
        JSONObject data = common(event, progress, wall, elapsed, source);
        var keys = extra.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            data.put(key, extra.get(key));
        }
        byte[] bytes = (data.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(new File(directory, EVENTS_FILE), true)) {
            out.write(bytes);
            out.flush();
        }
        writeLatest("active", progress, wall, elapsed, null);
    }

    private JSONObject common(String event, WorkProgress progress, long wall, long elapsed, String source) throws Exception {
        return new JSONObject().put("schema", 1).put("event", event).put("session_id", sessionId())
            .put("wall_ms", wall).put("elapsed_ms", elapsed).put("source", source)
            .put("started_wall_ms", startedWall()).put("wall_duration_ms", WorkDebugMetrics.wallDuration(startedWall(), wall))
            .put("baseline_total_ms", baselineTotal()).put("work_total_ms", progress.totalValidMs())
            .put("test_valid_ms", WorkDebugMetrics.validSince(baselineTotal(), progress))
            .put("round_valid_ms", progress.sessionValidMs()).put("round_reward_ms", progress.sessionRewardMs())
            .put("round_minutes", progress.roundMinutes()).put("active", progress.active())
            .put("interrupted_at_elapsed_ms", progress.interruptedAtElapsedMs())
            .put("reset_count", resetCount()).put("interruption_count", interruptionCount())
            .put("last_clock_state", lastClockState).put("capturing", lastCapturing)
            .put("person_candidates", lastCandidates == Integer.MIN_VALUE ? -1 : lastCandidates)
            .put("reliable_people", lastReliable == Integer.MIN_VALUE ? -1 : lastReliable)
            .put("max_score", lastScore).put("observation_kind", lastObservationKind);
    }

    private void writeLatest(String outcome, WorkProgress progress, long wall, long elapsed,
                             BatterySnapshot endBattery) throws Exception {
        JSONObject data = common("SUMMARY", progress, wall, elapsed, "journal")
            .put("outcome", outcome).put("log_file", EVENTS_FILE);
        addStoredStartBattery(data);
        if (endBattery != null) addEndBattery(data, endBattery);
        AtomicFile file = new AtomicFile(new File(directory, LATEST_FILE));
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(data.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception failure) {
            if (out != null) file.failWrite(out);
            throw failure;
        }
    }

    private void rotateIfNeeded() {
        File events = new File(directory, EVENTS_FILE);
        if (!events.exists() || events.length() < MAX_EVENTS_BYTES) return;
        File previous = new File(directory, EVENTS_FILE + ".1");
        if (previous.exists()) previous.delete();
        events.renameTo(previous);
    }

    private void persistActive(boolean active) {
        state.edit().putBoolean("active", active).commit();
    }

    private void addStoredStartBattery(JSONObject data) throws Exception {
        BatterySnapshot startBattery = storedStartBattery();
        merge(data, startBattery.json("start_"));
        data.put("start_battery_sample_kind", state.getString("start_battery_sample_kind", "unknown"));
    }

    private void addEndBattery(JSONObject data, BatterySnapshot endBattery) throws Exception {
        BatterySnapshot startBattery = storedStartBattery();
        merge(data, endBattery.json("end_"));
        int delta = BatterySnapshot.temperatureDeltaTenths(startBattery, endBattery);
        data.put("battery_temperature_delta_tenths_c", delta == BatterySnapshot.UNKNOWN ? JSONObject.NULL : delta)
            .put("battery_temperature_delta_c", delta == BatterySnapshot.UNKNOWN ? JSONObject.NULL : delta / 10.0);
    }

    private BatterySnapshot storedStartBattery() {
        return new BatterySnapshot(state.getLong("start_battery_sample_wall_ms", 0),
            state.getLong("start_battery_sample_elapsed_ms", 0),
            state.getInt("start_battery_temperature_tenths_c", BatterySnapshot.UNKNOWN),
            state.getInt("start_battery_level_percent", BatterySnapshot.UNKNOWN),
            state.getInt("start_battery_voltage_mv", BatterySnapshot.UNKNOWN),
            state.getInt("start_battery_status", android.os.BatteryManager.BATTERY_STATUS_UNKNOWN),
            state.getInt("start_battery_plugged", 0),
            state.getString("start_battery_read_error", "start_snapshot_missing"));
    }

    private static void merge(JSONObject target, JSONObject source) throws Exception {
        var keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            target.put(key, source.get(key));
        }
    }
    private String sessionId() { return state.getString("session_id", ""); }
    private long startedWall() { return state.getLong("started_wall_ms", 0); }
    private long startedElapsed() { return state.getLong("started_elapsed_ms", 0); }
    private long baselineTotal() { return state.getLong("baseline_total_ms", 0); }
    private int resetCount() { return state.getInt("reset_count", 0); }
    private int interruptionCount() { return state.getInt("interruption_count", 0); }
}
