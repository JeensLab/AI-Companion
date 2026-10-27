param([ValidateRange(1,20)][int]$Repetitions=10)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$namespace='aicompanion_banter'
$fixturePath=Join-Path $projectRoot "src\main\resources\data\$namespace"
$sources=@{}
foreach($name in @('CompanionBanterToolsTests','CompanionCharacterTests','CompanionSoulTests')) {
    $path=Join-Path $projectRoot "src\main\java\dev\aicompanion\gametest\$name.java"
    $sources[$path]=[IO.File]::ReadAllText($path)
}
$reports=Join-Path $projectRoot 'build\diagnostics'
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$init=Join-Path $reports 'banter-only.init.gradle'
[IO.File]::WriteAllText($init,"gradle.projectsEvaluated { rootProject.neoForge.runs.gameTestServer { systemProperty 'neoforge.enabledGameTestNamespaces', '$namespace' } }",[Text.UTF8Encoding]::new($false))
if(Test-Path -LiteralPath $fixturePath){throw 'Temporary fixtures already exist'}
New-Item -ItemType Directory -Path "$fixturePath\structure" | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'src\main\resources\data\aicompanion\structure\empty.nbt'),(Join-Path $projectRoot 'src\main\resources\data\aicompanion\structure\tall_tree.nbt') -Destination "$fixturePath\structure"
$runs=@()
Push-Location $projectRoot
try {
    foreach($path in $sources.Keys){[IO.File]::WriteAllText($path,$sources[$path].Replace('@GameTestHolder(AICompanionMod.MOD_ID)',"@GameTestHolder(`"$namespace`" )"),[Text.UTF8Encoding]::new($false))}
    for($i=1;$i -le $Repetitions;$i++) {
        $started=Get-Date
        $log=Join-Path $reports "banter-targeted-$i.txt"
        $ErrorActionPreference='Continue'
        try { & .\gradlew.bat --init-script $init runGameTestServer *> $log; $testExit=$LASTEXITCODE }
        finally {$ErrorActionPreference='Stop'}
        $output=Get-Content -LiteralPath $log -Raw
        $match=[regex]::Match($output,'All (\d+) required tests passed')
        $runs+=[ordered]@{iteration=$i;passed=if($match.Success){[int]$match.Groups[1].Value}else{0};exitCode=$testExit;elapsedSeconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2);log=$log}
        [IO.File]::WriteAllText((Join-Path $reports 'banter-targeted-summary.json'),(ConvertTo-Json -InputObject @($runs) -Depth 5),[Text.UTF8Encoding]::new($false))
        if($testExit -ne 0 -or !$match.Success){throw "Focused run $i failed: $log"}
        Write-Output "Focused run $i passed: $($match.Groups[1].Value)"
    }
} finally {
    foreach($path in $sources.Keys){[IO.File]::WriteAllText($path,$sources[$path],[Text.UTF8Encoding]::new($false))}
    $resolved=(Resolve-Path -LiteralPath $fixturePath).Path
    if($resolved -ne (Join-Path $projectRoot 'src\main\resources\data\aicompanion_banter')){throw 'Unexpected cleanup path'}
    Remove-Item -LiteralPath $resolved -Recurse -Force
    Pop-Location
}
