param([ValidateRange(1,20)][int]$Repetitions=10)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')
Invoke-GameTestSeries -Name 'banter' -Label 'Focused' -Directory 'run/banter-tests' -Classes @('CompanionBanterToolsTests','CompanionCharacterTests','CompanionSoulTests') -Repetitions $Repetitions
