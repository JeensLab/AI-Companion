# Magic-number ratchet on changed production lines. Usage: .\scripts\magic-numbers.ps1 [-Base <ref>]
# Compares the working tree with the merge base of origin/main and HEAD (uncommitted and untracked files included).
param([string]$Base = "")

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$javaCmd = $null
if ($env:JAVA_HOME) {
    foreach ($name in "java.exe", "java") {
        $candidate = Join-Path $env:JAVA_HOME "bin/$name"
        if (Test-Path -LiteralPath $candidate) { $javaCmd = $candidate; break }
    }
}
if (-not $javaCmd) {
    $found = Get-Command java -ErrorAction SilentlyContinue
    if ($found) { $javaCmd = $found.Source }
}
if (-not $javaCmd) {
    [Console]::Error.WriteLine("magic-numbers: java not found; set JAVA_HOME to a JDK 21 or put java on PATH")
    exit 2
}

if (-not $Base) {
    git rev-parse --verify -q origin/main *> $null
    if ($LASTEXITCODE -eq 0) {
        $Base = (git merge-base origin/main HEAD).Trim()
    } elseif ($env:CI) {
        [Console]::Error.WriteLine("magic-numbers: origin/main is missing in CI; fetch it (e.g. fetch-depth: 0) or pass -Base <ref>")
        exit 2
    } else {
        [Console]::Error.WriteLine("magic-numbers: warning: origin/main is missing, comparing with HEAD~1")
        $Base = "HEAD~1"
    }
}

$diff = [System.Collections.Generic.List[string]]::new()
$diff.AddRange([string[]]@(git diff --no-color --no-ext-diff -U0 $Base -- src/main/java))
foreach ($file in @(git ls-files --others --exclude-standard -- src/main/java)) {
    $diff.AddRange([string[]]@(git diff --no-color --no-ext-diff -U0 --no-index -- /dev/null $file))
}

$diff | & $javaCmd scripts/MagicNumbers.java
exit $LASTEXITCODE
