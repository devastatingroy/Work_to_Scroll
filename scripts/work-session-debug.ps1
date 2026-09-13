[CmdletBinding()]
param(
    [string]$Serial = "",
    [ValidateRange(1, 1000)][int]$Tail = 120
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.roy.worktoscroll.overlaytest'
$adbArgs = @()
if ($Serial) { $adbArgs += @('-s', $Serial) }

function Read-AppFile([string]$Path) {
    $output = & adb @adbArgs shell run-as $packageName cat $Path 2>&1
    if ($LASTEXITCODE -ne 0) { return @() }
    return @($output)
}

$devices = & adb @adbArgs get-state 2>&1
if ($LASTEXITCODE -ne 0 -or $devices -notmatch 'device') {
    throw 'No authorized Android device is connected.'
}

Write-Output 'Latest work-session summary:'
$latest = Read-AppFile 'files/work-session-latest.json'
if ($latest.Count -eq 0) { Write-Output '(no debug session has been recorded yet)' }
else {
    $latest
    try {
        $summary = ($latest -join "`n") | ConvertFrom-Json
        if ($null -ne $summary.start_battery_temperature_c -or $null -ne $summary.end_battery_temperature_c) {
            Write-Output 'Temperature endpoints:'
            [pscustomobject]@{
                start_c=$summary.start_battery_temperature_c
                end_c=$summary.end_battery_temperature_c
                delta_c=$summary.battery_temperature_delta_c
                start_power=$summary.start_battery_power_source
                end_power=$summary.end_battery_power_source
                start_level_percent=$summary.start_battery_level_percent
                end_level_percent=$summary.end_battery_level_percent
                start_error=$summary.start_battery_read_error
                end_error=$summary.end_battery_read_error
            } | ConvertTo-Json -Compress
        }
    } catch { Write-Output '(latest summary could not be parsed)' }
}

Write-Output "Recent work-session events (last $Tail):"
$events = Read-AppFile 'files/work-session-events.jsonl'
if ($events.Count -eq 0) { Write-Output '(no event journal found)' }
else {
    $eventLines = @($events | ForEach-Object { $_ -split "`r?`n" } | Where-Object { $_.Trim() })
    $parsedEvents = @($eventLines | ForEach-Object {
        try { $_ | ConvertFrom-Json } catch { }
    })
    $important = @('INTERRUPTION_START', 'INTERRUPTION_END', 'ROUND_RESET', 'CLOCK_STATE',
        'AVAILABILITY_LOST', 'AVAILABILITY_RESTORED', 'CAMERA_RELEASE',
        'UNRECORDED_REMINDER_DUE', 'UNRECORDED_REMINDER_SUPPRESSED_BUSY',
        'PAUSE_ALERT_DUE', 'PAUSE_ALERT_MUTED', 'PAUSE_ALERT_SUPPRESSED_COOLDOWN',
        'PAUSE_ALERT_REPEAT_DUE', 'PAUSE_ALERT_REPEAT_MUTED',
        'PAUSE_FLASH_STARTED', 'PAUSE_FLASH_PULSE', 'PAUSE_FLASH_COMPLETED', 'PAUSE_FLASH_CANCELLED',
        'COUNTING_RESUMED_DUE', 'COUNTING_RESUMED_MUTED',
        'AUDIO_COUNTING_RESUMED_REQUESTED', 'AUDIO_COUNTING_RESUMED_STARTED',
        'AUDIO_COUNTING_RESUMED_WAITING_RELEASE',
        'AUDIO_COUNTING_RESUMED_COMPLETED', 'AUDIO_COUNTING_RESUMED_CANCELLED',
        'AUDIO_COUNTING_RESUMED_STOPPED', 'AUDIO_COUNTING_RESUMED_SUPPRESSED', 'AUDIO_COUNTING_RESUMED_ERROR',
        'AUDIO_CUE_REQUESTED', 'AUDIO_CUE_STARTED', 'AUDIO_CUE_COMPLETED',
        'AUDIO_CUE_CANCELLED', 'AUDIO_CUE_STOPPED', 'AUDIO_CUE_ERROR',
        'AUDIO_UNRECORDED_REMINDER_REQUESTED', 'AUDIO_UNRECORDED_REMINDER_STARTED',
        'AUDIO_UNRECORDED_REMINDER_COMPLETED', 'AUDIO_UNRECORDED_REMINDER_CANCELLED',
        'AUDIO_UNRECORDED_REMINDER_STOPPED', 'AUDIO_UNRECORDED_REMINDER_SUPPRESSED',
        'AUDIO_UNRECORDED_REMINDER_ERROR',
        'AUDIO_FAILURE_STARTED', 'AUDIO_FAILURE_COMPLETED', 'AUDIO_FAILURE_SUPPRESSED',
        'AUDIO_FAILURE_ERROR', 'WORK_END_REQUESTED', 'WORK_FINISH')
    Write-Output 'Key diagnostic events:'
    $parsedEvents | Where-Object { $_.event -in $important } | Select-Object -Last $Tail | ForEach-Object {
        [pscustomobject]@{
            local_time=[DateTimeOffset]::FromUnixTimeMilliseconds([long]$_.wall_ms).ToLocalTime().ToString('yyyy-MM-dd HH:mm:ss.fff zzz')
            event=$_.event
            trigger=$_.trigger
            clock_state=$_.clock_state
            observation_kind=$_.observation_kind
            candidates=$_.person_candidates
            reliable=$_.reliable_people
            cue=$_.cue
            detail=$_.detail
            cause=$_.cause
            interruption_duration_ms=$_.interruption_duration_ms
            reminder_cycle=$_.reminder_cycle
            test_valid_ms=$_.test_valid_ms
            round_valid_ms=$_.round_valid_ms
        } | ConvertTo-Json -Compress
    }
    Write-Output 'Raw event tail:'
    $eventLines | Select-Object -Last $Tail
}
