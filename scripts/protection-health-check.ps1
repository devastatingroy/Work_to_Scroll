param(
    [string]$Serial = '35c2f60f',
    [ValidateSet('WorkToScroll', 'WorkToWatch')][string]$Product = 'WorkToScroll',
    [ValidateRange(1, 120)][int]$Samples = 1,
    [ValidateRange(1, 60)][int]$IntervalSeconds = 5
)
$ErrorActionPreference = 'Stop'
$productConfig = if ($Product -eq 'WorkToWatch') {
    @{ AppPackage = 'com.roy.worktowatch'; TargetPackage = 'com.google.android.youtube'; AppName = 'WorkToWatch' }
} else {
    @{ AppPackage = 'com.roy.worktoscroll.overlaytest'; TargetPackage = 'com.xingin.xhs'; AppName = 'WorkToScroll' }
}
$repairPackage = $productConfig.AppPackage
$targetPattern = [regex]::Escape($productConfig.TargetPackage)
$blockerTitlePattern = [regex]::Escape($productConfig.AppName + ' blocker')
$blockerPagePattern = [regex]::Escape($repairPackage + '/com.roy.worktoscroll.BlockerActivity')

# Read-only: does not launch/unfreeze the app, change credit, or collect page contents.
function Read-Adb([string[]]$Arguments, [switch]$Optional) {
    # An absent optional private-preference file is an expected state on older
    # installations.  Keep its stderr out of PowerShell's native-error stream
    # so ErrorActionPreference=Stop can still return $null to the caller.
    try {
        $output = & adb -s $Serial @Arguments 2>$null
        $exitCode = $LASTEXITCODE
    } catch {
        if ($Optional) { return $null }
        throw "ADB read failed: $($Arguments -join ' ')"
    }
    if ($exitCode -ne 0) {
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
    $recoveryFields = @{}
    $recoveryXml = Read-Adb @('shell', 'run-as', $repairPackage, 'cat', 'shared_prefs/boot_recovery_status.xml') -Optional
    if ($recoveryXml) {
        try {
            [xml]$recoverySnapshot = $recoveryXml
            foreach ($entry in $recoverySnapshot.map.ChildNodes) {
                $recoveryFields[$entry.name] = if ($entry.HasAttribute('value')) { $entry.value } else { $entry.InnerText }
            }
        } catch { $recoveryFields = @{} }
    }
    $bootCountText = Read-Adb @('shell', 'settings', 'get', 'global', 'boot_count') -Optional
    $currentBootCount = if ($bootCountText -match '^\d+$') { [int]$bootCountText } else { $null }
    $recoveryBootCount = if ($recoveryFields.ContainsKey('outcome_boot_count')) { [int]$recoveryFields['outcome_boot_count'] } else { $null }
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
    $targetForeground = $displays -match "mCurrentFocus=[^\r\n]*${targetPattern}/"
    $blockerListed = $windows -match "Window\{[^\r\n]*${blockerTitlePattern}"
    $blockerVisible = $false
    $blockerMatch = [regex]::Match($windows, "(?ms)^\s*Window #\d+ Window\{[^\r\n]*${blockerTitlePattern}[^\r\n]*\r?\n(?<body>.*?)(?=^\s*Window #\d+ |\z)")
    if ($blockerMatch.Success) {
        $blockerBody = $blockerMatch.Groups['body'].Value
        $blockerVisible = $blockerBody -match 'isVisible=true' -and $blockerBody -match 'Surface: shown=true'
    }
    $blockerPageFocused = $displays -match "mCurrentFocus=[^\r\n]*${blockerPagePattern}"
    $pageMatch = [regex]::Match($windows, "(?ms)^\s*Window #\d+ Window\{[^\r\n]*${blockerPagePattern}[^\r\n]*\r?\n(?<body>.*?)(?=^\s*Window #\d+ |\z)")
    $blockerPageVisible = $pageMatch.Success -and $pageMatch.Groups['body'].Value -match 'isVisible=true' -and
        $pageMatch.Groups['body'].Value -match 'Surface: shown=true'
    $ageMs = $deviceMs - [long]$fields.cursor_ms
    [ordered]@{
        time = [DateTimeOffset]::Now.ToString('o')
        product = $Product
        app_package = $repairPackage
        target_package = $productConfig.TargetPackage
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
        blocker_page_focused = $blockerPageFocused
        blocker_page_visible = $blockerPageVisible
        zero_balance_target_without_window = $fields.enabled -eq 'true' -and
            [long]$fields.balance_ms -eq 0 -and $targetForeground -and -not $blockerVisible
        observation_gaps = [long]$fields.observation_gaps
        last_observation_gap_ms = [long]$fields.last_observation_gap_ms
        boot_recovery_outcome = $recoveryFields['outcome']
        boot_recovery_boot_count = $recoveryBootCount
        current_boot_count = $currentBootCount
        boot_recovery_matches_current_boot = $null -ne $currentBootCount -and $null -ne $recoveryBootCount -and $currentBootCount -eq $recoveryBootCount
        boot_recovery_wall_ms = $recoveryFields['outcome_wall_ms']
    } | ConvertTo-Json -Compress
}
# A listed window is diagnostic evidence only; verify actual visibility and touch blocking on device.
