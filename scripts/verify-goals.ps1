param([ValidateRange(1,20)][int]$Repetitions=3)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskSource=Join-Path $taskRoot 'src/main/java/dev/aicompanion/gametest/CompanionGoalTests.java'
$taskOriginal=[IO.File]::ReadAllText($taskSource)
$taskFixtures=Join-Path $taskRoot 'src/main/resources/data/aicompanion_goals'
$taskReports=Join-Path $taskRoot 'build/diagnostics'
$taskInit=Join-Path $taskReports 'goals-only.init.gradle'
New-Item -ItemType Directory -Path $taskReports -Force | Out-Null
if(Test-Path -LiteralPath $taskFixtures){throw 'Goal fixture directory already exists'}
[IO.File]::WriteAllText($taskInit,"gradle.projectsEvaluated { rootProject.neoForge.runs.gameTestServer { gameDirectory = rootProject.file('run/goal-tests'); systemProperty 'neoforge.enabledGameTestNamespaces', 'aicompanion_goals' } }",[Text.UTF8Encoding]::new($false))
New-Item -ItemType Directory -Path "$taskFixtures/structure" | Out-Null
Copy-Item -LiteralPath (Join-Path $taskRoot 'src/main/resources/data/aicompanion/structure/tall_tree.nbt') -Destination "$taskFixtures/structure"
$taskRuns=@()
Push-Location $taskRoot
try {
    [IO.File]::WriteAllText($taskSource,$taskOriginal.Replace('@GameTestHolder(AICompanionMod.MOD_ID)','@GameTestHolder("aicompanion_goals")'),[Text.UTF8Encoding]::new($false))
    for($taskIteration=1;$taskIteration -le $Repetitions;$taskIteration++) {
        $taskLog=Join-Path $taskReports "goals-targeted-$taskIteration.txt"
        $taskStarted=Get-Date
        $ErrorActionPreference='Continue'
        try { & ./gradlew.bat --offline --init-script $taskInit runGameTestServer *> $taskLog; $taskExit=$LASTEXITCODE }
        finally {$ErrorActionPreference='Stop'}
        $taskOutput=[IO.File]::ReadAllText($taskLog)
        $taskMatch=[regex]::Match($taskOutput,'All (\d+) required tests passed')
        $taskRuns+=[ordered]@{iteration=$taskIteration;exitCode=$taskExit;passed=if($taskMatch.Success){[int]$taskMatch.Groups[1].Value}else{0};seconds=[math]::Round(((Get-Date)-$taskStarted).TotalSeconds,2);log=$taskLog}
        [IO.File]::WriteAllText((Join-Path $taskReports 'goals-targeted-summary.json'),(ConvertTo-Json -InputObject @($taskRuns) -Depth 5),[Text.UTF8Encoding]::new($false))
        if($taskExit -ne 0 -or !$taskMatch.Success){Get-Content -LiteralPath $taskLog -Tail 45;throw "Goal verification failed: $taskLog"}
        Write-Output "Goal run $taskIteration passed: $($taskMatch.Groups[1].Value)"
    }
} finally {
    [IO.File]::WriteAllText($taskSource,$taskOriginal,[Text.UTF8Encoding]::new($false))
    $taskResolved=(Resolve-Path -LiteralPath $taskFixtures).Path
    if($taskResolved -ne (Join-Path $taskRoot 'src\main\resources\data\aicompanion_goals')){throw 'Unexpected fixture cleanup target'}
    Remove-Item -LiteralPath $taskResolved -Recurse -Force
    Pop-Location
}
