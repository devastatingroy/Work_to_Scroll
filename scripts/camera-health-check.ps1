param(
    [string]$Serial = '35c2f60f',
    [ValidateRange(1,60)][int]$Samples = 1,
    [ValidateRange(1,5)][int]$IntervalSeconds = 1
)
$ErrorActionPreference = 'Stop'
$experimentAdb = 'D:\AndroidSDK\platform-tools\adb.exe'
$experimentPackage = 'com.roy.worktoscroll.overlaytest'
for ($sampleIndex = 0; $sampleIndex -lt $Samples; $sampleIndex++) {
    if ($sampleIndex -gt 0) { Start-Sleep -Seconds $IntervalSeconds }
    $experimentProcess = (& $experimentAdb -s $Serial shell pidof $experimentPackage 2>$null) -join ''
    if (-not $experimentProcess.Trim()) {
        [pscustomobject]@{sample=$sampleIndex+1; live=$false; reason='experiment_not_running'} | ConvertTo-Json -Compress
        continue
    }
    $experimentText = (& $experimentAdb -s $Serial shell run-as $experimentPackage cat files/camera-status.json 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Experiment status is unavailable; no detection conclusion is possible.' }
    $experimentStatus = $experimentText | ConvertFrom-Json
    $experimentUptime = (& $experimentAdb -s $Serial shell cat /proc/uptime) -join ''
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify device time; no fresh-result claim is possible.' }
    $experimentNow = [long](1000 * [double]::Parse(($experimentUptime.Trim() -split '\s+')[0], [cultureinfo]::InvariantCulture))
    $heartbeatAge = $experimentNow - [long]$experimentStatus.heartbeat_elapsed_ms
    $captureAge = if ([long]$experimentStatus.captured_elapsed_ms -ge 0) { $experimentNow - [long]$experimentStatus.captured_elapsed_ms } else { -1 }
    $experimentLive = $experimentStatus.running -and $heartbeatAge -ge 0 -and $heartbeatAge -le 2500 -and $captureAge -ge 0 -and $captureAge -le 1500
    [pscustomobject]@{
        sample=$sampleIndex+1
        live=[bool]$experimentLive
        model_ready=$experimentStatus.model_ready
        permission=$experimentStatus.camera_permission
        running=$experimentStatus.running
        lens=$experimentStatus.lens
        capture_policy=$experimentStatus.capture_policy
        preview_resolution=$experimentStatus.preview_resolution
        analysis_width=$experimentStatus.analysis_width
        analysis_height=$experimentStatus.analysis_height
        preview_mode=$experimentStatus.preview_mode
        scope=$experimentStatus.scope
        state=if ($experimentLive) { $experimentStatus.state } else { 'NO_LIVE_DETECTION' }
        people=if ($experimentLive) { $experimentStatus.person_candidates } else { $null }
        reliable=if ($experimentLive) { $experimentStatus.reliable_people } else { $null }
        score=if ($experimentLive) { [math]::Round([double]$experimentStatus.max_score,3) } else { $null }
        observation_kind=if ($experimentLive) { $experimentStatus.observation_kind } else { 'stale_or_not_running' }
        capture_age_ms=$captureAge
        heartbeat_age_ms=$heartbeatAge
        processing_ms=$experimentStatus.processing_ms
        sequence=$experimentStatus.sequence
        generation=$experimentStatus.generation
        session=$experimentStatus.session
        detail=$experimentStatus.detail
        audio_last_cue=$experimentStatus.audio_last_cue
        audio_status=$experimentStatus.audio_status
        audio_events=$experimentStatus.audio_events
        media_volume=$experimentStatus.media_volume
    } | ConvertTo-Json -Compress
}
