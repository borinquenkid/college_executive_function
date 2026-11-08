#!/usr/bin/env bash
# Report-only Pitest mutation run on the JVM target (T9). See docs/ops/mutation-testing-pilot.md.
# Usage: scripts/mutation/pit.sh '<target class glob>' '<test class glob>' [reportDir]
# e.g.   scripts/mutation/pit.sh 'com.borinquenterrier.cef.Bm25Ranker' 'com.borinquenterrier.cef.Bm25RankerTest'
set -euo pipefail
cd "$(dirname "$0")/../.."
TARGET="${1:?target class glob}"; TESTS="${2:?test class glob}"
OUT="${3:-build/pit-report}"
TOOLS="${CEF_PIT_TOOLS:-$HOME/.cache/cef-pitest}"
mkdir -p "$TOOLS" "$OUT"

fetch() { # group/path artifact version  (sha1-verified against Maven Central)
  local jar="$TOOLS/$2-$3.jar" url="https://repo1.maven.org/maven2/$1/$2/$3/$2-$3.jar"
  [ -f "$jar" ] && return 0
  curl -sfo "$jar" "$url"
  [ "$(curl -sf "$url.sha1")" = "$(shasum "$jar" | cut -d' ' -f1)" ] || { rm -f "$jar"; echo "sha1 mismatch: $2" >&2; exit 1; }
}
fetch org/pitest pitest 1.30.2; fetch org/pitest pitest-entry 1.30.2
fetch org/pitest pitest-command-line 1.30.2; fetch org/pitest pitest-junit5-plugin 1.2.3
fetch org/apache/commons commons-text 1.13.1; fetch org/apache/commons commons-lang3 3.17.0

PIT_OUT="$OUT/cp.txt" ./gradlew --init-script scripts/mutation/cp.init.gradle :composeApp:printJvmTestCp -q --no-configuration-cache >/dev/null
R="$PWD/composeApp"
EXTRA=$(ls "$TOOLS"/commons-*.jar | tr '\n' ',')
CP="$R/build/classes/kotlin/jvm/test,$EXTRA$(tr -d '\n' < "$OUT/cp.txt" | tr ':' ',')"
JARS=$(ls "$TOOLS"/*.jar | tr '\n' ':')
java -cp "$JARS" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$OUT" --targetClasses "$TARGET*" --excludedClasses '*Test*' --targetTests "$TESTS*" \
  --sourceDirs "$R/src/commonMain/kotlin" --classPath "$CP" --threads 2 \
  --outputFormats XML,HTML --timestampedReports false 2>&1 | grep -E "Generated [0-9]+ mutations|Line Coverage|Test strength|Exception|SEVERE" | tail -5
echo "report: $OUT/index.html  (survivors: $OUT/mutations.xml)"
