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
else { $latest }

Write-Output "Recent work-session events (last $Tail):"
$events = Read-AppFile 'files/work-session-events.jsonl'
if ($events.Count -eq 0) { Write-Output '(no event journal found)' }
else { $events | Select-Object -Last $Tail }
