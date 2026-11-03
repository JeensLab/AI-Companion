# Trips of Oleg over generated terrain: a real client in disposable normal worlds, no provider keys, no paid requests.
param([long[]]$Seeds=@(1,20261102,987654321),[int]$TimeoutMinutes=15)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$reports=Join-Path $root 'build/diagnostics'
$client=Join-Path $root 'build/home-terrain-client'
New-Item -ItemType Directory -Path $reports,$client -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $client 'saves') -Directory -Filter 'OlegHome-*' -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
[IO.File]::WriteAllText((Join-Path $client 'options.txt'),"guiScale:2`nrenderDistance:6`nsimulationDistance:6`nmaxFps:30`npauseOnLostFocus:false`nonboardAccessibility:false`nlang:ru_ru`ntutorialStep:none`n",[Text.UTF8Encoding]::new($false))
$failed=@()
foreach($seed in $Seeds) {
    $init=Join-Path $reports "home-terrain-$seed.init.gradle"
    $text=@"
gradle.projectsEvaluated {
    rootProject.neoForge.runs.client {
        gameDirectory = rootProject.file('build/home-terrain-client')
        systemProperty 'aicompanion.homeProbe', 'true'
        systemProperty 'aicompanion.homeProbeSeed', '$seed'
    }
}
"@
    [IO.File]::WriteAllText($init,$text,[Text.UTF8Encoding]::new($false))
    $log=Join-Path $reports "home-terrain-$seed.txt"
    $job=Start-Job -ScriptBlock {
        param($dir,$init,$log)
        # Gradle writes UTF-8; a job would read it in the OEM code page and garble Oleg's Russian lines.
        [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
        Set-Location -LiteralPath $dir
        & ./gradlew.bat --offline --no-configuration-cache --init-script $init runClient *> $log
    } -ArgumentList $root,$init,$log
    # A client that hangs on its way out never ends the Gradle run: stop this checkout's game after the timeout.
    if(!(Wait-Job $job -Timeout ($TimeoutMinutes*60))) {
        Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" |
            Where-Object { $_.CommandLine -like "*-Dfml.modFolders=aicompanion%%$root\build\*" } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
        Wait-Job $job -Timeout 120 | Out-Null
        Write-Output "HOME_TERRAIN_PROBE_TIMEOUT: seed=$seed after $TimeoutMinutes min"
    }
    Remove-Job $job -Force
    Select-String -LiteralPath $log -Pattern 'HOME_TERRAIN_' | ForEach-Object { $_.Line }
    if(!(Select-String -LiteralPath $log -Pattern 'HOME_TERRAIN_PROBE_DONE:.*failed=\[\]' -Quiet)) { $failed+=$seed }
}
if($failed.Count -gt 0) { throw "Home terrain probe failed for seeds: $($failed -join ', ')" }
Write-Output "Home terrain verified. Screenshots: $client/screenshots"
