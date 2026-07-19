#!/bin/sh
set -e

# Xcode Cloud's build VM has no JDK, Gradle, or SDKMAN preinstalled, but the
# "Compile Kotlin Framework" Run Script build phase (iosApp.xcodeproj) shells
# out to `sdk env` + `./gradlew` to build the shared Kotlin Multiplatform
# framework before Xcode ever compiles Swift. Without this, that phase fails
# with "sdk: command not found" / "Unable to locate a Java Runtime."
export SDKMAN_DIR="$HOME/.sdkman"

# SDKMAN's own servers occasionally drop mid-download on Xcode Cloud's network
# (seen live: "curl: (35) Recv failure: Connection reset by peer" on the
# installer's second archive) -- retry a few times before giving up rather
# than failing the whole build on one transient blip.
retry() {
  n=0
  max=3
  until [ "$n" -ge "$max" ]; do
    if "$@"; then
      return 0
    fi
    n=$((n + 1))
    echo "Attempt $n/$max failed, retrying in 5s..." >&2
    sleep 5
  done
  return 1
}

if [ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
  # SDKMAN's installer (unlike the `sdk` command itself) hard-requires bash 4+
  # and refuses to run under Apple's stock bash 3.2. Xcode Cloud images ship
  # Homebrew preinstalled, so borrow its bash just for this one-time install.
  brew install bash
  BREW_BASH="$(brew --prefix)/bin/bash"
  retry sh -c 'curl -s "https://get.sdkman.io" | "'"$BREW_BASH"'"'
fi
# shellcheck disable=SC1091
source "$SDKMAN_DIR/bin/sdkman-init.sh"

cd "$CI_PRIMARY_REPOSITORY_PATH"

# .sdkmanrc pins the exact JDK this project builds with (kept in sync with
# gradle/libs.versions.toml's toolchain) — install it, then `sdk env` in the
# Run Script phase picks it up automatically since it cd's to the repo root.
JAVA_VERSION=$(grep '^java=' .sdkmanrc | cut -d'=' -f2)
retry sdk install java "$JAVA_VERSION" < /dev/null || true

chmod +x ./gradlew
