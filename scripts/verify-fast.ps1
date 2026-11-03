param([ValidateRange(1,20)][int]$Repetitions=1,[ValidatePattern('^(auto|[1-8])$')][string]$Shards='auto')
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
    $failed=@([regex]::Matches($output,'\]: ([^\r\n]+) failed at [^\r\n]*?! ([^\r\n]{0,200})') | ForEach-Object {"$($_.Groups[1].Value): $($_.Groups[2].Value)"} | Select-Object -Unique)
    [ordered]@{shard=$Shard;passed=$parsed.passed;expected=if($selection.Success){[int]$selection.Groups[2].Value}else{0};pool=if($selection.Success){[int]$selection.Groups[1].Value}else{0};failedCount=$parsed.failedCount;failed=$failed;encodingBroken=$parsed.encodingBroken}
}

# One server holds about 1.5 GB; 2 GB each keeps the system out of swap. Six and eight shards were no faster than four, and eight left 1.4 GB free.
function Get-AutoShards {
    $freeGb=(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB
    $byMemory=[math]::Floor($freeGb/2)
    $count=[math]::Max(1,[math]::Min(4,[math]::Min([Environment]::ProcessorCount-1,$byMemory)))
    Write-Host "Shards: $count (cores $([Environment]::ProcessorCount), free $([math]::Round($freeGb,1)) GB, limit 4)"
    [int]$count
}

$shardCount=if($Shards -eq 'auto'){Get-AutoShards}else{[int]$Shards}
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
    if(@($results | Where-Object {$_.encodingBroken}).Count){Write-Warning "U+FFFD in shard logs: log encoding is broken"}
    $passed=($results | ForEach-Object {$_.passed} | Measure-Object -Sum).Sum
    $expected=($results | ForEach-Object {$_.expected} | Measure-Object -Sum).Sum
    $pool=($results | ForEach-Object {$_.pool} | Sort-Object -Unique)
    $run=[ordered]@{iteration=$iteration;exitCode=$testExit;passed=[int]$passed;expected=[int]$expected;seconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2);log=$log;shards=$results}
    $runs+=$run
    Write-GameTestSummary 'fast' @($runs)
    # A shard that died early or logged nothing must not pass as a smaller suite: each one has to report work,
    # finish all of it, and together they have to add up to the same pool.
    $broken=@($results | Where-Object {$_.expected -le 0 -or $_.passed -ne $_.expected -or $_.failedCount})
    if($testExit -ne 0 -or $broken.Count -or @($pool).Count -ne 1 -or $expected -ne $pool){
        $results | ForEach-Object {$_.failed} | Where-Object {$_} | ForEach-Object {Write-Output "FAILED $_"}
        throw "Fast verification failed: $passed of $expected passed, pool $($pool -join '/'), exit $testExit. See $log"
    }
    Write-Output "Fast run $iteration passed: $passed in $($run.seconds)s"
}
