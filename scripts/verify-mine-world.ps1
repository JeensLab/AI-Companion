param(
    [string]$RunName=('mine-world-'+(Get-Date -Format 'yyyyMMdd-HHmmss')),
    [string]$Seed='20261031',
    [string[]]$Scenarios=@('descent','void','void-2','void-3','void-4','void-5','lava','mobs','water','retreat','come','restart','deepdark'),
    [switch]$Night
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$Scenarios=@($Scenarios | ForEach-Object {$_ -split ','})
$taskRoot=Split-Path -Parent $PSScriptRoot
if($RunName -notmatch '^mine-world-[A-Za-z0-9-]+$'){throw 'Use a new mine-world-* test directory name'}
if($Seed -notmatch '^-?[0-9]+$'){throw 'Seed must be a number'}
$taskRun=Join-Path $taskRoot "run/$RunName"
if(Test-Path -LiteralPath $taskRun){throw 'Test directory already exists; never overwrite an existing world'}
if(-not $env:JAVA_HOME){
    $taskGradleHome=if($env:GRADLE_USER_HOME){$env:GRADLE_USER_HOME}else{Join-Path $HOME '.gradle'}
    $taskJdk=Get-ChildItem -LiteralPath (Join-Path $taskGradleHome 'jdks') -Directory -Filter '*-21-*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if($taskJdk){$env:JAVA_HOME=$taskJdk.FullName}
}
$taskProps="online-mode=false`nserver-port=0`nlevel-type=minecraft:normal`nlevel-seed=$Seed`nspawn-protection=0`nmax-tick-time=-1`nview-distance=4`nsimulation-distance=4`nspawn-monsters=true`nspawn-animals=false`nspawn-npcs=false`ngenerate-structures=true`n"
$taskDiagnostics=Join-Path $taskRoot 'build/diagnostics'
$taskSummary=Join-Path $taskDiagnostics "$RunName-summary.json"
New-Item -ItemType Directory -Force -Path $taskDiagnostics | Out-Null
$taskStart=Get-Date
$taskResults=@()
$taskBad=$false
$taskStopGraceSeconds=60
$taskStageLimitSeconds=1200
$taskScanLimitSeconds=1800

function Write-Dir([string]$dir){
    New-Item -ItemType Directory -Path $dir | Out-Null
    [IO.File]::WriteAllText((Join-Path $dir 'eula.txt'),'eula=true',[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $dir 'server.properties'),($taskProps+"difficulty=normal`n"),[Text.UTF8Encoding]::new($false))
}

# The server of a stage sometimes spins forever in its stop (vanilla ChunkMap unload loop) after the verdict is written, so the
# stage is watched: a grace period after the verdict, plus a hard limit for a stage that never gets that far.
function Get-StageServers([datetime]$since){
    $marker=Join-Path $taskRoot 'build\moddev\mineWorldServerRunVmArgs.txt'
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
        $_.CommandLine -and $_.CreationDate -ge $since -and $_.CommandLine.Replace('\\','\').Replace('/','\').IndexOf($marker,[StringComparison]::OrdinalIgnoreCase) -ge 0 })
}

function Test-StageDone([string]$stage,[string]$json,[string]$log){
    if($stage -ne 'scan'){return (Test-Path -LiteralPath $json)}
    try {
        $stream=[IO.File]::Open($log,'Open','Read','ReadWrite,Delete')
        try {return ([IO.StreamReader]::new($stream)).ReadToEnd().Contains('MINE_WORLD_SCAN_DONE')} finally {$stream.Dispose()}
    } catch {return $false}
}

function Stop-Stage($proc,[datetime]$since){
    foreach($server in Get-StageServers $since){Stop-Process -Id $server.ProcessId -ErrorAction SilentlyContinue}
    if(-not $proc.WaitForExit(120000)){& taskkill.exe /PID $proc.Id /T /F | Out-Null; $null=$proc.WaitForExit(10000)}
}

function Invoke-Stage([string]$name,[string]$dir,[string]$stage,[string]$scenario){
    $log=Join-Path $taskDiagnostics "$RunName-$name-$stage.txt"
    $json=Join-Path $taskRun "$dir/mine-world-$stage.json"
    $gradle=Join-Path $taskRoot 'gradlew.bat'
    $began=Get-Date
    $nightArg=if($Night){" `"-PmineWorldNight=true`""}else{''}
    $proc=Start-Process -FilePath $env:ComSpec -WorkingDirectory $taskRoot -NoNewWindow -PassThru -ArgumentList "/s /d /c `"`"$gradle`" --offline `"-PmineWorldDirectory=run/$RunName/$dir`" `"-PmineWorldStage=$stage`" `"-PmineWorldScenario=$scenario`"$nightArg runMineWorldServer > `"$log`" 2>&1`""
    $null=$proc.Handle
    $limit=if($stage -eq 'scan'){$taskScanLimitSeconds}else{$taskStageLimitSeconds}
    $doneAt=$null
    $hung=''
    while(-not $proc.WaitForExit(2000)){
        $now=Get-Date
        if($null -eq $doneAt -and (Test-StageDone $stage $json $log)){$doneAt=$now}
        if(($now-$began).TotalSeconds -gt $limit){$hung=" [stage timed out after $limit s, killed]";break}
        if($doneAt -and ($now-$doneAt).TotalSeconds -gt $taskStopGraceSeconds -and @(Get-StageServers $began).Count -gt 0){$hung=' [server hung on stop, killed]';break}
    }
    if($hung){Stop-Stage $proc $began}
    $exit=$proc.ExitCode
    $text=[IO.File]::ReadAllText($log)
    $saved=$text.Contains('All dimensions are saved')
    $marks=[regex]::Matches($text,"MINE_WORLD_(PASS|FAIL|UNPROVEN|SETUP_FAILED|SKIPPED|ERROR|PREPARED) scenario=$scenario [^\r\n]*")
    $verdict=if($marks.Count -gt 0){$marks[$marks.Count-1].Groups[1].Value}else{'ERROR'}
    # The restart stage must leave a saved world for the resume stage to load.
    if($hung -and $stage -eq 'prepare' -and $verdict -eq 'PREPARED'){$verdict='ERROR'}
    $line=if($marks.Count -gt 0){$marks[$marks.Count-1].Value -replace '^MINE_WORLD_[A-Z_]+',"MINE_WORLD_$verdict"}else{"no verdict line, gradle exit $exit, see $log"}
    $detail=if(Test-Path -LiteralPath $json){ConvertFrom-Json ([IO.File]::ReadAllText($json))}else{$null}
    if($detail){$hazard=if($null -eq $detail.hazardMet){'n/a'}else{"$($detail.hazardMet)".ToLower()}; $line="MINE_WORLD_$verdict scenario=$scenario stage=$stage hazardMet=$hazard climbs=$($detail.climbs) goalBlocked=$($detail.goalBlocked) floorRefusals=$($detail.floorRefusals) stairStops=$($detail.stairStops) day=$($detail.day) newStairs=$($detail.newStairs) deaths=$($detail.deaths) ticks=$($detail.gameTicks) wall=$($detail.wallSeconds)s reason=`"$($detail.reason)`""}
    $line+=$hung
    if($verdict -ne 'SKIPPED' -and -not $saved -and $verdict -ne 'SETUP_FAILED'){$line+=' [world was not saved cleanly]'}
    return [ordered]@{scenario=$scenario;stage=$stage;verdict=$verdict;line=$line;exitCode=$exit;saved=$saved;killed=$hung.Trim();log=$log;detail=$detail}
}

function Save-Summary(){
    $report=[ordered]@{run=$RunName;seed=$Seed;started=$taskStart.ToString('o');wallSeconds=[int]((Get-Date)-$taskStart).TotalSeconds;results=@($taskResults)}
    [IO.File]::WriteAllText($taskSummary,(ConvertTo-Json -InputObject $report -Depth 8),[Text.UTF8Encoding]::new($false))
}

Push-Location $taskRoot
try {
    Write-Dir (Join-Path $taskRun 'scan')
    $taskScan=Invoke-Stage 'scan' 'scan' 'scan' 'scan'
    $taskScanText=[IO.File]::ReadAllText($taskScan.log)
    if(-not $taskScanText.Contains('MINE_WORLD_SCAN_DONE') -or -not $taskScan.saved){
        Get-Content -LiteralPath $taskScan.log -Tail 40
        throw 'World scan failed'
    }
    ([regex]::Matches($taskScanText,'MINE_WORLD_(SITE|SCAN_DONE)[^\r\n]*') | ForEach-Object {$_.Value}) | Write-Output
    foreach($taskScenario in $Scenarios){
        $taskDir=$taskScenario
        Write-Dir (Join-Path $taskRun $taskDir)
        Copy-Item -LiteralPath (Join-Path $taskRun 'scan/world') -Destination (Join-Path $taskRun "$taskDir/world") -Recurse
        Copy-Item -LiteralPath (Join-Path $taskRun 'scan/sites.properties') -Destination (Join-Path $taskRun "$taskDir/sites.properties")
        $taskStages=if($taskScenario -eq 'restart'){@('prepare','resume')}else{@('run')}
        $taskFinal=$null
        foreach($taskStage in $taskStages){
            $taskOne=Invoke-Stage $taskScenario $taskDir $taskStage $taskScenario
            $taskResults+=$taskOne
            $taskFinal=$taskOne
            if($taskStage -eq 'prepare' -and $taskOne.verdict -eq 'PREPARED'){continue}
            break
        }
        Save-Summary
        Write-Output ("{0,-9} {1}" -f $taskFinal.verdict,$taskFinal.line)
        if($taskFinal.verdict -in @('FAIL','UNPROVEN','SETUP_FAILED','ERROR')){$taskBad=$true}
    }
}
finally {Pop-Location}
Save-Summary
$taskFinals=@($taskResults | Where-Object {$_.verdict -ne 'PREPARED'})
$taskCounts=@($taskFinals | Group-Object {$_.verdict} | ForEach-Object {"$($_.Name)=$($_.Count)"})
$taskPasses=@($taskFinals | Where-Object {$_.verdict -eq 'PASS'})
$taskMet=@($taskPasses | Where-Object {$_.detail -and $_.detail.hazardMet -eq $true}).Count
$taskAvoided=@($taskPasses | Where-Object {$_.detail -and $_.detail.hazardMet -eq $false}).Count
$taskCounts+="PASS-hazard-met=$taskMet PASS-hazard-avoided=$taskAvoided PASS-no-hazard=$($taskPasses.Count-$taskMet-$taskAvoided)"
Write-Output ("TOTAL {0} in {1} s; summary {2}" -f ($taskCounts -join ' '),[int]((Get-Date)-$taskStart).TotalSeconds,$taskSummary)
if($taskBad){Write-Output 'RED: at least one scenario failed';exit 1}
Write-Output 'GREEN';exit 0
