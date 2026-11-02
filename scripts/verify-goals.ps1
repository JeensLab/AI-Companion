param([ValidateRange(1,20)][int]$Repetitions=3)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')
Invoke-GameTestSeries -Name 'goals' -Label 'Goal' -Directory 'run/goal-tests' -Classes @('CompanionGoalTests') -Repetitions $Repetitions
