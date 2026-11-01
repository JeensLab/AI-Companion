param([ValidateRange(1,20)][int]$Repetitions=10,[switch]$CriticalOnly,[ValidateSet('All','Forest','Cave','Reload')][string]$Arena='All')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')
$arenas=[ordered]@{Forest='twoHoursForestToolsStorageAndReturningPlayer';Cave='ninetyMinutesCaveFoodOreLightAndChangingPassage';Reload='hundredMinutesFullBagOrdersCancellationAndReloads'}
if($Arena -ne 'All' -and $CriticalOnly){throw 'Choose a long arena or CriticalOnly'}
$selection=@{}
if($Arena -ne 'All'){$selection.Methods=@($arenas[$Arena])}
else{
    $selection.Classes=@('CompanionEnduranceTests')
    if($CriticalOnly){$selection.SkipMethods=@($arenas.Values)}
}
$runs=@()
for($i=1;$i -le $Repetitions;$i++) {
    $run=Invoke-GameTestRun -Log (Join-Path $gameTestReports "endurance-targeted-$i.txt") @selection
    $destination=Join-Path $gameTestReports "endurance-run-$i"
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    $metrics=Join-Path $gameTestRoot 'run/endurance'
    if(Test-Path -LiteralPath $metrics){
        Get-ChildItem -LiteralPath $metrics -Filter '*.json' | Where-Object { !$CriticalOnly -or $_.Name -eq 'interrupted-disk-reload.json' } | Copy-Item -Destination $destination
    }
    $run.Insert(0,'iteration',$i)
    $run.metrics=$destination
    $runs+=$run
    Write-GameTestSummary 'endurance-targeted' @($runs)
    if($run.exitCode -ne 0 -or !$run.passed){throw "Endurance run $i failed: $($run.log)"}
    Write-Output "Endurance run $i passed: $($run.passed)"
}
