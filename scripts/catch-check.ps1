# Runs the production catch-block rules (scripts/CatchCheck.java); passes its exit code through, 2 when Java is missing.
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
    [Console]::Error.WriteLine('catch-check: java was not found (set JAVA_HOME or put a JDK 21 on PATH)')
    exit 2
}

& $java (Join-Path $root 'scripts/CatchCheck.java') $root
exit $LASTEXITCODE
