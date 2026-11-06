# Package-boundary check (client imports, server code importing client/, *Tests placement); rules in scripts/ArchCheck.java.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java' } else { 'java' }
& $java scripts/ArchCheck.java @args
exit $LASTEXITCODE
