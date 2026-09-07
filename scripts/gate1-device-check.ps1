param([string]$Serial = '35c2f60f')
$ErrorActionPreference = 'Stop'

# On-device experiment: only launches the two apps and Home; no scrolling or content actions.
# Start with exactly 300 seconds of credit and protection enabled, granted through the visible app.
# Start unlocked. Temporarily increase auto-lock timeout and restore it in finally.
# Results are observations, not a universal Android compatibility claim.
function Invoke-Adb([string[]]$Command) {
    $result = & adb -s $Serial @Command
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Command[0])" }
    return $result
}
function Sample([string]$Label) {
    [xml]$snapshot = (Invoke-Adb @('shell','run-as','com.roy.worktoscroll','cat','shared_prefs/ledger.xml')) -join "`n"
    $fields = @{}
    foreach ($entry in $snapshot.map.ChildNodes) {
        $fields[$entry.name] = if ($entry.HasAttribute('value')) { $entry.value } else { $entry.InnerText }
    }
    $row = [ordered]@{
        label = $Label; host_time = [DateTimeOffset]::Now.ToString('o')
        balance_ms = [long]$fields.balance_ms; consumed_ms = [long]$fields.consumed_ms
        cursor_ms = [long]$fields.cursor_ms; target_active = $fields.target_active
        enabled = $fields.enabled; blocks = [long]$fields.blocks; note = $fields.note
    }
    Write-Information ($row | ConvertTo-Json -Compress) -InformationAction Continue
    return $row
}
function Assert-Unlocked {
    $policy = (Invoke-Adb @('shell','dumpsys','window','policy')) -join "`n"
    if ($policy -match 'mIsShowing=true') { throw 'Phone is locked; unlock it before continuing.' }
}
function Assert-TargetForeground {
    $activity = (Invoke-Adb @('shell','dumpsys','activity','activities')) -join "`n"
    if ($activity -notmatch 'topResumedActivity=[^\r\n]*com\.xingin\.xhs/') {
        throw 'Target app is not foreground; this segment is interrupted and cannot count as a controlled test.'
    }
}
function Assert-Fresh([long]$CursorMs) {
    $deviceSeconds = [long]((Invoke-Adb @('shell','date','+%s')) -join '').Trim()
    if ($deviceSeconds * 1000 - $CursorMs -gt 8000) {
        throw 'Ledger checkpoint is stale by over 8 seconds; inspect service health / system freeze.'
    }
}
Assert-Unlocked
$initial = Sample 'initial'
if ($initial.balance_ms -ne 300000 -or $initial.enabled -ne 'true') {
    throw 'Expected 300000 ms of visible test credit with protection enabled.'
}
$previousTimeout = ((Invoke-Adb @('shell','settings','get','system','screen_off_timeout')) -join '').Trim()
if ($previousTimeout -notmatch '^\d+$') { throw 'Cannot preserve original screen timeout.' }
Write-Information "Original screen_off_timeout=$previousTimeout; temporary test value=600000" -InformationAction Continue
try {
    Invoke-Adb @('shell','settings','put','system','screen_off_timeout','600000') | Out-Null
    Invoke-Adb @('shell','am','start','-W','-n','com.xingin.xhs/.index.v2.IndexActivityV2') | Out-Null
    foreach ($segment in 1..4) {
        Start-Sleep -Seconds 30; Assert-Unlocked; Assert-TargetForeground
        $row = Sample "first_segment_${segment}"; Assert-Fresh $row.cursor_ms
    }
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Start-Sleep -Seconds 3
    $idleStart = Sample 'idle_start'
    Start-Sleep -Seconds 20
    Assert-Unlocked
    $idleEnd = Sample 'idle_end'
    if ($idleStart.balance_ms -ne $idleEnd.balance_ms) { throw 'Balance changed while on Home.' }
    Invoke-Adb @('shell','am','start','-W','-n','com.xingin.xhs/.index.v2.IndexActivityV2') | Out-Null
    foreach ($segment in 1..7) {
        Start-Sleep -Seconds 30; Assert-Unlocked; Assert-TargetForeground
        $row = Sample "second_segment_${segment}"; Assert-Fresh $row.cursor_ms
    }
    $final = Sample 'final'
    if ($final.balance_ms -ne 0) { throw 'Balance did not exhaust; inspect event delivery.' }
    if ($final.blocks -le $initial.blocks) { throw 'No new blocker recorded.' }
    Write-Information 'PASS: 5-minute split use exhausted; Home pause preserved balance; new blocker recorded. Inspect final blocker visually.' -InformationAction Continue
} finally {
    $currentTimeout = ((Invoke-Adb @('shell','settings','get','system','screen_off_timeout')) -join '').Trim()
    if ($currentTimeout -eq '600000') {
        Invoke-Adb @('shell','settings','put','system','screen_off_timeout',$previousTimeout) | Out-Null
        Write-Information "Restored screen_off_timeout=$previousTimeout" -InformationAction Continue
    }
}
