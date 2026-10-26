param([ValidateRange(1,20)][int]$Repetitions=10,[switch]$CriticalOnly,[ValidateSet('All','Forest','Cave','Reload')][string]$Arena='All')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$namespace='aicompanion_endurance'
$source=Join-Path $projectRoot 'src\main\java\dev\aicompanion\gametest\CompanionEnduranceTests.java'
$original=[IO.File]::ReadAllText($source)
$fixtures=Join-Path $projectRoot "src\main\resources\data\$namespace"
$reports=Join-Path $projectRoot 'build\diagnostics'
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$init=Join-Path $reports 'endurance-only.init.gradle'
[IO.File]::WriteAllText($init,"gradle.projectsEvaluated { rootProject.neoForge.runs.gameTestServer { systemProperty 'neoforge.enabledGameTestNamespaces', '$namespace' } }",[Text.UTF8Encoding]::new($false))
if(Test-Path -LiteralPath $fixtures){throw 'Temporary fixtures already exist'}
New-Item -ItemType Directory -Path "$fixtures\structure" | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'src\main\resources\data\aicompanion\structure\marathon_valley.nbt') -Destination "$fixtures\structure"
$runs=@()
Push-Location $projectRoot
try {
    $focused=$original.Replace('@GameTestHolder(AICompanionMod.MOD_ID)',"@GameTestHolder(`"$namespace`")")
    if($CriticalOnly){$focused=[regex]::Replace($focused,'@GameTest\(template="marathon_valley",batch="endurance",timeoutTicks=(123|93|103)\*MINUTE\)','')}
    if($Arena -ne 'All'){
        if($CriticalOnly){throw 'Choose a long arena or CriticalOnly'}
        $method=@{Forest='twoHoursForestToolsStorageAndReturningPlayer';Cave='ninetyMinutesCaveFoodOreLightAndChangingPassage';Reload='hundredMinutesFullBagOrdersCancellationAndReloads'}[$Arena]
        $focused=[regex]::Replace($focused,'@GameTest\([^\r\n]+\)(\s+public static void (\w+)\()',{param($m)if($m.Groups[2].Value -eq $method){$m.Value}else{$m.Groups[1].Value}})
    }
    [IO.File]::WriteAllText($source,$focused,[Text.UTF8Encoding]::new($false))
    for($i=1;$i -le $Repetitions;$i++) {
        $started=Get-Date
        $log=Join-Path $reports "endurance-targeted-$i.txt"
        $ErrorActionPreference='Continue'
        try { & .\gradlew.bat --init-script $init runGameTestServer *> $log; $testExit=$LASTEXITCODE }
        finally {$ErrorActionPreference='Stop'}
        $output=Get-Content -LiteralPath $log -Raw
        $match=[regex]::Match($output,'All (\d+) required tests passed')
        $destination=Join-Path $reports "endurance-run-$i"
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        if(Test-Path -LiteralPath 'run/endurance'){
            Get-ChildItem -LiteralPath 'run/endurance' -Filter '*.json' | Where-Object { !$CriticalOnly -or $_.Name -eq 'interrupted-disk-reload.json' } | Copy-Item -Destination $destination
        }
        $failed=@([regex]::Matches($output,'LogTestReporter[^\r\n]*?\]: ([^\r\n]+) failed at') | ForEach-Object {$_.Groups[1].Value})
        $runs+=[ordered]@{iteration=$i;passed=if($match.Success){[int]$match.Groups[1].Value}else{0};exitCode=$testExit;failed=$failed;elapsedSeconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2);log=$log;metrics=$destination}
        [IO.File]::WriteAllText((Join-Path $reports 'endurance-targeted-summary.json'),(ConvertTo-Json -InputObject @($runs) -Depth 5),[Text.UTF8Encoding]::new($false))
        if($testExit -ne 0 -or !$match.Success){throw "Endurance run $i failed: $log"}
        Write-Output "Endurance run $i passed: $($match.Groups[1].Value)"
    }
} finally {
    [IO.File]::WriteAllText($source,$original,[Text.UTF8Encoding]::new($false))
    $resolved=(Resolve-Path -LiteralPath $fixtures).Path
    if($resolved -ne (Join-Path $projectRoot 'src\main\resources\data\aicompanion_endurance')){throw 'Unexpected cleanup path'}
    Remove-Item -LiteralPath $resolved -Recurse -Force
    Pop-Location
}
