#!/bin/sh
set -e

# Xcode Cloud's build VM has no JDK, Gradle, or SDKMAN preinstalled, but the
# "Compile Kotlin Framework" Run Script build phase (iosApp.xcodeproj) shells
# out to `sdk env` + `./gradlew` to build the shared Kotlin Multiplatform
# framework before Xcode ever compiles Swift. Without this, that phase fails
# with "sdk: command not found" / "Unable to locate a Java Runtime."
export SDKMAN_DIR="$HOME/.sdkman"

if [ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
  curl -s "https://get.sdkman.io" | bash
fi
# shellcheck disable=SC1091
source "$SDKMAN_DIR/bin/sdkman-init.sh"

cd "$CI_PRIMARY_REPOSITORY_PATH"

# .sdkmanrc pins the exact JDK this project builds with (kept in sync with
# gradle/libs.versions.toml's toolchain) — install it, then `sdk env` in the
# Run Script phase picks it up automatically since it cd's to the repo root.
JAVA_VERSION=$(grep '^java=' .sdkmanrc | cut -d'=' -f2)
sdk install java "$JAVA_VERSION" < /dev/null || true

chmod +x ./gradlew
