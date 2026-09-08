#!/system/bin/sh
# ADB shell helper, identical for both APKs. No camera images are read.
# Usage: sh camera-thermal-record.sh OUTPUT MODE DURATION_SECONDS
# MODE=armed waits for unplugged, fresh monitoring; MODE=smoke only validates the logger.
out="$1"
mode="$2"
duration="$3"
case "$out" in /data/local/tmp/wts-thermal-*.txt) ;; *) exit 2 ;; esac
case "$mode:$duration" in armed:600|smoke:5) ;; *) exit 2 ;; esac
[ ! -e "$out" ] || exit 3
pkg=com.roy.worktoscroll.overlaytest
uptime_s() { cut -d. -f1 /proc/uptime; }
read_state() {
    battery=$(dumpsys battery)
    camera=$(run-as "$pkg" cat files/camera-status.json 2>/dev/null)
    now=$(uptime_s)
    heartbeat=$(printf '%s' "$camera" | sed -n 's/.*"heartbeat_elapsed_ms":\([0-9]*\).*/\1/p')
    captured=$(printf '%s' "$camera" | sed -n 's/.*"captured_elapsed_ms":\([0-9]*\).*/\1/p')
    fresh=false
    if [ -n "$heartbeat" ] && [ -n "$captured" ]; then
        if printf '%s' "$camera" | grep -q '"running":true' &&
            [ "$((now - heartbeat / 1000))" -ge 0 ] && [ "$((now - heartbeat / 1000))" -le 2 ] &&
            [ "$((now - captured / 1000))" -ge 0 ] && [ "$((now - captured / 1000))" -le 1 ]; then fresh=true; fi
    fi
    powered=false
    if printf '%s\n' "$battery" | grep -q 'powered: true'; then powered=true; fi
}
sample() {
    {
        printf '\nSAMPLE epoch_s=%s elapsed_s=%s powered=%s fresh_at_sample=%s\n' "$(date +%s)" "$((now - started))" "$powered" "$fresh"
        printf '%s\n' "$battery" | grep -E 'powered:|status:|level:|scale:|temperature:'
        printf 'camera_status=%s\n' "$camera"
    } >> "$out"
}
printf 'mode=%s duration_s=%s source=BatteryService temperature_unit=0.1C\n' "$mode" "$duration" > "$out"
dumpsys package "$pkg" | grep -E 'versionCode=|versionName=' >> "$out"
wait_started=$(uptime_s)
while :; do
    read_state
    if [ "$mode" = smoke ] || { [ "$powered" = false ] && [ "$fresh" = true ]; }; then break; fi
    if [ "$((now - wait_started))" -ge 1200 ]; then printf 'ABORT start_timeout\n' >> "$out"; exit 4; fi
    sleep 2
done
started=$now
sample
if [ "$mode" = smoke ]; then interval=5; else interval=60; fi
next=$interval
while [ "$next" -le "$duration" ]; do
    now=$(uptime_s)
    delay=$((started + next - now))
    if [ "$delay" -gt 0 ]; then sleep "$delay"; fi
    read_state
    sample
    next=$((next + interval))
done
printf '\nCOMPLETE actual_duration_s=%s\n' "$((now - started))" >> "$out"
