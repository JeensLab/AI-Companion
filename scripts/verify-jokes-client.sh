#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/jokes-client build/diagnostics
touch build/jokes-client/.jokes-probe-disposable
if [[ ! -e build/jokes-client/options.txt ]]; then
    cat > build/jokes-client/options.txt <<'OPTIONS'
guiScale:3
renderDistance:5
simulationDistance:5
maxFps:30
pauseOnLostFocus:false
onboardAccessibility:false
lang:ru_ru
tutorialStep:none
OPTIONS
fi
cat > build/diagnostics/jokes-client.init.gradle <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.neoForge.runs.client {
        gameDirectory = rootProject.file('build/jokes-client')
        systemProperty 'aicompanion.jokeProbe', 'true'
        programArguments.addAll '--width', '1100', '--height', '720'
    }
}
GRADLE
log=build/diagnostics/jokes-client.log
./gradlew --offline --no-configuration-cache --init-script build/diagnostics/jokes-client.init.gradle runClient > "$log" 2>&1
case "$(< "$log")" in
    *JOKE_CLIENT_PROBE_FAILED*) echo "Joke client probe failed; see $log"; exit 1 ;;
    *JOKE_CLIENT_PROBE_PASSED*) echo "Joke client probe passed; see $log" ;;
    *) echo "Joke client probe did not report success; see $log"; exit 1 ;;
esac
