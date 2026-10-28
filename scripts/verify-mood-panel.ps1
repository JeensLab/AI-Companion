$ErrorActionPreference='Stop'
$moodRoot=Split-Path -Parent $PSScriptRoot
$moodReports=Join-Path $moodRoot 'build/diagnostics'
$moodClient=Join-Path $moodRoot 'build/mood-client'
New-Item -ItemType Directory -Path $moodReports,$moodClient -Force | Out-Null
$moodInit=Join-Path $moodReports 'mood-client.init.gradle'
$moodOptions=Join-Path $moodClient 'options.txt'
if(!(Test-Path -LiteralPath $moodOptions)) {
    [IO.File]::WriteAllText($moodOptions,"guiScale:3`nrenderDistance:4`nsimulationDistance:4`nmaxFps:30`npauseOnLostFocus:false`nonboardAccessibility:false`nlang:ru_ru`ntutorialStep:none`n",[Text.UTF8Encoding]::new($false))
}
$moodInitText=@'
gradle.projectsEvaluated {
    rootProject.neoForge.runs.client {
        gameDirectory = rootProject.file('build/mood-client')
        systemProperty 'aicompanion.moodProbe', 'true'
        programArguments.addAll '--width', '1536', '--height', '1000'
    }
}
'@
[IO.File]::WriteAllText($moodInit,$moodInitText,[Text.UTF8Encoding]::new($false))
$moodLog=Join-Path $moodReports 'mood-client.txt'
Push-Location $moodRoot
try {
    $ErrorActionPreference='Continue'
    try { & ./gradlew.bat --offline --init-script $moodInit runClient *> $moodLog; $moodExit=$LASTEXITCODE }
    finally { $ErrorActionPreference='Stop' }
    if($moodExit -ne 0 -or !([IO.File]::ReadAllText($moodLog).Contains('MOOD_PANEL_PROBE_PASSED'))) {
        Get-Content -LiteralPath $moodLog -Tail 40
        throw "Mood panel verification failed: $moodLog"
    }
    Write-Output "Mood panel verified. Screenshots: $moodClient/screenshots"
} finally { Pop-Location }
