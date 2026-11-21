param([ValidateRange(1,20)][int]$Repetitions=1,[string[]]$Classes=@())
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')
$selected=@('CompanionDiamondMineTests')+@($Classes | ForEach-Object {$_ -split ','} | ForEach-Object {$_.Trim()} | Where-Object {$_})
Invoke-GameTestSeries -Name 'diamond' -Label 'Diamond' -Directory 'run/diamond-tests' -Classes ($selected | Select-Object -Unique) -Repetitions $Repetitions
