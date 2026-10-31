param([ValidateRange(1,20)][int]$Repetitions=1,[ValidateRange(1,8)][int]$Shards=4)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')

function Read-Shard([int]$Shard) {
    $log=Join-Path $gameTestRoot "run\shard-$Shard\logs\latest.log"
    if(!(Test-Path -LiteralPath $log)){return [ordered]@{shard=$Shard;passed=0;expected=0;pool=0;failed=@("no log: $log")}}
    $output=[IO.File]::ReadAllText($log)
    $selection=[regex]::Match($output,'Game test selection: pool=\d+ selected=\d+ pool-required=(\d+) selected-required=(\d+)')
    $match=[regex]::Match($output,'All (\d+) required tests passed')
    $failed=@([regex]::Matches($output,'\]: ([^\r\n]+) failed at [^\r\n]*?! ([^\r\n]{0,200})') | ForEach-Object {"$($_.Groups[1].Value): $($_.Groups[2].Value)"} | Select-Object -Unique)
    [ordered]@{shard=$Shard;passed=if($match.Success){[int]$match.Groups[1].Value}else{0};expected=if($selection.Success){[int]$selection.Groups[2].Value}else{0};pool=if($selection.Success){[int]$selection.Groups[1].Value}else{0};failed=$failed}
}

$runs=@()
for($iteration=1;$iteration -le $Repetitions;$iteration++) {
    0..($Shards-1) | ForEach-Object {
        $stale=Join-Path $gameTestRoot "run\shard-$_\logs\latest.log"
        if(Test-Path -LiteralPath $stale){Remove-Item -LiteralPath $stale -Force}
    }
    $log=Join-Path $gameTestReports "fast-$iteration.txt"
    $started=Get-Date
    Push-Location $gameTestRoot
    $ErrorActionPreference='Continue'
    try { & ./gradlew.bat --offline --continue "-PgameTestShards=$Shards" runFastGameTests *> $log; $testExit=$LASTEXITCODE }
    finally { $ErrorActionPreference='Stop'; Pop-Location }
    $results=@(0..($Shards-1) | ForEach-Object {Read-Shard $_})
    $passed=($results | ForEach-Object {$_.passed} | Measure-Object -Sum).Sum
    $expected=($results | ForEach-Object {$_.expected} | Measure-Object -Sum).Sum
    $pool=($results | ForEach-Object {$_.pool} | Sort-Object -Unique)
    $run=[ordered]@{iteration=$iteration;exitCode=$testExit;passed=[int]$passed;expected=[int]$expected;seconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2);log=$log;shards=$results}
    $runs+=$run
    Write-GameTestSummary 'fast' @($runs)
    # A shard that died early or logged nothing must not pass as a smaller suite: each one has to report work,
    # finish all of it, and together they have to add up to the same pool.
    $broken=@($results | Where-Object {$_.expected -le 0 -or $_.passed -ne $_.expected})
    if($testExit -ne 0 -or $broken.Count -or @($pool).Count -ne 1 -or $expected -ne $pool){
        $results | ForEach-Object {$_.failed} | Where-Object {$_} | ForEach-Object {Write-Output "FAILED $_"}
        throw "Fast verification failed: $passed of $expected passed, pool $($pool -join '/'), exit $testExit. See $log"
    }
    Write-Output "Fast run $iteration passed: $passed in $($run.seconds)s"
}
