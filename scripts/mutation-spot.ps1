# Spot mutation testing; the work is in MutationSpot.java. Exit code passes through: 1 when a mutant survived, 2 on a usage error or a dirty tree.
# Usage: mutation-spot.ps1 -List | [-DryRun] -All | [-DryRun] -One <id>
param([switch]$List,[switch]$All,[switch]$DryRun,[string]$One='')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$java=if($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))){Join-Path $env:JAVA_HOME 'bin\java.exe'}else{'java'}
$javaArgs=@('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8',(Join-Path $root 'scripts\MutationSpot.java'),'--root',$root)
if($List){$javaArgs+='--list'}
if($DryRun){$javaArgs+='--dry-run'}
if($All){$javaArgs+='--all'}
if($One){$javaArgs+=@('--one',$One)}
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
Push-Location $root
try { & $java @javaArgs; $code=$LASTEXITCODE }
finally { Pop-Location }
exit $code
