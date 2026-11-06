# Size ceilings (file, method, parameters) against tools/code-shape-baseline.txt; see scripts/CodeShape.java.
param([Parameter(ValueFromRemainingArguments)][string[]]$CodeShapeArgs)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot

$javaExe=$null
if($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))){ $javaExe=Join-Path $env:JAVA_HOME 'bin\java.exe' }
elseif(Get-Command java -ErrorAction SilentlyContinue){ $javaExe=(Get-Command java).Source }
else { Write-Error 'java not found: set JAVA_HOME to a JDK 21 or put java on PATH'; exit 2 }

Push-Location $projectRoot
try { & $javaExe scripts/CodeShape.java @CodeShapeArgs; exit $LASTEXITCODE } finally { Pop-Location }
