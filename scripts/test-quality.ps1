# Anti-tautology check for @GameTest methods; the work is in TestQuality.java. Exit code passes through.
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$java=if($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))){Join-Path $env:JAVA_HOME 'bin\java.exe'}else{'java'}
Push-Location $root
try { & $java (Join-Path $root 'scripts\TestQuality.java') $root; $code=$LASTEXITCODE }
finally { Pop-Location }
exit $code
