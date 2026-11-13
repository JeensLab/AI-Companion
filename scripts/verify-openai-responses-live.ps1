param([string]$ConfigPath, [switch]$AllowPaidRequests)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
if (!$AllowPaidRequests) {
    Write-Output 'SKIP: this opens a disposable Minecraft client and makes 6 paid OpenAI requests; pass -AllowPaidRequests explicitly'
    exit 0
}
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$ConfigPath) { $ConfigPath = Join-Path $projectRoot 'run\config\aicompanion-client.toml' }
$match = [regex]::Match([IO.File]::ReadAllText($ConfigPath), '(?m)^\s*apiKey\s*=\s*"([^"\r\n]+)"')
if (!$match.Success -or !$match.Groups[1].Value.StartsWith('sk-') -or $match.Groups[1].Value.StartsWith('sk-or-')) {
    throw 'A direct OpenAI key is required in the local Minecraft config'
}
$key = $match.Groups[1].Value
$directory = Join-Path $projectRoot 'build\responses-client'
$config = Join-Path $directory 'config\aicompanion-client.toml'
$marker = Join-Path $directory 'allow-paid-responses-probe'
$log = Join-Path $projectRoot 'build\responses-client-live.log'
if (Test-Path -LiteralPath $config) { throw 'Probe config already exists; finish the previous probe first' }
$utf8 = [Text.UTF8Encoding]::new($false)
New-Item -ItemType Directory -Path (Split-Path -Parent $config) -Force | Out-Null
# The key stays in a temporary local Minecraft config, never in command arguments, environment or output.
[IO.File]::WriteAllText($config, "apiKey = `"$key`"`napiUrl = `"https://api.openai.com/v1`"`nmodel = `"gpt-6-luna`"`nautonomousSpeech = false`nwindowsVoice = false`nmacosVoice = false`nlongTermMemory = false`n", $utf8)
[IO.File]::WriteAllText($marker, 'Explicitly authorized paid Responses integration probe', $utf8)
[IO.File]::WriteAllText((Join-Path $directory 'options.txt'), "onboardAccessibility:false`nlang:ru_ru`nrenderDistance:6`nsimulationDistance:5`npauseOnLostFocus:false`n", $utf8)
Push-Location $projectRoot
try {
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat runResponsesClientProbe --console=plain *> $log
    $code = $LASTEXITCODE
} finally {
    $ErrorActionPreference = 'Stop'
    Pop-Location
    # NightConfig may also create .bak files while correcting missing defaults.
    Get-ChildItem -LiteralPath (Split-Path -Parent $config) -Filter 'aicompanion-client.toml*' -File |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    if (Test-Path -LiteralPath $marker) { Remove-Item -LiteralPath $marker -Force }
}
$output = [IO.File]::ReadAllText($log)
if ($output.Contains($key)) {
    Remove-Item -LiteralPath $log -Force
    throw 'FAIL: secret appeared in the output; log removed'
}
$key = $null
$output -split "`n" | Where-Object { $_ -match 'RESPONSES_CLIENT_(MODEL_PASSED|PROBE_)|BUILD SUCCESSFUL|BUILD FAILED' } | ForEach-Object { Write-Output $_.TrimEnd() }
if ($code -ne 0 -or !$output.Contains('RESPONSES_CLIENT_PROBE_PASSED: 2 models, 6 requests')) {
    throw 'FAIL: live Responses verification did not finish; inspect the private build/responses-client-live.log'
}
exit 0
