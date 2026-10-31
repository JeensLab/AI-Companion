param([ValidateRange(1,20)][int]$Repetitions=1,[string[]]$Classes=@())
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'GameTestRun.ps1')
$looseClasses=@('CompanionLooseTests')+@($Classes | ForEach-Object {$_ -split ','} | ForEach-Object {$_.Trim()} | Where-Object {$_}) | Select-Object -Unique
foreach($looseClass in $looseClasses){if($looseClass -notmatch '^[A-Za-z0-9_]+$'){throw "Unknown gametest class: $looseClass"}}
Invoke-GameTestSeries -Name 'loose' -Label 'Loose' -Directory 'run/loose-tests' -Classes $looseClasses -Repetitions $Repetitions
