param(
    [Parameter(Mandatory)][ValidateSet('Start','List','Status','Export')][string]$Action,
    [ValidateSet('A','B','C')][string]$Group = 'A',
    [string]$SessionId,
    [string]$Serial = '35c2f60f'
)
$ErrorActionPreference = 'Stop'
$diagAdb = 'D:/AndroidSDK/platform-tools/adb.exe'
$diagPackage = 'com.roy.worktoscroll.overlaytest'
function Read-DeviceText([string]$Path) {
    $value = (& $diagAdb -s $Serial shell run-as $diagPackage cat $Path 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Cannot read $Path : $value" }
    return $value
}
if ($Action -eq 'List') {
    $ids = & $diagAdb -s $Serial shell run-as $diagPackage ls no_backup/presence-diagnostic 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Sampling directory is unavailable; no conclusion about completed groups.' }
    $rows = foreach ($id in $ids) {
        $id = "$id".Trim()
        if ($id -notmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$') { continue }
        try {
            $metadata = (Read-DeviceText "no_backup/presence-diagnostic/$id/metadata.json") | ConvertFrom-Json
            $state = (Read-DeviceText "no_backup/presence-diagnostic/$id/status.json") | ConvertFrom-Json
            [pscustomobject]@{id=$id;group=$metadata.group;version=$metadata.version;trigger=$metadata.trigger;
                source=$metadata.source;status=$state.status;wall_ms=$metadata.wall_ms;images=$state.images_saved}
        } catch { [pscustomobject]@{id=$id;status='unreadable_or_unfinished';error=$_.Exception.Message} }
    }
    ConvertTo-Json -InputObject @($rows) -Depth 5
    return
}
if ($Action -eq 'Start') {
    $camera = (Read-DeviceText 'files/camera-status.json') | ConvertFrom-Json
    $uptime = (& $diagAdb -s $Serial shell cat /proc/uptime) -join ''
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify current device time.' }
    $now = [long](1000 * [double]::Parse(($uptime.Trim() -split '\s+')[0], [cultureinfo]::InvariantCulture))
    $age = $now - [long]$camera.heartbeat_elapsed_ms
    if (-not $camera.running -or -not $camera.model_ready -or $age -lt 0 -or $age -gt 2500) {
        throw 'Monitoring is not currently ready. This script will not open the camera or resume work.'
    }
    $SessionId = [guid]::NewGuid().ToString()
    $reply = (& $diagAdb -s $Serial shell am broadcast -a "$diagPackage.PRESENCE_DIAGNOSTIC" -p $diagPackage `
        --es camera_session $camera.session --es request_id $SessionId --es group $Group 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $reply -notmatch 'data="accepted"') { throw "Sampling was not accepted: $reply" }
    [pscustomobject]@{id=$SessionId;group=$Group;status='accepted'} | ConvertTo-Json -Compress
    return
}
if ($SessionId -notmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$') {
    throw 'A valid sampling session ID is required; no broad export is allowed.'
}
$diagStatus = (Read-DeviceText "no_backup/presence-diagnostic/$SessionId/status.json") | ConvertFrom-Json
if ($Action -eq 'Status') { $diagStatus | ConvertTo-Json -Depth 8; return }
if ($diagStatus.status -notin @('complete','incomplete')) { throw 'Sampling/writing has not finished; do not export yet.' }
$repoRoot = Split-Path -Parent $PSScriptRoot
$exportRoot = Join-Path $repoRoot "tasks/active/evidence/presence-$SessionId"
if (Test-Path -LiteralPath $exportRoot) { throw 'Export already exists; refusing to overwrite evidence.' }
New-Item -ItemType Directory -Path $exportRoot | Out-Null
$archivePath = Join-Path $exportRoot 'device-evidence.tar'
$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $diagAdb
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
foreach ($argument in @('-s',$Serial,'exec-out','run-as',$diagPackage,'tar','-cf','-','-C','no_backup/presence-diagnostic',$SessionId)) {
    $startInfo.ArgumentList.Add($argument)
}
$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$null = $process.Start()
$errorTask = $process.StandardError.ReadToEndAsync()
$outputFile = [IO.File]::Open($archivePath,[IO.FileMode]::CreateNew)
try { $process.StandardOutput.BaseStream.CopyTo($outputFile) } finally { $outputFile.Dispose() }
$process.WaitForExit()
if ($process.ExitCode -ne 0) { throw "Evidence export failed: $($errorTask.Result)" }
& tar -xf $archivePath -C $exportRoot
if ($LASTEXITCODE -ne 0) { throw 'Evidence extraction failed; original archive retained.' }
$sessionRoot = Join-Path $exportRoot $SessionId
$hashRows = @()
foreach ($inputRecord in Get-ChildItem -LiteralPath $sessionRoot -Filter '*-input.json') {
    $record = Get-Content -LiteralPath $inputRecord.FullName -Raw | ConvertFrom-Json
    if ($record.image_status -eq 'saved') {
        if ($record.image -notmatch '^frame-\d{2}\.png$') { throw 'Unexpected image filename in evidence.' }
        $actual = (Get-FileHash -LiteralPath (Join-Path $sessionRoot $record.image) -Algorithm SHA256).Hash
        if ($actual.ToLowerInvariant() -ne $record.image_sha256) { throw "Image hash mismatch: $($record.image)" }
    }
}
foreach ($file in Get-ChildItem -LiteralPath $sessionRoot -File) {
    $hashRows += [pscustomobject]@{file=$file.Name;bytes=$file.Length;sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash}
}
$manifest = [pscustomobject]@{session=$SessionId;serial=$Serial;exported_utc=[datetime]::UtcNow.ToString('o');status=$diagStatus;files=$hashRows}
[IO.File]::WriteAllText((Join-Path $exportRoot 'export-manifest.json'),($manifest | ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
[pscustomobject]@{status=$diagStatus.status;directory=$exportRoot;files=$hashRows.Count;image_hashes_verified=$true;phone_originals_retained=$true} | ConvertTo-Json
