#!/bin/bash
#
# Rotates this project's SONAR_TOKEN against the local SonarQube instance
# (http://localhost:9000, see docs/ops/sonarqube-local.md) and updates Keychain in place.
#
# Mechanism: SonarQube's user-token API (`/api/user_tokens/generate` and `/api/user_tokens/revoke`),
# authenticated with the *current* token (a valid User Token has the same permissions as the user
# it belongs to). Generate-new -> verify-new-works -> swap Keychain -> revoke-old, in that order,
# so a failure partway through never leaves you with zero working tokens.
#
# Usage: scripts/rotate-sonar-token.sh
#
# Full context: ROADMAP.md Phase 11 Task 10, ~/zed/second_brain/ops/runbooks/secret-rotation-runbook.md.

set -euo pipefail

SERVICE="college_executive_function"
KEY="SONAR_TOKEN"
HOST="http://localhost:9000"
NEW_NAME="local-gradle-$(date +%s)"

START_TS=$(date +%s)

OLD_TOKEN=$(security find-generic-password -a "$KEY" -s "$SERVICE" -w 2>/dev/null || true)
if [ -z "$OLD_TOKEN" ]; then
    echo "ERROR: $KEY not found in Keychain (service=$SERVICE) — nothing to rotate from." >&2
    exit 1
fi

# Find the current token's own name (needed to revoke it later) by matching lastConnectionDate
# against the one we're about to bump with this very call — SonarQube's token-list API doesn't
# return values, so this is the only correlation available without guessing.
BEFORE_JSON=$(curl -sf -u "${OLD_TOKEN}:" "${HOST}/api/user_tokens/search") \
    || { echo "ERROR: could not reach SonarQube at ${HOST}, or current $KEY is already invalid." >&2; exit 1; }
OLD_NAME=$(echo "$BEFORE_JSON" | jq -r '.userTokens | sort_by(.lastConnectionDate) | last | .name')
if [ -z "$OLD_NAME" ] || [ "$OLD_NAME" = "null" ]; then
    echo "ERROR: could not determine current token's name from ${HOST}/api/user_tokens/search response." >&2
    exit 1
fi
echo "Current token identified as '$OLD_NAME'."

echo "Generating new token '$NEW_NAME'..."
GEN_JSON=$(curl -sf -u "${OLD_TOKEN}:" -X POST "${HOST}/api/user_tokens/generate" --data-urlencode "name=${NEW_NAME}")
NEW_TOKEN=$(echo "$GEN_JSON" | jq -r '.token')
if [ -z "$NEW_TOKEN" ] || [ "$NEW_TOKEN" = "null" ]; then
    echo "ERROR: token generation returned no token value. Response: $GEN_JSON" >&2
    exit 1
fi

echo "Verifying new token authenticates..."
VERIFY_LOGIN=$(curl -sf -u "${NEW_TOKEN}:" "${HOST}/api/user_tokens/search" | jq -r '.login')
if [ -z "$VERIFY_LOGIN" ] || [ "$VERIFY_LOGIN" = "null" ]; then
    echo "ERROR: new token '$NEW_NAME' failed to authenticate — leaving old token '$OLD_NAME' untouched." >&2
    exit 1
fi
echo "New token verified (login=$VERIFY_LOGIN)."

echo "Updating Keychain (service=$SERVICE, account=$KEY)..."
security add-generic-password -a "$KEY" -s "$SERVICE" -w "$NEW_TOKEN" -U

echo "Revoking old token '$OLD_NAME'..."
curl -sf -u "${NEW_TOKEN}:" -X POST "${HOST}/api/user_tokens/revoke" --data-urlencode "name=${OLD_NAME}" >/dev/null

END_TS=$(date +%s)
echo "Done in $((END_TS - START_TS))s. Old token '$OLD_NAME' revoked, new token '$NEW_NAME' live in Keychain."
echo "Run './gradlew :composeApp:checkQualityGate' to confirm the build picks up the new value end-to-end."
