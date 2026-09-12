param(
    [string]$TargetRawDirectory = '',
    [switch]$ReminderOnly
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$cueRoot = $PSScriptRoot
$cueRaw = if ($TargetRawDirectory) { $TargetRawDirectory } else { Join-Path $cueRoot 'app\src\main\res\raw' }
$cueBuild = Join-Path $cueRoot 'build'
New-Item -ItemType Directory -Force -Path $cueRaw,$cueBuild | Out-Null
# A short 880 Hz tone with fades, stored only as a build intermediate.
$cueBeepPath = Join-Path $cueBuild 'cue-beep.wav'
$cueRate = 22050
$cueSamples = [int]($cueRate * 0.24)
$cueWriter = [IO.BinaryWriter]::new([IO.File]::Create($cueBeepPath))
try {
    $cueWriter.Write([Text.Encoding]::ASCII.GetBytes('RIFF'))
    $cueWriter.Write([int](36 + $cueSamples * 2))
    $cueWriter.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
    $cueWriter.Write([int]16); $cueWriter.Write([int16]1); $cueWriter.Write([int16]1)
    $cueWriter.Write([int]$cueRate); $cueWriter.Write([int]($cueRate * 2))
    $cueWriter.Write([int16]2); $cueWriter.Write([int16]16)
    $cueWriter.Write([Text.Encoding]::ASCII.GetBytes('data'))
    $cueWriter.Write([int]($cueSamples * 2))
    for ($cueIndex = 0; $cueIndex -lt $cueSamples; $cueIndex++) {
        $cueFade = [math]::Min(1.0, [math]::Min($cueIndex / 220.0, ($cueSamples - 1 - $cueIndex) / 220.0))
        $cueWriter.Write([int16](7500 * $cueFade * [math]::Sin(2 * [math]::PI * 880 * $cueIndex / $cueRate)))
    }
} finally { $cueWriter.Dispose() }
$cueVoice = [System.Speech.Synthesis.SpeechSynthesizer]::new()
try {
    $cueVoice.SelectVoice('Microsoft Huihui Desktop')
    $cueVoice.Rate = 0
    $cueVoice.Volume = 85
    $cueFormat = [System.Speech.AudioFormat.SpeechAudioFormatInfo]::new(22050,
        [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
        [System.Speech.AudioFormat.AudioChannel]::Mono)
    if (-not $ReminderOnly) {
        $cueVoice.SetOutputToWaveFile((Join-Path $cueRaw 'person_away.wav'), $cueFormat)
        $cuePrompt = [System.Speech.Synthesis.PromptBuilder]::new([cultureinfo]'zh-CN')
        $cuePrompt.AppendAudio($cueBeepPath)
        $cuePrompt.AppendBreak([timespan]::FromMilliseconds(80))
        $cuePrompt.AppendText('暂时离开')
        $cueVoice.Speak($cuePrompt)
        $cueVoice.SetOutputToNull()
        $cueVoice.SetOutputToWaveFile((Join-Path $cueRaw 'person_return.wav'), $cueFormat)
        $cueVoice.Speak('欢迎归来')
        $cueVoice.SetOutputToNull()
    }
    if ($ReminderOnly -or $TargetRawDirectory) {
        $cueVoice.SetOutputToWaveFile((Join-Path $cueRaw 'unrecorded_reminder.wav'), $cueFormat)
        $cueVoice.Speak('请注意，监控无效，监控无效')
        $cueVoice.SetOutputToNull()
    }
} finally { $cueVoice.Dispose() }
Get-ChildItem -LiteralPath $cueRaw -Filter '*.wav' | Get-FileHash -Algorithm SHA256
