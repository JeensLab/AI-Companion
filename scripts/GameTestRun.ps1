# Dot-sourced by the focused verify scripts. Selection travels as Gradle properties, so sources stay untouched.
$gameTestRoot=Split-Path -Parent $PSScriptRoot
$gameTestReports=Join-Path $gameTestRoot 'build\diagnostics'
New-Item -ItemType Directory -Path $gameTestReports -Force | Out-Null
# `*>` decodes the child's output with the console code page; the scripts run as a child pwsh, so the setting does not outlive them.
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)

function Write-GameTestSummary([string]$Name,$Value) {
    [IO.File]::WriteAllText((Join-Path $gameTestReports "$Name-summary.json"),(ConvertTo-Json -InputObject $Value -Depth 6),[Text.UTF8Encoding]::new($false))
}

function ConvertFrom-GameTestLog([string]$Output) {
    $all=[regex]::Match($Output,'All (\d+) required tests passed')
    $completeMatch=[regex]::Match($Output,'(\d+) GAME TESTS COMPLETE')
    $failedMatch=[regex]::Match($Output,'(\d+) required tests failed')
    $complete=if($completeMatch.Success){[int]$completeMatch.Groups[1].Value}else{0}
    $failedCount=if($failedMatch.Success){[int]$failedMatch.Groups[1].Value}else{0}
    $failed=@([regex]::Matches($Output,'LogTestReporter[^\r\n]*?\]: ([^\r\n]+) failed at [^\r\n]*?! ([^\r\n]{0,200})') | ForEach-Object {"$($_.Groups[1].Value): $($_.Groups[2].Value)"} | Select-Object -Unique)
    # One failed test used to zero the whole run: without the All-line, passed is complete minus failed.
    $passed=if($all.Success){[int]$all.Groups[1].Value}elseif($complete -gt 0 -and $failedCount -gt 0){$complete-$failedCount}else{0}
    [ordered]@{ok=$all.Success;passed=$passed;complete=$complete;failedCount=$failedCount;failed=$failed;encodingBroken=$Output.Contains([string][char]0xFFFD)}
}

function Invoke-GameTestRun {
    param([Parameter(Mandatory)][string]$Log,[string]$Directory,[string[]]$Classes=@(),[string[]]$Methods=@(),[string[]]$SkipMethods=@())
    $gradleArgs=@('--offline')
    if($Directory){$gradleArgs+="-PgameTestDirectory=$Directory"}
    if($Classes.Count){$gradleArgs+="-PgameTestClasses=$($Classes -join ',')"}
    if($Methods.Count){$gradleArgs+="-PgameTestMethods=$($Methods -join ',')"}
    if($SkipMethods.Count){$gradleArgs+="-PgameTestSkipMethods=$($SkipMethods -join ',')"}
    $gradleArgs+='runGameTestServer'
    $started=Get-Date
    Push-Location $gameTestRoot
    $ErrorActionPreference='Continue'
    try { & ./gradlew.bat @gradleArgs *> $Log; $testExit=$LASTEXITCODE }
    finally { $ErrorActionPreference='Stop'; Pop-Location }
    $parsed=ConvertFrom-GameTestLog ([IO.File]::ReadAllText($Log))
    if($parsed.encodingBroken){Write-Warning "U+FFFD in ${Log}: log encoding is broken"}
    [ordered]@{exitCode=$testExit;ok=$parsed.ok;passed=$parsed.passed;complete=$parsed.complete;failedCount=$parsed.failedCount;failed=$parsed.failed;encodingBroken=$parsed.encodingBroken;seconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2);log=$Log}
}

# The server log of a red run is overwritten by the next run; keep it next to the Gradle output.
function Save-GameTestServerLog([string]$Directory,[string]$Destination) {
    $serverLog=Join-Path (Join-Path $gameTestRoot $(if($Directory){$Directory}else{'run'})) 'logs\latest.log'
    if(Test-Path -LiteralPath $serverLog){Copy-Item -LiteralPath $serverLog -Destination (Join-Path $gameTestReports $Destination) -Force}
}

function Invoke-GameTestSeries {
    param([Parameter(Mandatory)][string]$Name,[Parameter(Mandatory)][string]$Label,[string]$Directory,[string[]]$Classes=@(),[int]$Repetitions=1)
    $runs=@()
    for($iteration=1;$iteration -le $Repetitions;$iteration++) {
        $run=Invoke-GameTestRun -Log (Join-Path $gameTestReports "$Name-targeted-$iteration.txt") -Directory $Directory -Classes $Classes
        $run.Insert(0,'iteration',$iteration)
        $runs+=$run
        Write-GameTestSummary "$Name-targeted" @($runs)
        if($run.exitCode -ne 0 -or -not $run.ok){
            Save-GameTestServerLog $Directory "$Name-$iteration-latest.log"
            foreach($name in $run.failed){Write-Output "FAILED $name"}
            if($run.failedCount -gt 0){Write-Output "$($run.failedCount) required tests failed"}
            # The tail only helps when the server died before reporting; with a reported failure the cause is the test name.
            if(@($run.failed).Count -eq 0){Get-Content -LiteralPath $run.log -Tail 45}
            throw "$Label verification failed: $($run.log)"
        }
        Write-Output "$Label run $iteration passed: $($run.passed)"
    }
}
