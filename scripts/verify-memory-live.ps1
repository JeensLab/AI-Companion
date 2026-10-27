param([string]$ConfigPath,[switch]$AllowPaidRequests)
$ErrorActionPreference='Stop'
if(!$AllowPaidRequests){
    Write-Output 'SKIP: memory-live makes paid OpenRouter requests; pass -AllowPaidRequests explicitly to run it'
    exit 0
}
$projectRoot=Split-Path -Parent $PSScriptRoot
if(!$ConfigPath){$ConfigPath=Join-Path $projectRoot 'run\config\aicompanion-client.toml'}
$namespace='aicompanion_memlive'
$keyVariable='AICOMPANION_LIVE_KEY'
$modelVariable='AICOMPANION_LIVE_MODEL'

# Reads one string field of the mod's CLIENT config by name; the value only ever lives in the returned string.
function Read-TomlString([string]$Path,[string]$Field) {
    foreach($line in [IO.File]::ReadAllLines($Path,[Text.Encoding]::UTF8)){
        $m=[regex]::Match($line,'^\s*'+[regex]::Escape($Field)+'\s*=\s*(?:"((?:[^"\\]|\\.)*)"|''([^'']*)'')')
        if($m.Success){
            if($m.Groups[2].Success){return $m.Groups[2].Value}
            return [regex]::Replace($m.Groups[1].Value,'\\(.)','$1')
        }
    }
    return ''
}

$key=''
if(Test-Path -LiteralPath $ConfigPath -PathType Leaf){$key=(Read-TomlString $ConfigPath 'apiKey').Trim()}
if(!$key){
    Write-Output "SKIP: no OpenRouter key in $ConfigPath (field apiKey)"
    exit 0
}
$model=(Read-TomlString $ConfigPath 'model').Trim()

$utf8=[Text.UTF8Encoding]::new($false)
$reports=Join-Path $projectRoot 'build\diagnostics'
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$fixturePath=Join-Path $projectRoot "src\main\resources\data\$namespace"
if(Test-Path -LiteralPath $fixturePath){throw 'Temporary fixtures already exist'}
$init=Join-Path $reports 'memlive-only.init.gradle'
[IO.File]::WriteAllText($init,"gradle.projectsEvaluated { rootProject.neoForge.runs.gameTestServer { systemProperty 'neoforge.enabledGameTestNamespaces', '$namespace' } }",$utf8)
$log=Join-Path $reports 'memory-live.txt'
New-Item -ItemType Directory -Path "$fixturePath\structure" | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'src\main\resources\data\aicompanion\structure\empty.nbt') -Destination "$fixturePath\structure"

Push-Location $projectRoot
try {
    $env:AICOMPANION_LIVE_KEY=$key
    if($model){$env:AICOMPANION_LIVE_MODEL=$model}
    $ErrorActionPreference='Continue'
    try { & .\gradlew.bat --init-script $init runGameTestServer *> $log; $exit=$LASTEXITCODE } finally { $ErrorActionPreference='Stop' }
} finally {
    Remove-Item "Env:$keyVariable" -ErrorAction SilentlyContinue
    Remove-Item "Env:$modelVariable" -ErrorAction SilentlyContinue
    $resolved=(Resolve-Path -LiteralPath $fixturePath).Path
    if($resolved -ne (Join-Path $projectRoot "src\main\resources\data\$namespace")){throw 'Unexpected cleanup path'}
    Remove-Item -LiteralPath $resolved -Recurse -Force
    Pop-Location
}

$lines=@(Get-Content -LiteralPath $log)
$leaked=@($lines | Where-Object { $_.Contains($key) }).Count -gt 0
$key=$null
if($leaked){
    Remove-Item -LiteralPath $log -Force
    Write-Output 'FAIL memory-live: the key appeared in the run output; the log was deleted'
    exit 1
}
$text=$lines -join "`n"
$m=[regex]::Match($text,'All (\d+) required tests passed')
if($exit -eq 0 -and $m.Success -and [int]$m.Groups[1].Value -eq 3){
    $lines | Where-Object { $_ -match 'memlive dialog' } | ForEach-Object { $_.Substring($_.IndexOf('memlive')) }
    Write-Output 'PASS memory-live: 3/3 recorded dialogs condensed, planted secrets rejected'
    exit 0
}
$lines | Where-Object { $_ -match 'dialog \d: |failed at|required tests failed|error:|What went wrong' } | Select-Object -First 20 | ForEach-Object { $_.Substring(0,[Math]::Min(300,$_.Length)) }
$got=if($m.Success){"$($m.Groups[1].Value) passed"}else{'not all passed'}
Write-Output "FAIL memory-live: expected All 3 required tests passed, $got (exit $exit, see $log)"
exit 1
