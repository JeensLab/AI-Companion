# Whole-tree formatting rules: trailing whitespace, final newline, unused imports. See scripts/FormatCheck.java.
# Usage: .\scripts\format-check.ps1 [-Fix] [-SelfTest]; exit 0 clean, 1 findings, 2 tool error.
param([switch]$Fix, [switch]$SelfTest)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$java = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
} else {
    $found = Get-Command java -ErrorAction SilentlyContinue
    if ($found) { $java = $found.Source }
}
if (-not $java) {
    [Console]::Error.WriteLine('format-check: java was not found (set JAVA_HOME or put a JDK 21 on PATH)')
    exit 2
}

$formatArgs = @('scripts/FormatCheck.java')
if ($Fix) { $formatArgs += '--fix' }
if ($SelfTest) { $formatArgs += '--selftest' }
Set-Location $root
& $java @formatArgs
exit $LASTEXITCODE
