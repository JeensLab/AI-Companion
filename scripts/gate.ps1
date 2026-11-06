# The single pre-PR gate for Windows: one command, one summary line, nonzero exit on any failure.
# Steps, in order: source-tree guard, git diff --check, optional shape checks, Gradle build, GameTests.
# Usage: .\scripts\gate.ps1 [-Fast]
#   -Fast  skips the GameTests. For local iteration only: CI and the final pre-PR run must never use it.
# Env GATE_GRADLE_ARGS passes extra arguments to the Gradle build (for example --offline).
param([switch]$Fast,[Parameter(ValueFromRemainingArguments)][string[]]$Rest)
$ErrorActionPreference='Stop'
if($Rest){
    [Console]::Error.WriteLine("unknown argument: $($Rest[0])")
    Write-Host "GATE FAIL: usage"
    exit 2
}
$root=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$psHost=(Get-Process -Id $PID).Path

function Stop-Gate([string]$Step) {
    Write-Host "GATE FAIL: $Step"
    exit 1
}

# 1. Guard: a build over an empty source tree is green and proves nothing.
if(-not (Get-ChildItem -LiteralPath 'src/main/java' -Filter '*.java' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1)){
    [Console]::Error.WriteLine('src/main/java has no .java file: the build must not pass on an empty tree')
    Stop-Gate 'source-guard'
}
$steps='source-guard'

# 2. Whitespace errors, in the working tree and in the committed range.
Write-Host '== diff-check'
& git diff --check HEAD | Out-Host
if($LASTEXITCODE -ne 0){Stop-Gate 'diff-check (working tree)'}
& git rev-parse --verify -q origin/main *> $null
if($LASTEXITCODE -eq 0){
    $base=(& git merge-base origin/main HEAD)
    if($LASTEXITCODE -ne 0){Stop-Gate 'diff-check (merge base)'}
    Write-Host "range: $($base.Substring(0,10))..HEAD (merge base with origin/main)"
    & git diff --check $base HEAD | Out-Host
    if($LASTEXITCODE -ne 0){Stop-Gate 'diff-check (range)'}
} elseif($env:CI){
    [Console]::Error.WriteLine('origin/main is missing in CI: the committed range cannot be checked')
    Stop-Gate 'diff-check (origin/main missing)'
} else {
    & git rev-parse --verify -q 'HEAD~1' *> $null
    if($LASTEXITCODE -eq 0){
        Write-Host 'WARNING: origin/main is not available: checking only HEAD~1..HEAD'
        & git diff --check HEAD~1 HEAD | Out-Host
        if($LASTEXITCODE -ne 0){Stop-Gate 'diff-check (range)'}
    } else {
        Write-Host 'WARNING: origin/main and HEAD~1 are not available: the committed range is not checked'
    }
}
$steps="$steps, diff-check"

# 3. Shape checks run only when their script exists, so later stages add them without editing the gate.
$shapeRun=0
foreach($name in 'code-shape','switch-check','arch-check','catch-check','magic-numbers','line-check','test-quality'){
    $script="scripts/$name.ps1"
    if(Test-Path -LiteralPath $script){
        Write-Host "== $name"
        & $psHost -NoProfile -File $script | Out-Host
        if($LASTEXITCODE -ne 0){Stop-Gate $name}
        $shapeRun++
    } else {
        Write-Host "skip: $script (not present)"
    }
}
$steps="$steps, $shapeRun shape checks"

# 4. Build. It compiles and packs the JAR but runs no tests.
Write-Host '== build'
$gradleArgs=@('build')
if($env:GATE_GRADLE_ARGS){$gradleArgs+=@($env:GATE_GRADLE_ARGS -split '\s+' | Where-Object {$_})}
& ./gradlew.bat @gradleArgs | Out-Host
if($LASTEXITCODE -ne 0){Stop-Gate 'build'}
$steps="$steps, build"

# 5. GameTests through the sharded runner, which counts shards honestly and prints failed test names.
if($Fast){
    Write-Host 'GameTests skipped (-Fast)'
    $steps="$steps, gametest SKIPPED"
} else {
    Write-Host '== gametest'
    $summaryFile=Join-Path $root 'build/diagnostics/fast-summary.json'
    Remove-Item -LiteralPath $summaryFile -Force -ErrorAction SilentlyContinue
    & $psHost -NoProfile -File (Join-Path $PSScriptRoot 'verify-fast.ps1') | Out-Host
    if($LASTEXITCODE -ne 0){Stop-Gate 'gametest'}
    $runs=@(Get-Content -Raw -LiteralPath $summaryFile | ConvertFrom-Json)
    $last=$runs[-1]
    if($null -eq $last.passed -or $last.passed -ne $last.expected){Stop-Gate 'gametest (passed count differs from expected)'}
    $steps="$steps, gametest $($last.passed)/$($last.expected)"
}

Write-Host "GATE PASS ($steps)"
