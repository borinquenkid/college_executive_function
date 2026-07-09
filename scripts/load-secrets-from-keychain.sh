#!/bin/bash
#
# Sources this project's secrets from macOS Keychain into the current shell as env vars.
# Usage: `source scripts/load-secrets-from-keychain.sh` before ./gradlew (or before launching
# Android Studio from this same shell — IDE-launched Gradle runs don't inherit this otherwise,
# see docs/ops/keychain-secrets-migration.md's "Known limitation" section).
#
# Fails loudly (returns non-zero, does not export a blank value) if a key is missing —
# deliberately no silent fallback to a plaintext .env value. See that same doc for why.

SERVICE="college_executive_function"
SECRET_KEYS=(
    GOOGLE_CLIENT_SECRET
    CEF_GEMINI_API_KEY
    CEF_OTLP_PASSWORD
    CEF_TEST_USER_API_KEY
    OOC_TOKEN
    SONAR_TOKEN
)

_missing=()
for key in "${SECRET_KEYS[@]}"; do
    value=$(security find-generic-password -a "$key" -s "$SERVICE" -w 2>/dev/null)
    if [ -z "$value" ]; then
        _missing+=("$key")
        continue
    fi
    export "$key=$value"
done

if [ ${#_missing[@]} -gt 0 ]; then
    echo "ERROR: missing from Keychain (service=$SERVICE): ${_missing[*]}" >&2
    echo "Run scripts/migrate-secrets-to-keychain.sh, or add manually with:" >&2
    echo "  security add-generic-password -a KEY_NAME -s $SERVICE -w VALUE" >&2
    return 1 2>/dev/null || exit 1
fi

echo "Loaded ${#SECRET_KEYS[@]} secret(s) from Keychain (service=$SERVICE)."
