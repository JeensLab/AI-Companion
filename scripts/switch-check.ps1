# Runs scripts/SwitchCheck.java from the repository root; the exit code is the check's own.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$java = $null
if ($env:JAVA_HOME) {
    foreach ($name in 'java.exe', 'java') {
        $candidate = Join-Path $env:JAVA_HOME "bin/$name"
        if (Test-Path $candidate) { $java = $candidate; break }
    }
}
if (-not $java) {
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $java = $command.Source }
}
if (-not $java) {
    [Console]::Error.WriteLine('switch-check: Java not found, set JAVA_HOME or put java on PATH')
    exit 2
}

& $java scripts/SwitchCheck.java
exit $LASTEXITCODE
