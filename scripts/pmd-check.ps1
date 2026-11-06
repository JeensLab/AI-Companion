# Complexity ceilings: runs PMD, then compares its report with tools/pmd-baseline.txt (see scripts/PmdCheck.java).
# Extra arguments go to Gradle. Exit 0 clean, 1 new violation or stale baseline entry, 2 PMD or the report failed.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
& .\gradlew.bat pmdMain --offline -q @args
if ($LASTEXITCODE -ne 0) { Write-Error 'pmd-check: pmdMain failed'; exit 2 }
java scripts/PmdCheck.java
exit $LASTEXITCODE
