#!/bin/bash

# CEF Release Script
# Usage: ./release.sh <version>   e.g. ./release.sh 1.0.14
# - Updates cef.versionName in gradle.properties
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

echo "--------------------------------------------------"
echo "Releasing $TAG"
echo "--------------------------------------------------"

# 1. Bump version in gradle.properties
sed -i '' "s/^cef\.versionName=.*/cef.versionName=$VERSION/" gradle.properties

# 2. Commit only if the version actually changed
git add gradle.properties
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
