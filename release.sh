#!/bin/bash

# CEF Release Script
# Usage: ./release.sh <version>   e.g. ./release.sh 1.0.14
# - Updates cef.versionName in gradle.properties
# - Updates MARKETING_VERSION in iosApp/Configuration/Config.xcconfig (kept in sync with
#   Android — these two drifted once already: gradle.properties reached 2.0.1 while
#   Config.xcconfig stayed at 2.0.0, which would have shipped a mismatched version to
#   App Store Connect)
# - Increments cef.versionCode (Android's Play-Store build number; must strictly increase
#   on every uploaded artifact, independent of versionName)
# - Commits the bump (skipped if already at that version)
# - Pushes the commit
# - Tags vX.Y.Z and pushes the tag → triggers Release Desktop (JVM) workflow

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

VERSION="$1"

if [ -z "$VERSION" ]; then
    CURRENT=$(grep -E '^cef\.versionName=' gradle.properties | cut -d'=' -f2 | tr -d '[:space:]')
    echo -e "${RED}✗ No version supplied.${NC}"
    echo "  Usage: ./release.sh <version>"
    echo "  Current: $CURRENT"
    exit 1
fi

if ! echo "$VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo -e "${RED}✗ Version must be X.Y.Z (got: $VERSION)${NC}"
    exit 1
fi

TAG="v$VERSION"

# Only tracked file changes block a release; untracked files are ignored
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo -e "${YELLOW}⚠ Working tree has uncommitted changes — commit or stash before releasing.${NC}"
    git status --short --untracked-files=no
    exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo -e "${RED}✗ Tag $TAG already exists.${NC}"
    exit 1
fi

# A warm local Gradle cache already has every dependency resolved+verified, so it can pass
# clean while CI's fresh checkout (Xcode Cloud, Release Desktop, Deploy Android) fails
# dependency verification on an artifact this machine happened to have cached already — hit
# twice now (v3.0.0: commit 86dd62c, v3.0.3: commit ca196f4), both times only discovered
# after the tag was already pushed. --refresh-dependencies forces real verification against
# every one of the 4 build targets before the tag goes out, not just the 3
# assembleDebug/iosApp/server set the Build Verification Protocol checked before — that set
# never touched :androidApp at all, which is exactly what both prior failures needed.
echo "--------------------------------------------------"
echo "Verifying dependencies across all 4 build targets (fresh-checkout simulation)..."
echo "--------------------------------------------------"
if ! ./gradlew :composeApp:assembleDebug :iosApp:assemble :server:assemble :androidApp:assembleDebug --refresh-dependencies; then
    echo -e "${RED}✗ Dependency verification or build failed — fix before releasing (see gradle/verification-metadata.xml).${NC}"
    exit 1
fi
echo -e "${GREEN}✓ All 4 build targets verified${NC}"

echo "--------------------------------------------------"
echo "Releasing $TAG"
echo "--------------------------------------------------"

# 1. Bump version in gradle.properties
sed -i '' "s/^cef\.versionName=.*/cef.versionName=$VERSION/" gradle.properties

CURRENT_VERSION_CODE=$(grep -E '^cef\.versionCode=' gradle.properties | cut -d'=' -f2 | tr -d '[:space:]')
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
sed -i '' "s/^cef\.versionCode=.*/cef.versionCode=$NEW_VERSION_CODE/" gradle.properties
echo -e "${GREEN}✓ Bumped cef.versionCode $CURRENT_VERSION_CODE → $NEW_VERSION_CODE${NC}"

# 1b. Keep iOS's marketing version in sync with Android's versionName
sed -i '' "s/^MARKETING_VERSION=.*/MARKETING_VERSION=$VERSION/" iosApp/Configuration/Config.xcconfig
echo -e "${GREEN}✓ Synced iOS MARKETING_VERSION to $VERSION${NC}"

# 2. Commit only if the version actually changed
git add gradle.properties iosApp/Configuration/Config.xcconfig
if ! git diff --cached --quiet; then
    git commit -m "version bump to $VERSION"
    echo -e "${GREEN}✓ Committed version bump${NC}"

    git push origin "$(git rev-parse --abbrev-ref HEAD)"
    echo -e "${GREEN}✓ Pushed commit${NC}"
else
    echo -e "${YELLOW}  gradle.properties already at $VERSION — skipping bump commit${NC}"
fi

# 3. Tag and push to trigger the release workflow
git tag -a "$TAG" -m "Release $TAG"
git push origin "$TAG"
echo -e "${GREEN}✓ Pushed tag $TAG — Release Desktop (JVM) workflow is now running.${NC}"
