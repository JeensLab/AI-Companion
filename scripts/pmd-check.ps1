# Complexity ceilings: runs PMD, then compares its report with tools/pmd-baseline.txt (see scripts/PmdCheck.java).
# Extra arguments go to Gradle. Exit 0 clean, 1 new violation or stale baseline entry, 2 PMD, the report or Java failed.
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
    [Console]::Error.WriteLine('pmd-check: java was not found (set JAVA_HOME or put a JDK 21 on PATH)')
    exit 2
}

Set-Location $root
& .\gradlew.bat pmdMain --offline -q @args
if ($LASTEXITCODE -ne 0) { [Console]::Error.WriteLine('pmd-check: pmdMain failed'); exit 2 }
& $java scripts/PmdCheck.java
exit $LASTEXITCODE
