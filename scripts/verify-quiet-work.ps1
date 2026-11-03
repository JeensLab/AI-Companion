$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')
$quietClasses=@('CompanionQuietWorkTests','CompanionSpeechTests','CompanionPlanRecoveryTests','CompanionTaskTests')
$quietMethods=@('failedWoodIsFollowedByHuntingAndCooking','reloadKeepsFailureBackoff')
$quietRun=Invoke-GameTestRun -Log (Join-Path $gameTestReports 'quiet-work.txt') -Directory 'run/quiet-tests' -Classes $quietClasses -Methods $quietMethods
Write-GameTestSummary 'quiet-work' $quietRun
if($quietRun.exitCode -ne 0 -or -not $quietRun.ok){
    foreach($name in $quietRun.failed){Write-Output "FAILED $name"}
    if($quietRun.failedCount -gt 0){Write-Output "$($quietRun.failedCount) required tests failed"}
    if(@($quietRun.failed).Count -eq 0){Get-Content -LiteralPath $quietRun.log -Tail 45}
    throw "Quiet-work verification failed: $($quietRun.log)"
}
Write-Output "Quiet work passed: $($quietRun.passed)"
