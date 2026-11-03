param([ValidateRange(1,10)][int]$Repetitions=1)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')
Invoke-GameTestSeries -Name 'tasks' -Label 'Task' -Directory 'run/task-tests' -Classes @('CompanionTaskTests','CompanionPlanRecoveryTests') -Repetitions $Repetitions
