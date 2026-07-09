#!/bin/bash
#
# One-time migration: reads this project's actual secrets from .env and stores them in
# macOS Keychain, namespaced by repo name. See docs/ops/keychain-secrets-migration.md.
#
# Deliberately migrates only secret-shaped values (credentials/tokens/keys) — plain config
# (CALENDAR_ID, SEMESTER_START_DATE, client IDs that are already embedded in the app binary)
# stays in .env, since Keychain migration exists to protect secrets, not general config.
#
# Safe to re-run — uses -U (update in place) so it never errors on an existing entry.

set -euo pipefail
cd "$(dirname "$0")/.."

SERVICE="college_executive_function"
SECRET_KEYS=(
    GOOGLE_CLIENT_SECRET
    CEF_GEMINI_API_KEY
    CEF_OTLP_PASSWORD
    CEF_TEST_USER_API_KEY
    OOC_TOKEN
    SONAR_TOKEN
)

if [ ! -f .env ]; then
    echo "No .env file found — nothing to migrate." >&2
    exit 1
fi

for key in "${SECRET_KEYS[@]}"; do
    value=$(grep -E "^${key}=" .env | head -1 | cut -d'=' -f2-)
    if [ -z "$value" ]; then
        echo "  SKIP  $key — not set (or already empty) in .env"
        continue
    fi
    security add-generic-password -a "$key" -s "$SERVICE" -w "$value" -U 2>/dev/null
    echo "  OK    $key -> Keychain (service=$SERVICE)"
done

echo ""
echo "Migration complete. Values NOT yet removed from .env — verify with the loader script"
echo "and the integration test before redacting (see docs/ops/keychain-secrets-migration.md)."
