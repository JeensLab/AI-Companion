param([ValidateRange(1,20)][int]$Repetitions=3)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')
Invoke-GameTestSeries -Name 'agenda' -Label 'Agenda' -Directory 'run/agenda-tests' -Classes @('CompanionAgendaTests','CompanionAgendaModelTests','CompanionAgendaBoardTests') -Repetitions $Repetitions
