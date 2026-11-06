# Ratchet over the lines a change adds: length, names, TODO. See scripts/LineCheck.java.
# Usage: .\scripts\line-check.ps1 [-Base <ref>]
param([string]$Base)

$root = Split-Path -Parent $PSScriptRoot
$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $java -and $env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $java = "$env:JAVA_HOME\bin\java.exe"
}
if (-not $java) {
    [Console]::Error.WriteLine('line-check: java not found (put JDK 21 on PATH or set JAVA_HOME)')
    exit 2
}
$lineCheckArgs = @("$root\scripts\LineCheck.java")
if ($Base) { $lineCheckArgs += @('--base', $Base) }
Set-Location $root
& $java @lineCheckArgs
exit $LASTEXITCODE
