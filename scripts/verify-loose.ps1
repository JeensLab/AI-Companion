param([ValidateRange(1,20)][int]$Repetitions=1,[string[]]$Classes=@())
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskClasses=@('CompanionLooseTests')+@($Classes | ForEach-Object {$_ -split ','} | ForEach-Object {$_.Trim()} | Where-Object {$_})
foreach($taskClass in $taskClasses){if($taskClass -notmatch '^[A-Za-z0-9_]+$' -or !(Test-Path -LiteralPath (Join-Path $PSScriptRoot "../src/main/java/dev/aicompanion/gametest/$taskClass.java"))){throw "Unknown gametest class: $taskClass"}}
$taskSources=@($taskClasses | Select-Object -Unique | ForEach-Object {"$_.java"}) | ForEach-Object {Join-Path $taskRoot "src/main/java/dev/aicompanion/gametest/$_"}
$taskOriginals=@{};foreach($taskSource in $taskSources){$taskOriginals[$taskSource]=[IO.File]::ReadAllText($taskSource)}
$taskFixtures=Join-Path $taskRoot 'src/main/resources/data/aicompanion_loose'
$taskReports=Join-Path $taskRoot 'build/diagnostics'
$taskInit=Join-Path $taskReports 'loose-only.init.gradle'
New-Item -ItemType Directory -Path $taskReports -Force | Out-Null
if(Test-Path -LiteralPath $taskFixtures){throw 'Loose fixture directory already exists'}
[IO.File]::WriteAllText($taskInit,"gradle.projectsEvaluated { rootProject.neoForge.runs.gameTestServer { gameDirectory = rootProject.file('run/loose-tests'); systemProperty 'neoforge.enabledGameTestNamespaces', 'aicompanion_loose' } }",[Text.UTF8Encoding]::new($false))
New-Item -ItemType Directory -Path "$taskFixtures/structure" | Out-Null
$taskTemplates=@('tall_tree')+@($taskSources | ForEach-Object {[regex]::Matches([IO.File]::ReadAllText($_),'template\s*=\s*"([^"]+)"') | ForEach-Object {$_.Groups[1].Value}}) | Select-Object -Unique
foreach($taskTemplate in $taskTemplates){$taskStructure=Join-Path $taskRoot "src/main/resources/data/aicompanion/structure/$taskTemplate.nbt";if(Test-Path -LiteralPath $taskStructure){Copy-Item -LiteralPath $taskStructure -Destination "$taskFixtures/structure"}}
$taskRuns=@()
Push-Location $taskRoot
try {
    foreach($taskSource in $taskSources){[IO.File]::WriteAllText($taskSource,$taskOriginals[$taskSource].Replace('@GameTestHolder(AICompanionMod.MOD_ID)','@GameTestHolder("aicompanion_loose")'),[Text.UTF8Encoding]::new($false))}
    for($taskIteration=1;$taskIteration -le $Repetitions;$taskIteration++) {
        $taskLog=Join-Path $taskReports "loose-targeted-$taskIteration.txt"
        $taskStarted=Get-Date
        $ErrorActionPreference='Continue'
        try { & ./gradlew.bat --offline --init-script $taskInit runGameTestServer *> $taskLog; $taskExit=$LASTEXITCODE }
        finally {$ErrorActionPreference='Stop'}
        $taskOutput=[IO.File]::ReadAllText($taskLog)
        $taskMatch=[regex]::Match($taskOutput,'All (\d+) required tests passed')
        $taskRuns+=[ordered]@{iteration=$taskIteration;exitCode=$taskExit;passed=if($taskMatch.Success){[int]$taskMatch.Groups[1].Value}else{0};seconds=[math]::Round(((Get-Date)-$taskStarted).TotalSeconds,2);log=$taskLog}
        [IO.File]::WriteAllText((Join-Path $taskReports 'loose-targeted-summary.json'),(ConvertTo-Json -InputObject @($taskRuns) -Depth 5),[Text.UTF8Encoding]::new($false))
        if($taskExit -ne 0 -or !$taskMatch.Success){Get-Content -LiteralPath $taskLog -Tail 45;throw "Loose verification failed: $taskLog"}
        Write-Output "Loose run $taskIteration passed: $($taskMatch.Groups[1].Value)"
    }
} finally {
    foreach($taskSource in $taskSources){[IO.File]::WriteAllText($taskSource,$taskOriginals[$taskSource],[Text.UTF8Encoding]::new($false))}
    $taskResolved=(Resolve-Path -LiteralPath $taskFixtures).Path
    if($taskResolved -ne (Join-Path $taskRoot 'src\main\resources\data\aicompanion_loose')){throw 'Unexpected fixture cleanup target'}
    Remove-Item -LiteralPath $taskResolved -Recurse -Force
    Pop-Location
}
