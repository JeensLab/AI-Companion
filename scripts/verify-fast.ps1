param([ValidateRange(1,20)][int]$Repetitions=1,[ValidatePattern('^(auto|[1-8])$')][string]$Shards='auto',[switch]$Force,[switch]$DryRun)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')

function Read-Shard([int]$Shard) {
    $log=Join-Path $gameTestRoot "run\shard-$Shard\logs\latest.log"
    if(!(Test-Path -LiteralPath $log)){return [ordered]@{shard=$Shard;passed=0;expected=0;pool=0;failedCount=0;failed=@("no log: $log");encodingBroken=$false}}
    # Another process may still hold the log for a moment after its server exits: a shared read must not turn a green run red.
    $stream=[IO.File]::Open($log,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)
    try { $reader=[IO.StreamReader]::new($stream); $output=$reader.ReadToEnd() } finally { $stream.Dispose() }
    $selection=[regex]::Match($output,'Game test selection: pool=\d+ selected=\d+ pool-required=(\d+) selected-required=(\d+)')
    $parsed=ConvertFrom-GameTestLog $output
    [ordered]@{shard=$Shard;passed=$parsed.passed;expected=if($selection.Success){[int]$selection.Groups[2].Value}else{0};pool=if($selection.Success){[int]$selection.Groups[1].Value}else{0};failedCount=$parsed.failedCount;failed=$parsed.failed;encodingBroken=$parsed.encodingBroken}
}

# One server holds about 1.5 GB; 2 GB each keeps the system out of swap.
# Measured on one machine: 4 shards took 228 s, 6 took 193 s, 8 took 270 s, and 1 / 1 / 3 tests were still red after the retry.
# Stability is claimed only for 4, the default; 5-8 need -Force.
function Get-AutoShards {
    $freeGb=(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB
    $byMemory=[math]::Floor($freeGb/2)
    $count=[math]::Max(1,[math]::Min(4,[math]::Min([Environment]::ProcessorCount-1,$byMemory)))
    # Returned through a script variable: anything written to the output stream here would become part of the return value.
    $script:autoShardsLine="Shards: $count (cores $([Environment]::ProcessorCount), free $([math]::Round($freeGb,1)) GB, limit 4)"
    [int]$count
}

if($Shards -eq 'auto'){$shardCount=Get-AutoShards;Write-Output $script:autoShardsLine}
elseif([int]$Shards -gt 4 -and -not $Force){
    Write-Output "Shards: 4 (asked $Shards; 5-8 need -Force: they were not always faster and flaked here)"
    $shardCount=4
}
elseif([int]$Shards -gt 4){
    Write-Output "Shards: $Shards (asked $Shards with -Force; stability is not guaranteed above 4)"
    $shardCount=[int]$Shards
}
else{
    Write-Output "Shards: $Shards (asked $Shards)"
    $shardCount=[int]$Shards
}
if($DryRun){return}
$runs=@()
for($iteration=1;$iteration -le $Repetitions;$iteration++) {
    # Four kept worlds grow four times as fast as run/world, which reached 12.6 GB and slowed runs into random failures.
    Get-ChildItem -LiteralPath (Join-Path $gameTestRoot 'run') -Directory -Filter 'shard-*' -ErrorAction SilentlyContinue | ForEach-Object {
        $shardDir=$_.FullName
        $stale=Join-Path $shardDir 'logs\latest.log'
        if(Test-Path -LiteralPath $stale){Remove-Item -LiteralPath $stale -Force}
        $world=Join-Path $shardDir 'world'
        if(Test-Path -LiteralPath $world){Remove-Item -LiteralPath $world -Recurse -Force}
    }
    $log=Join-Path $gameTestReports "fast-$iteration.txt"
    $started=Get-Date
    Push-Location $gameTestRoot
    $ErrorActionPreference='Continue'
    try { & ./gradlew.bat --offline --continue "-PgameTestShards=$shardCount" runFastGameTests *> $log; $testExit=$LASTEXITCODE }
    finally { $ErrorActionPreference='Stop'; Pop-Location }
    $results=@(0..($shardCount-1) | ForEach-Object {Read-Shard $_})
    # A shard that flakes is retried alone, as the Linux gate does; its world and log start clean so a stale
    # lock cannot fail the retry as well.
    $retried=@()
    $flaky=@()
    foreach($shard in 0..($shardCount-1)){
        $result=$results[$shard]
        if($result.expected -le 0 -or $result.passed -ne $result.expected -or $result.failedCount){
            Write-Output "::warning::shard $shard failed, retrying it alone"
            $retried+=$shard
            $shardDir=Join-Path $gameTestRoot "run\shard-$shard"
            $stale=Join-Path $shardDir 'logs\latest.log'
            # The retry overwrites the log and the result; a flake that then passes would leave no trace of what failed.
            $firstLog=Join-Path $gameTestReports "fast-$iteration-shard-$shard-attempt-1.log"
            if(Test-Path -LiteralPath $stale){Copy-Item -LiteralPath $stale -Destination $firstLog -Force}
            $firstAttempt=[ordered]@{passed=$result.passed;expected=$result.expected;failedCount=$result.failedCount;failed=@($result.failed);log=$firstLog}
            $flaky+=@($result.failed)
            if(Test-Path -LiteralPath $stale){Remove-Item -LiteralPath $stale -Force}
            $world=Join-Path $shardDir 'world'
            if(Test-Path -LiteralPath $world){Remove-Item -LiteralPath $world -Recurse -Force}
            Push-Location $gameTestRoot
            $ErrorActionPreference='Continue'
            try { & ./gradlew.bat --offline "-PgameTestShards=$shardCount" "runGameTestShard$shard" *>> $log; $retryExit=$LASTEXITCODE }
            finally { $ErrorActionPreference='Stop'; Pop-Location }
            $results[$shard]=Read-Shard $shard
            $results[$shard].firstAttempt=$firstAttempt
            if($retryExit -ne 0){ $results[$shard].failed += "exit-$retryExit" }
        }
    }
    if(@($results | Where-Object {$_.encodingBroken}).Count){Write-Warning "U+FFFD in shard logs: log encoding is broken"}
    $passed=($results | ForEach-Object {$_.passed} | Measure-Object -Sum).Sum
    $expected=($results | ForEach-Object {$_.expected} | Measure-Object -Sum).Sum
    $pool=($results | ForEach-Object {$_.pool} | Sort-Object -Unique)
    $run=[ordered]@{iteration=$iteration;exitCode=$testExit;passed=[int]$passed;expected=[int]$expected;seconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2);log=$log;retried=($retried -join ',');flaky=@($flaky);shards=$results}
    # A nonzero exit code must never stay unexplained in the summary.
    if($testExit -ne 0 -and @($results | ForEach-Object {$_.failed} | Where-Object {$_}).Count -eq 0 -and $flaky.Count -eq 0){
        $gradleLines=@([IO.File]::ReadAllText($log) -split '\r?\n')
        $marker=@(0..($gradleLines.Count-1) | Where-Object {$gradleLines[$_] -match 'What went wrong'} | Select-Object -First 1)
        $wentWrong=if($marker.Count){(@($gradleLines[$marker[0]..([math]::Min($marker[0]+6,$gradleLines.Count-1))]) -join "`n")}else{''}
        $run.cause='unknown'
        $run.wentWrong=$wentWrong
        Write-Output "::warning::exit $testExit with no failed test in any shard (cause unknown). $wentWrong"
    }
    $runs+=$run
    Write-GameTestSummary 'fast' @($runs)
    # A shard that died early or logged nothing must not pass as a smaller suite: each one has to report work,
    # finish all of it, and together they have to add up to the same pool.
    # A retry that printed the success line but still exited nonzero is not a green shard.
    $broken=@($results | Where-Object {$_.expected -le 0 -or $_.passed -ne $_.expected -or $_.failedCount -or (($_.failed -join ' ') -match 'exit-')})
    if((($retried.Count -eq 0) -and $testExit -ne 0) -or $broken.Count -or @($pool).Count -ne 1 -or $expected -ne $pool){
        $results | ForEach-Object {$_.failed} | Where-Object {$_} | ForEach-Object {Write-Output "FAILED $_"}
        throw "Fast verification failed: $passed of $expected passed, pool $($pool -join '/'), exit $testExit. See $log"
    }
    Write-Output "Fast run $iteration passed: $passed in $($run.seconds)s"
}
