param(
    [string]$RunName=('mine-world-'+(Get-Date -Format 'yyyyMMdd-HHmmss')),
    [string]$Seed='20261031',
    [string[]]$Scenarios=@('descent','void','lava','mobs','water','retreat','come','restart','deepdark')
)
$ErrorActionPreference='Stop'
$Scenarios=@($Scenarios | ForEach-Object {$_ -split ','})
$taskRoot=Split-Path -Parent $PSScriptRoot
if($RunName -notmatch '^mine-world-[A-Za-z0-9-]+$'){throw 'Use a new mine-world-* test directory name'}
if($Seed -notmatch '^-?[0-9]+$'){throw 'Seed must be a number'}
$taskRun=Join-Path $taskRoot "run/$RunName"
if(Test-Path -LiteralPath $taskRun){throw 'Test directory already exists; never overwrite an existing world'}
if(-not $env:JAVA_HOME){
    $taskJdk='C:\Users\Admin\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
    if(Test-Path -LiteralPath $taskJdk){$env:JAVA_HOME=$taskJdk}
}
$taskProps="online-mode=false`nserver-port=0`nlevel-type=minecraft:normal`nlevel-seed=$Seed`nspawn-protection=0`nmax-tick-time=-1`ndifficulty=normal`nview-distance=4`nsimulation-distance=4`nspawn-monsters=true`nspawn-animals=false`nspawn-npcs=false`ngenerate-structures=true`n"
$taskDiagnostics=Join-Path $taskRoot 'build/diagnostics'
$taskSummary=Join-Path $taskDiagnostics "$RunName-summary.json"
New-Item -ItemType Directory -Force -Path $taskDiagnostics | Out-Null
$taskStart=Get-Date
$taskResults=@()
$taskBad=$false

function Write-Dir([string]$dir){
    New-Item -ItemType Directory -Path $dir | Out-Null
    [IO.File]::WriteAllText((Join-Path $dir 'eula.txt'),'eula=true',[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $dir 'server.properties'),$taskProps,[Text.UTF8Encoding]::new($false))
}

function Invoke-Stage([string]$name,[string]$dir,[string]$stage,[string]$scenario){
    $log=Join-Path $taskDiagnostics "$RunName-$name-$stage.txt"
    $ErrorActionPreference='Continue'
    try { & ./gradlew.bat --offline "-PmineWorldDirectory=run/$RunName/$dir" "-PmineWorldStage=$stage" "-PmineWorldScenario=$scenario" runMineWorldServer *> $log; $exit=$LASTEXITCODE }
    finally {$ErrorActionPreference='Stop'}
    $text=[IO.File]::ReadAllText($log)
    $saved=$text.Contains('All dimensions are saved')
    $marks=[regex]::Matches($text,"MINE_WORLD_(PASS|FAIL|SETUP_FAILED|SKIPPED|ERROR|PREPARED) scenario=$scenario [^\r\n]*")
    $verdict=if($marks.Count -gt 0){$marks[$marks.Count-1].Groups[1].Value}else{'ERROR'}
    $line=if($marks.Count -gt 0){$marks[$marks.Count-1].Value}else{"no verdict line, gradle exit $exit, see $log"}
    $json=Join-Path $taskRun "$dir/mine-world-$stage.json"
    $detail=if(Test-Path -LiteralPath $json){ConvertFrom-Json ([IO.File]::ReadAllText($json))}else{$null}
    if($verdict -ne 'SKIPPED' -and -not $saved -and $verdict -ne 'SETUP_FAILED'){$line+=' [world was not saved cleanly]'}
    return [ordered]@{scenario=$scenario;stage=$stage;verdict=$verdict;line=$line;exitCode=$exit;saved=$saved;log=$log;detail=$detail}
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
        if($taskFinal.verdict -in @('FAIL','SETUP_FAILED','ERROR')){$taskBad=$true}
    }
}
finally {Pop-Location}
Save-Summary
$taskCounts=$taskResults | Where-Object {$_.verdict -ne 'PREPARED'} | Group-Object verdict | ForEach-Object {"$($_.Name)=$($_.Count)"}
Write-Output ("TOTAL {0} in {1} s; summary {2}" -f ($taskCounts -join ' '),[int]((Get-Date)-$taskStart).TotalSeconds,$taskSummary)
if($taskBad){Write-Output 'RED: at least one scenario failed';exit 1}
Write-Output 'GREEN';exit 0
