# Anti-tautology check for @GameTest methods; the work is in TestQuality.java. Usage: test-quality.ps1 [-SelfTest]
# Exit code passes through: 1 a test without a reachable assertion, 2 a usage or I/O error.
param([switch]$SelfTest)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$java=if($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))){Join-Path $env:JAVA_HOME 'bin\java.exe'}else{'java'}
$target=if($SelfTest){'--selftest'}else{$root}
Push-Location $root
try { & $java '-Dfile.encoding=UTF-8' (Join-Path $root 'scripts\TestQuality.java') $target; $code=$LASTEXITCODE }
finally { Pop-Location }
exit $code
