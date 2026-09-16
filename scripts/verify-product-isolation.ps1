$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$localProperties = Join-Path $projectRoot 'local.properties'
$sdkDir = $null
if (Test-Path -LiteralPath $localProperties) {
    $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($sdkLine) {
        $sdkDir = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':'
        $sdkDir = $sdkDir -replace '\\\\', '\'
    }
}
if (-not $sdkDir) { $sdkDir = $env:ANDROID_SDK_ROOT }
if (-not $sdkDir) { $sdkDir = $env:ANDROID_HOME }
if (-not $sdkDir) { throw 'Android SDK path was not found in local.properties, ANDROID_SDK_ROOT, or ANDROID_HOME.' }

$analyzer = Join-Path $sdkDir 'cmdline-tools\latest\bin\apkanalyzer.bat'
if (-not (Test-Path -LiteralPath $analyzer)) { throw "apkanalyzer was not found at $analyzer" }

$scrollApk = Join-Path $projectRoot 'app\build\outputs\apk\workToScroll\debug\app-workToScroll-debug.apk'
$watchApk = Join-Path $projectRoot 'app\build\outputs\apk\workToWatch\debug\app-workToWatch-debug.apk'
foreach ($apk in @($scrollApk, $watchApk)) {
    if (-not (Test-Path -LiteralPath $apk)) { throw "Expected APK was not built: $apk" }
}

function Invoke-ApkAnalyzer([string[]]$Arguments) {
    $output = & $analyzer @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "apkanalyzer failed: $($Arguments -join ' ')`n$($output -join "`n")" }
    return ($output -join "`n").Trim()
}

function Assert-Exact([string]$Label, [string]$Actual, [string]$Expected) {
    if ($Actual -ne $Expected) { throw "$Label mismatch. Expected '$Expected', got '$Actual'." }
}

$scrollId = Invoke-ApkAnalyzer @('manifest', 'application-id', $scrollApk)
$watchId = Invoke-ApkAnalyzer @('manifest', 'application-id', $watchApk)
$scrollManifest = Invoke-ApkAnalyzer @('manifest', 'print', $scrollApk)
$watchManifest = Invoke-ApkAnalyzer @('manifest', 'print', $watchApk)
$scrollConfig = Invoke-ApkAnalyzer @('dex', 'code', '--class', 'com.roy.worktoscroll.BuildConfig', $scrollApk)
$watchConfig = Invoke-ApkAnalyzer @('dex', 'code', '--class', 'com.roy.worktoscroll.BuildConfig', $watchApk)
$scrollService = Invoke-ApkAnalyzer @('dex', 'code', '--class', 'com.roy.worktoscroll.ProtectionService', $scrollApk)
$watchService = Invoke-ApkAnalyzer @('dex', 'code', '--class', 'com.roy.worktoscroll.ProtectionService', $watchApk)
$scrollRecovery = Invoke-ApkAnalyzer @('dex', 'code', '--class', 'com.roy.worktoscroll.ProtectionRecovery', $scrollApk)
$watchRecovery = Invoke-ApkAnalyzer @('dex', 'code', '--class', 'com.roy.worktoscroll.ProtectionRecovery', $watchApk)
$scrollName = Invoke-ApkAnalyzer @('resources', 'value', '--config', 'default', '--type', 'string', '--name', 'app_name', $scrollApk)
$watchName = Invoke-ApkAnalyzer @('resources', 'value', '--config', 'default', '--type', 'string', '--name', 'app_name', $watchApk)

Assert-Exact 'WorkToScroll application ID' $scrollId 'com.roy.worktoscroll.overlaytest'
Assert-Exact 'WorkToWatch application ID' $watchId 'com.roy.worktowatch'
Assert-Exact 'WorkToScroll application name' $scrollName 'WorkToScroll'
Assert-Exact 'WorkToWatch application name' $watchName 'WorkToWatch'

if ($scrollManifest -notmatch 'com\.xingin\.xhs') { throw 'WorkToScroll manifest does not query the Xiaohongshu package.' }
if ($scrollManifest -match 'com\.google\.android\.youtube') { throw 'WorkToScroll manifest unexpectedly queries YouTube.' }
if ($watchManifest -notmatch 'com\.google\.android\.youtube') { throw 'WorkToWatch manifest does not query the official YouTube package.' }
if ($watchManifest -match 'com\.xingin\.xhs') { throw 'WorkToWatch manifest still queries Xiaohongshu.' }

if ($scrollConfig -notmatch 'PRODUCT_NAME:Ljava/lang/String; = "WorkToScroll"') { throw 'WorkToScroll BuildConfig product name is wrong.' }
if ($scrollConfig -notmatch 'TARGET_PACKAGE:Ljava/lang/String; = "com\.xingin\.xhs"') { throw 'WorkToScroll BuildConfig target is wrong.' }
if ($watchConfig -notmatch 'PRODUCT_NAME:Ljava/lang/String; = "WorkToWatch"') { throw 'WorkToWatch BuildConfig product name is wrong.' }
if ($watchConfig -notmatch 'TARGET_PACKAGE:Ljava/lang/String; = "com\.google\.android\.youtube"') { throw 'WorkToWatch BuildConfig target is wrong.' }
if ($watchConfig -match 'com\.xingin\.xhs|"WorkToScroll"') { throw 'WorkToWatch BuildConfig contains WorkToScroll product configuration.' }
if (($scrollService + $scrollRecovery) -notmatch 'com\.roy\.worktoscroll\.overlaytest') { throw 'WorkToScroll internal actions do not use its application ID.' }
if (($watchService + $watchRecovery) -notmatch 'com\.roy\.worktowatch') { throw 'WorkToWatch internal actions do not use its application ID.' }
if (($watchService + $watchRecovery) -match 'com\.roy\.worktoscroll\.overlaytest') { throw 'WorkToWatch internal actions still use the WorkToScroll application ID.' }

Write-Output 'Product isolation verified:'
Write-Output "  WorkToScroll -> $scrollId / com.xingin.xhs"
Write-Output "  WorkToWatch  -> $watchId / com.google.android.youtube"
