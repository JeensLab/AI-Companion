param([ValidateRange(1, 20)][int]$Repetitions = 1)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$reportDirectory = Join-Path $projectRoot 'build/diagnostics'
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$runs = @()
Push-Location $projectRoot
try {
    for ($iteration = 1; $iteration -le $Repetitions; $iteration++) {
        $outputPath = Join-Path $reportDirectory "verification-$iteration.txt"
        $started = Get-Date
        # Windows PowerShell treats native stderr as ErrorRecord; warnings must not
        # terminate the harness before the real process exit code is inspected.
        $ErrorActionPreference = 'Continue'
        try { & .\gradlew.bat runGameTestServer *> $outputPath; $exitCode = $LASTEXITCODE }
        finally { $ErrorActionPreference = 'Stop' }
        $output = Get-Content -LiteralPath $outputPath -Raw
        $passed = [regex]::Match($output, 'All (\d+) required tests passed')
        $failed = [regex]::Matches($output, 'LogTestReporter[^\r\n]*?\]: ([^\r\n]+) failed at') | ForEach-Object { $_.Groups[1].Value }
        $metrics = Join-Path $reportDirectory "verification-endurance-$iteration"
        if (Test-Path -LiteralPath 'run/endurance') {
            New-Item -ItemType Directory -Path $metrics -Force | Out-Null
            Get-ChildItem -LiteralPath 'run/endurance' -Filter '*.json' | Copy-Item -Destination $metrics
        }
        $runs += [ordered]@{ iteration=$iteration; exitCode=$exitCode; elapsedSeconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2); passed=if($passed.Success){[int]$passed.Groups[1].Value}else{$null}; failed=@($failed); log=$outputPath; metrics=$metrics }
        # Which code was checked: the commit (and «+changes» if the tree was dirty) and the JAR built from it, if any.
        $commit = (git rev-parse --short HEAD 2>$null); if (git status --porcelain 2>$null) { $commit = "$commit+changes" }
        $jar = Join-Path $projectRoot 'build/libs/aicompanion-0.0.1.jar'
        $jarHash = if (Test-Path -LiteralPath $jar) { (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash } else { $null }
        $json = [ordered]@{ modVersion='0.0.1'; commit=$commit; jarSha256=$jarHash; scope='local deterministic GameTests; no LLM, voice or 10-hour stress certification'; runs=$runs } |
            ConvertTo-Json -Depth 6
        # UTF-8 without BOM, so other tools read the JSON as is.
        [System.IO.File]::WriteAllText((Join-Path $reportDirectory 'verification-summary.json'), $json, (New-Object System.Text.UTF8Encoding $false))
        if ($exitCode -ne 0 -or !$passed.Success) { throw "Scenario verification failed. See $outputPath" }
        Write-Host "Run $iteration passed: $($passed.Groups[1].Value) scenarios."
    }
} finally { Pop-Location }
