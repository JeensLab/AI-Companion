$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskReports=Join-Path $taskRoot 'build/diagnostics'
$taskFixtures=Join-Path $taskRoot 'src/main/resources/data/aicompanion_quiet'
$taskSources=@('CompanionQuietWorkTests','CompanionSpeechTests','CompanionPlanRecoveryTests','CompanionTaskTests','CompanionFoodLightTests','CompanionEnduranceTests') | ForEach-Object { Join-Path $taskRoot "src/main/java/dev/aicompanion/gametest/$_.java" }
$taskOriginals=@{}
foreach($taskSource in $taskSources){$taskOriginals[$taskSource]=[IO.File]::ReadAllText($taskSource)}
New-Item -ItemType Directory -Path $taskReports -Force | Out-Null
if(Test-Path -LiteralPath $taskFixtures){throw 'Quiet-work fixture directory already exists'}
$taskInit=Join-Path $taskReports 'quiet-only.init.gradle'
[IO.File]::WriteAllText($taskInit,"gradle.projectsEvaluated { rootProject.neoForge.runs.gameTestServer { gameDirectory = rootProject.file('run/quiet-tests'); systemProperty 'neoforge.enabledGameTestNamespaces', 'aicompanion_quiet' } }",[Text.UTF8Encoding]::new($false))
New-Item -ItemType Directory -Path "$taskFixtures/structure" | Out-Null
Copy-Item -LiteralPath (Join-Path $taskRoot 'src/main/resources/data/aicompanion/structure/empty.nbt'),(Join-Path $taskRoot 'src/main/resources/data/aicompanion/structure/tall_tree.nbt') -Destination "$taskFixtures/structure"
Copy-Item -LiteralPath (Join-Path $taskRoot 'src/main/resources/data/aicompanion/structure/marathon_valley.nbt') -Destination "$taskFixtures/structure"
Push-Location $taskRoot
try {
    foreach($taskSource in $taskSources){
        $taskFocused=$taskOriginals[$taskSource].Replace('@GameTestHolder(AICompanionMod.MOD_ID)','@GameTestHolder("aicompanion_quiet")')
        if($taskSource.EndsWith('CompanionFoodLightTests.java') -or $taskSource.EndsWith('CompanionEnduranceTests.java')) {
            $taskFocused=[regex]::Replace($taskFocused,'@GameTest\([^\r\n]+\)(\s+public static void (\w+)\()',{param($m)if($m.Groups[2].Value -in @('failedWoodIsFollowedByHuntingAndCooking','reloadKeepsFailureBackoff')){$m.Value}else{$m.Groups[1].Value}})
        }
        [IO.File]::WriteAllText($taskSource,$taskFocused,[Text.UTF8Encoding]::new($false))
    }
    $taskLog=Join-Path $taskReports 'quiet-work.txt';$taskStarted=Get-Date
    $ErrorActionPreference='Continue'
    try { & ./gradlew.bat --offline --init-script $taskInit runGameTestServer *> $taskLog; $taskExit=$LASTEXITCODE }
    finally {$ErrorActionPreference='Stop'}
    $taskOutput=[IO.File]::ReadAllText($taskLog);$taskMatch=[regex]::Match($taskOutput,'All (\d+) required tests passed')
    $taskSummary=[ordered]@{exitCode=$taskExit;passed=if($taskMatch.Success){[int]$taskMatch.Groups[1].Value}else{0};seconds=[math]::Round(((Get-Date)-$taskStarted).TotalSeconds,2);log=$taskLog}
    [IO.File]::WriteAllText((Join-Path $taskReports 'quiet-work-summary.json'),(ConvertTo-Json -InputObject $taskSummary -Depth 5),[Text.UTF8Encoding]::new($false))
    if($taskExit -ne 0 -or !$taskMatch.Success){Get-Content -LiteralPath $taskLog -Tail 45;throw "Quiet-work verification failed: $taskLog"}
    Write-Output "Quiet work passed: $($taskMatch.Groups[1].Value)"
} finally {
    foreach($taskSource in $taskSources){[IO.File]::WriteAllText($taskSource,$taskOriginals[$taskSource],[Text.UTF8Encoding]::new($false))}
    $taskResolved=(Resolve-Path -LiteralPath $taskFixtures).Path
    if($taskResolved -ne (Join-Path $taskRoot 'src\main\resources\data\aicompanion_quiet')){throw 'Unexpected fixture cleanup target'}
    Remove-Item -LiteralPath $taskResolved -Recurse -Force
    Pop-Location
}
