$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskReports=Join-Path $taskRoot 'build/diagnostics'
$taskClient=Join-Path $taskRoot 'build/tasks-client'
New-Item -ItemType Directory -Path $taskReports,$taskClient -Force | Out-Null
$taskInit=Join-Path $taskReports 'tasks-client.init.gradle'
$taskOptions=Join-Path $taskClient 'options.txt'
if(!(Test-Path -LiteralPath $taskOptions)) {
    [IO.File]::WriteAllText($taskOptions,"guiScale:3`nrenderDistance:4`nsimulationDistance:4`nmaxFps:30`npauseOnLostFocus:false`nonboardAccessibility:false`nlang:ru_ru`ntutorialStep:none`n",[Text.UTF8Encoding]::new($false))
}
$taskInitText=@'
gradle.projectsEvaluated {
    rootProject.neoForge.runs.client {
        gameDirectory = rootProject.file('build/tasks-client')
        systemProperty 'aicompanion.tasksProbe', 'true'
        programArguments.addAll '--width', '1536', '--height', '1000'
    }
}
'@
[IO.File]::WriteAllText($taskInit,$taskInitText,[Text.UTF8Encoding]::new($false))
$taskLog=Join-Path $taskReports 'tasks-client-final.txt'
Push-Location $taskRoot
try {
    $ErrorActionPreference='Continue'
    try { & ./gradlew.bat --offline --init-script $taskInit runClient *> $taskLog; $taskExit=$LASTEXITCODE }
    finally { $ErrorActionPreference='Stop' }
    if($taskExit -ne 0 -or !([IO.File]::ReadAllText($taskLog).Contains('TASKS_UI_PROBE_PASSED'))) {
        Get-Content -LiteralPath $taskLog -Tail 40
        throw "UI verification failed: $taskLog"
    }
    Write-Output "Task UI verified. Screenshots: $taskClient/screenshots"
} finally { Pop-Location }
