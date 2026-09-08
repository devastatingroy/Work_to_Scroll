param(
    [string]$Serial = '35c2f60f',
    [ValidateRange(1, 120)][int]$Samples = 1,
    [ValidateRange(1, 60)][int]$IntervalSeconds = 5
)
$ErrorActionPreference = 'Stop'
$repairPackage = 'com.roy.worktoscroll.overlaytest'

# Read-only: does not launch/unfreeze the app, change credit, or collect page contents.
function Read-Adb([string[]]$Arguments, [switch]$Optional) {
    $output = & adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        if ($Optional) { return $null }
        throw "ADB read failed: $($Arguments -join ' ')"
    }
    return ($output -join "`n").Trim()
}
for ($sampleIndex = 0; $sampleIndex -lt $Samples; $sampleIndex++) {
    if ($sampleIndex -gt 0) { Start-Sleep -Seconds $IntervalSeconds }
    [xml]$snapshot = Read-Adb @('shell', 'run-as', $repairPackage, 'cat', 'shared_prefs/ledger.xml')
    $fields = @{}
    foreach ($entry in $snapshot.map.ChildNodes) {
        $fields[$entry.name] = if ($entry.HasAttribute('value')) { $entry.value } else { $entry.InnerText }
    }
    $deviceMs = 1000 * [long](Read-Adb @('shell', 'date', '+%s'))
    $processIdText = Read-Adb @('shell', 'pidof', $repairPackage) -Optional
    $frozen = $null
    $waitChannel = $null
    if ($processIdText -match '^\d+$') {
        $groups = Read-Adb @('shell', 'cat', "/proc/$processIdText/cgroup") -Optional
        if ($groups -match '(?m)^0::(/[^\r\n]+)') {
            $groupPath = $Matches[1]
            if ($groupPath -match '^/[a-zA-Z0-9_/-]+$') {
                $freezeValue = Read-Adb @('shell', 'cat', "/sys/fs/cgroup$groupPath/cgroup.freeze") -Optional
                if ($freezeValue -match '^[01]$') { $frozen = $freezeValue -eq '1' }
            }
        }
        $waitChannel = Read-Adb @('shell', 'run-as', $repairPackage, 'cat', "/proc/$processIdText/wchan") -Optional
    }
    $displays = Read-Adb @('shell', 'dumpsys', 'window', 'displays')
    $windows = Read-Adb @('shell', 'dumpsys', 'window', 'windows')
    $targetForeground = $displays -match 'mCurrentFocus=[^\r\n]*com\.xingin\.xhs/'
    $blockerListed = $windows -match 'Window\{[^\r\n]*WorkToScroll blocker'
    $blockerVisible = $false
    $blockerMatch = [regex]::Match($windows, '(?ms)^\s*Window #\d+ Window\{[^\r\n]*WorkToScroll blocker[^\r\n]*\r?\n(?<body>.*?)(?=^\s*Window #\d+ |\z)')
    if ($blockerMatch.Success) {
        $blockerBody = $blockerMatch.Groups['body'].Value
        $blockerVisible = $blockerBody -match 'isVisible=true' -and $blockerBody -match 'Surface: shown=true'
    }
    $ageMs = $deviceMs - [long]$fields.cursor_ms
    [ordered]@{
        time = [DateTimeOffset]::Now.ToString('o')
        balance_ms = [long]$fields.balance_ms
        consumed_ms = [long]$fields.consumed_ms
        enabled = $fields.enabled -eq 'true'
        checkpoint_age_ms = $ageMs
        checkpoint_stale = $ageMs -gt 8000 -or $ageMs -lt -2000
        process_id = $processIdText
        cgroup_frozen = $frozen
        wait_channel = $waitChannel
        target_foreground = $targetForeground
        recorded_target_active = $fields.target_active -eq 'true'
        blocker_window_listed = $blockerListed
        blocker_window_visible = $blockerVisible
        zero_balance_target_without_window = $fields.enabled -eq 'true' -and
            [long]$fields.balance_ms -eq 0 -and $targetForeground -and -not $blockerVisible
        observation_gaps = [long]$fields.observation_gaps
        last_observation_gap_ms = [long]$fields.last_observation_gap_ms
    } | ConvertTo-Json -Compress
}
# A listed window is diagnostic evidence only; verify actual visibility and touch blocking on device.
