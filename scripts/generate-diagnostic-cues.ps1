# Windows PowerShell / local installed voice only; generated WAVs are bundled, with no phone TTS dependency.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$voice = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voice.SelectVoice('Microsoft Huihui Desktop')
$voice.Rate = 0
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(22050, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$rawDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'app/src/main/res/raw'
$phrases = @{
    'diagnostic_start.wav' = -join ([char[]]@(0x5F00,0x59CB,0x91C7,0x6837))
    'diagnostic_end.wav' = -join ([char[]]@(0x672C,0x7EC4,0x91C7,0x6837,0x7ED3,0x675F))
}
try {
    foreach ($name in $phrases.Keys) {
        $voice.SetOutputToWaveFile((Join-Path $rawDir $name), $format)
        $voice.Speak($phrases[$name])
        $voice.SetOutputToNull()
    }
} finally { $voice.Dispose() }
