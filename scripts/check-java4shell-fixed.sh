#!/bin/bash
#
# Temporary check for CVE-2026-114938 ("Java4Shell"): has a fixed JDK landed in the Docker image
# the production server runs on?
#
# server/Dockerfile carries the -XX:-UseBASE64Intrinsics workaround because eclipse-temurin:21-jre
# was still on a vulnerable build when the CVE was handled (2026-11-18). This script pulls each
# production image for linux/amd64, reads `java -version` from inside it (never the tag name), and
# compares it to the first fixed version of that JDK line.
#
# Usage:
#   ./scripts/check-java4shell-fixed.sh              # images = final FROM of server/Dockerfile
#   ./scripts/check-java4shell-fixed.sh IMAGE...     # check the given images instead
#
# Exit codes:
#   0   every image is on a fixed JDK  -> pin the tag, remove the flag, delete this check
#   10  at least one image is still vulnerable (the expected answer until the image is rebuilt)
#   any other code: the check itself broke (pull failed, version unparseable, unknown JDK line)
#
# Delete this script and .github/workflows/java4shell-image-check.yml once the image is pinned.

set -euo pipefail
cd "$(dirname "$0")/.."

# Dockerfiles whose final stage is what runs in production.
PRODUCTION_DOCKERFILES="server/Dockerfile"

# First fixed version per JDK feature line (vendor advisories for CVE-2026-114938).
fixed_floor() {
    case "$1" in
        17) echo "17.0.21.1" ;;
        21) echo "21.0.13.1" ;;
        25) echo "25.0.5.1" ;;
        27) echo "27.0.1.1" ;;
        *)  return 1 ;;
    esac
}

# version_at_least A B -> true when dotted version A >= B (missing components count as 0).
version_at_least() {
    local IFS=.
    local -a a=($1) b=($2)
    local i
    for i in 0 1 2 3 4 5; do
        local x=${a[i]:-0} y=${b[i]:-0}
        if ((10#$x > 10#$y)); then return 0; fi
        if ((10#$x < 10#$y)); then return 1; fi
    done
    return 0
}

if [ "$#" -gt 0 ]; then
    images=("$@")
else
    images=()
    for dockerfile in $PRODUCTION_DOCKERFILES; do
        # Last FROM = the runtime stage. "FROM [--platform=x] image [AS name]".
        image=$(awk 'toupper($1) == "FROM" { img = ($2 ~ /^--/) ? $3 : $2 } END { print img }' "$dockerfile")
        if [ -z "$image" ]; then
            echo "ERROR: no FROM line found in $dockerfile" >&2
            exit 2
        fi
        images+=("$image")
    done
fi

pending=0
for image in "${images[@]}"; do
    if ! output=$(docker run --rm --pull always --platform linux/amd64 "$image" java -version 2>&1); then
        echo "ERROR: could not run java -version in $image:" >&2
        echo "$output" >&2
        exit 2
    fi
    # e.g.  openjdk version "21.0.13.1" 2026-11-17 LTS
    version=$(echo "$output" | sed -n 's/^.* version "\([0-9][0-9.]*\)".*$/\1/p' | head -n 1)
    if [ -z "$version" ]; then
        echo "ERROR: could not parse a version from java -version in $image:" >&2
        echo "$output" >&2
        exit 2
    fi
    if ! floor=$(fixed_floor "${version%%.*}"); then
        echo "ERROR: $image runs JDK $version — no fixed floor known for that JDK line." >&2
        exit 2
    fi
    if version_at_least "$version" "$floor"; then
        echo "FIXED    $image  java $version  (fixed floor $floor)"
    else
        echo "PENDING  $image  java $version  (fixed floor $floor)"
        pending=1
    fi
done

if [ "$pending" -ne 0 ]; then
    exit 10
fi
exit 0
