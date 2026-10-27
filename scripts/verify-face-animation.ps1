$ErrorActionPreference='Stop'
$faceRoot=Split-Path -Parent $PSScriptRoot
$faceReports=Join-Path $faceRoot 'build/diagnostics'
$faceClient=Join-Path $faceRoot 'build/faces-client'
New-Item -ItemType Directory -Path $faceReports,$faceClient -Force | Out-Null
$faceInit=Join-Path $faceReports 'faces-client.init.gradle'
$faceOptions=Join-Path $faceClient 'options.txt'
if(!(Test-Path -LiteralPath $faceOptions)) {
    [IO.File]::WriteAllText($faceOptions,"guiScale:2`nrenderDistance:4`nsimulationDistance:4`nmaxFps:60`npauseOnLostFocus:false`nonboardAccessibility:false`nlang:ru_ru`ntutorialStep:none`nsoundCategory_music:0.0`n",[Text.UTF8Encoding]::new($false))
}
$faceInitText=@'
gradle.projectsEvaluated {
    rootProject.neoForge.runs.client {
        gameDirectory = rootProject.file('build/faces-client')
        systemProperty 'aicompanion.faceProbe', 'true'
        programArguments.addAll '--width', '1920', '--height', '1280'
    }
}
'@
[IO.File]::WriteAllText($faceInit,$faceInitText,[Text.UTF8Encoding]::new($false))
$faceLog=Join-Path $faceReports 'faces-client.txt'
Push-Location $faceRoot
try {
    $ErrorActionPreference='Continue'
    try { & ./gradlew.bat --offline --init-script $faceInit runClient *> $faceLog; $faceExit=$LASTEXITCODE }
    finally { $ErrorActionPreference='Stop' }
    if($faceExit -ne 0 -or !([IO.File]::ReadAllText($faceLog).Contains('FACE_AUDIO_PROBE_PASSED'))) {
        Get-Content -LiteralPath $faceLog -Tail 45
        throw "Face/audio verification failed: $faceLog"
    }
    Write-Output "Face/audio verified. Screenshots: $faceClient/screenshots"
} finally { Pop-Location }
