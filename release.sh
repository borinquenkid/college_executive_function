#!/bin/bash

# CEF Release Tagging Script
# Reads cef.versionName from gradle.properties (the single source of truth for
# app version) and creates + pushes a matching git tag (vX.Y.Z), which triggers
# the Release Desktop (JVM) GitHub Actions workflow.

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

VERSION=$(grep -E '^cef\.versionName=' gradle.properties | cut -d'=' -f2 | tr -d '[:space:]')

if [ -z "$VERSION" ]; then
    echo -e "${RED}✗ Could not find cef.versionName in gradle.properties${NC}"
    exit 1
fi

TAG="v$VERSION"

echo "--------------------------------------------------"
echo "Preparing release $TAG (from cef.versionName=$VERSION)"
echo "--------------------------------------------------"

if [ -n "$(git status --porcelain)" ]; then
    echo -e "${YELLOW}⚠ Working tree has uncommitted changes — commit or stash before releasing.${NC}"
    exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo -e "${RED}✗ Tag $TAG already exists. Bump cef.versionName in gradle.properties first.${NC}"
    exit 1
fi

git tag -a "$TAG" -m "Release $TAG"
git push origin "$TAG"

echo -e "${GREEN}✓ Pushed tag $TAG — Release Desktop (JVM) workflow should now be running.${NC}"
