# Dot-sourced by the focused verify scripts. Selection travels as Gradle properties, so sources stay untouched.
$gameTestRoot=Split-Path -Parent $PSScriptRoot
$gameTestReports=Join-Path $gameTestRoot 'build\diagnostics'
New-Item -ItemType Directory -Path $gameTestReports -Force | Out-Null

function Write-GameTestSummary([string]$Name,$Value) {
    [IO.File]::WriteAllText((Join-Path $gameTestReports "$Name-summary.json"),(ConvertTo-Json -InputObject $Value -Depth 5),[Text.UTF8Encoding]::new($false))
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
    $output=[IO.File]::ReadAllText($Log)
    $match=[regex]::Match($output,'All (\d+) required tests passed')
    $failed=@([regex]::Matches($output,'LogTestReporter[^\r\n]*?\]: ([^\r\n]+) failed at') | ForEach-Object {$_.Groups[1].Value} | Select-Object -Unique)
    [ordered]@{exitCode=$testExit;passed=if($match.Success){[int]$match.Groups[1].Value}else{0};failed=$failed;seconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2);log=$Log}
}

function Invoke-GameTestSeries {
    param([Parameter(Mandatory)][string]$Name,[Parameter(Mandatory)][string]$Label,[string]$Directory,[string[]]$Classes=@(),[int]$Repetitions=1)
    $runs=@()
    for($iteration=1;$iteration -le $Repetitions;$iteration++) {
        $run=Invoke-GameTestRun -Log (Join-Path $gameTestReports "$Name-targeted-$iteration.txt") -Directory $Directory -Classes $Classes
        $run.Insert(0,'iteration',$iteration)
        $runs+=$run
        Write-GameTestSummary "$Name-targeted" @($runs)
        if($run.exitCode -ne 0 -or !$run.passed){Get-Content -LiteralPath $run.log -Tail 45;throw "$Label verification failed: $($run.log)"}
        Write-Output "$Label run $iteration passed: $($run.passed)"
    }
}
