param([string]$RunName=('mine-restart-'+(Get-Date -Format 'yyyyMMdd-HHmmss')))
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
if($RunName -notmatch '^mine-restart-[A-Za-z0-9-]+$'){throw 'Use a new mine-restart-* test directory name'}
$taskWorld=Join-Path $taskRoot "run/$RunName"
if(Test-Path -LiteralPath $taskWorld){throw 'Test directory already exists; never overwrite an existing world'}
New-Item -ItemType Directory -Path $taskWorld | Out-Null
[IO.File]::WriteAllText((Join-Path $taskWorld 'eula.txt'),'eula=true',[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $taskWorld 'server.properties'),"online-mode=false`nserver-port=0`nlevel-type=minecraft:flat`nspawn-protection=0`nmax-tick-time=-1`ndifficulty=peaceful`nview-distance=3`nsimulation-distance=3`n",[Text.UTF8Encoding]::new($false))
$taskResults=@()
Push-Location $taskRoot
try {
    New-Item -ItemType Directory -Force -Path (Join-Path $taskRoot 'build/diagnostics') | Out-Null
    foreach($taskStage in @('prepare','resume','verify')) {
        $taskLog=Join-Path $taskRoot "build/diagnostics/$RunName-$taskStage.txt"
        $ErrorActionPreference='Continue'
        try { & ./gradlew.bat --offline "-PmineRestartDirectory=run/$RunName" "-PmineRestartStage=$taskStage" runMineRestartServer *> $taskLog; $taskExit=$LASTEXITCODE }
        finally {$ErrorActionPreference='Stop'}
        $taskOutput=[IO.File]::ReadAllText($taskLog)
        $taskPassed=$taskExit -eq 0 -and $taskOutput.Contains("MINE_RESTART_PASS stage=$taskStage") -and !$taskOutput.Contains('MINE_RESTART_FAIL') -and $taskOutput.Contains('All dimensions are saved')
        $taskResults+=[ordered]@{stage=$taskStage;passed=$taskPassed;exitCode=$taskExit;log=$taskLog;manifest=if(Test-Path "$taskWorld/mine-probe.properties"){[IO.File]::ReadAllText("$taskWorld/mine-probe.properties")}else{''}}
        [IO.File]::WriteAllText((Join-Path $taskRoot "build/diagnostics/$RunName-summary.json"),(ConvertTo-Json -InputObject @($taskResults) -Depth 6),[Text.UTF8Encoding]::new($false))
        if(!$taskPassed){Get-Content -LiteralPath $taskLog -Tail 60;throw "Process restart stage failed: $taskStage"}
        Write-Output "Fresh JVM passed: $taskStage"
    }
}finally{Pop-Location}
