param(
    [Parameter(Mandatory)][ValidateRange(1,200)][int]$Expected,
    [int]$Full=0,
    [switch]$Banter,
    [string]$Tag='memory'
)
$ErrorActionPreference='Stop'
$env:JAVA_HOME='D:\Program Files\Java\jdk-22'
$projectRoot=Split-Path -Parent $PSScriptRoot
$namespace='aicompanion_memory'
$testClasses=@('CompanionMemoryTests','CompanionBondTests','CompanionHappeningsTests')
$knownFlaky=@('woodtoolschestandawalk','hundredminutesfullbagorderscancellationandreloads','ninetyminutescavefoodorelightandchangingpassage','escapessoilpitwithoutfreestartingblocks')
$reports=Join-Path $projectRoot 'build\diagnostics'
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$utf8=[Text.UTF8Encoding]::new($false)

$oversized=Get-ChildItem (Join-Path $projectRoot 'src\main\java\dev\aicompanion\entity\*.java') | Where-Object { (Get-Content -LiteralPath $_.FullName).Count -gt 800 }
if($oversized){ $oversized | ForEach-Object { Write-Output "FAIL $($_.Name) has $((Get-Content -LiteralPath $_.FullName).Count) lines > 800" }; exit 1 }
Write-Output 'PASS entity/*.java all <= 800 lines'

function Invoke-Gradle([string[]]$GradleArgs,[string]$Log) {
    $ErrorActionPreference='Continue'
    try { & .\gradlew.bat @GradleArgs *> $Log; return $LASTEXITCODE } finally { $ErrorActionPreference='Stop' }
}

Push-Location $projectRoot
try {
    $buildLog=Join-Path $reports "$Tag-build.txt"
    if(Invoke-Gradle @('build') $buildLog){
        Write-Output "FAIL build (see $buildLog)"
        Select-String -LiteralPath $buildLog -Pattern 'error:|FAILED|What went wrong' -Context 0,3 | Select-Object -First 30 | ForEach-Object { $_.ToString() }
        exit 1
    }
    Write-Output 'PASS build'

    # Focused run: only the plan's new test classes, in their own gametest namespace.
    $sources=@{}
    foreach($name in $testClasses){
        $path=Join-Path $projectRoot "src\main\java\dev\aicompanion\gametest\$name.java"
        if(Test-Path -LiteralPath $path){$sources[$path]=[IO.File]::ReadAllText($path)}
    }
    if($sources.Count -eq 0){Write-Output "FAIL no new test classes ($($testClasses -join ', ')) exist"; exit 1}
    foreach($path in $sources.Keys){ if(!$sources[$path].Contains('@GameTestHolder(AICompanionMod.MOD_ID)')){Write-Output "FAIL $path lacks @GameTestHolder(AICompanionMod.MOD_ID)"; exit 1} }
    $fixturePath=Join-Path $projectRoot "src\main\resources\data\$namespace"
    if(Test-Path -LiteralPath $fixturePath){throw 'Temporary fixtures already exist'}
    $init=Join-Path $reports 'memory-only.init.gradle'
    [IO.File]::WriteAllText($init,"gradle.projectsEvaluated { rootProject.neoForge.runs.gameTestServer { systemProperty 'neoforge.enabledGameTestNamespaces', '$namespace' } }",$utf8)
    New-Item -ItemType Directory -Path "$fixturePath\structure" | Out-Null
    Copy-Item -Path (Join-Path $projectRoot 'src\main\resources\data\aicompanion\structure\*.nbt') -Destination "$fixturePath\structure"
    $focusedLog=Join-Path $reports "$Tag-focused.txt"
    try {
        foreach($path in $sources.Keys){[IO.File]::WriteAllText($path,$sources[$path].Replace('@GameTestHolder(AICompanionMod.MOD_ID)',"@GameTestHolder(`"$namespace`")"),$utf8)}
        $null=Invoke-Gradle @('--init-script',$init,'runGameTestServer') $focusedLog
    } finally {
        foreach($path in $sources.Keys){[IO.File]::WriteAllText($path,$sources[$path],$utf8)}
        $resolved=(Resolve-Path -LiteralPath $fixturePath).Path
        if($resolved -ne (Join-Path $projectRoot "src\main\resources\data\$namespace")){throw 'Unexpected cleanup path'}
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    $focused=Get-Content -LiteralPath $focusedLog -Raw
    $m=[regex]::Match($focused,'All (\d+) required tests passed')
    if(!$m.Success -or [int]$m.Groups[1].Value -ne $Expected){
        $got=if($m.Success){"all $($m.Groups[1].Value) passed"}else{'not all passed'}
        Write-Output "FAIL focused: expected exactly $Expected passed, $got (see $focusedLog)"
        Select-String -LiteralPath $focusedLog -Pattern 'failed at|required tests failed|error:|Exception' | Select-Object -First 30 | ForEach-Object { $_.Line }
        exit 1
    }
    Write-Output "PASS focused: All $Expected required tests passed"

    if($Full -gt 0){
        $passed=$false
        for($attempt=1;$attempt -le 2 -and !$passed;$attempt++){
            $fullLog=Join-Path $reports "$Tag-full-$attempt.txt"
            $null=Invoke-Gradle @('runGameTestServer') $fullLog
            if(Select-String -LiteralPath $fullLog -Pattern "All $Full required tests passed" -Quiet){$passed=$true;Write-Output "PASS full (attempt $attempt): All $Full required tests passed";break}
            $failed=@(Select-String -LiteralPath $fullLog -Pattern 'LogTestReporter\]: (\S+) failed at' | ForEach-Object { $_.Matches[0].Groups[1].Value } | Sort-Object -Unique)
            $oneRequired=Select-String -LiteralPath $fullLog -Pattern '\b1 required tests failed' -Quiet
            Write-Output "full attempt ${attempt}: failed [$($failed -join ', ')] (see $fullLog)"
            if($attempt -eq 1 -and $oneRequired -and $failed.Count -eq 1 -and $knownFlaky -contains $failed[0]){
                Write-Output "known flaky $($failed[0]) is the only red test; repeating once on the same tree"
                continue
            }
            Select-String -LiteralPath $fullLog -Pattern 'failed at|required tests failed|error:|Exception' | Select-Object -First 30 | ForEach-Object { $_.Line.Substring(0,[Math]::Min(400,$_.Line.Length)) }
            Write-Output "FAIL full: expected All $Full required tests passed"
            exit 1
        }
        if(!$passed){Write-Output 'FAIL full: flaky repeat was red'; exit 1}
    }

    if($Banter){
        try { & (Join-Path $PSScriptRoot 'verify-banter-tools.ps1') -Repetitions 3 }
        catch { Write-Output "FAIL banter: $($_.Exception.Message)"; exit 1 }
        Write-Output 'PASS banter x3'
    }
    Write-Output "PASS verify-memory: Expected=$Expected Full=$Full Banter=$Banter"
    exit 0
} finally { Pop-Location }
