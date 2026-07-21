#!/bin/bash
#
# One-command setup for IT staff standing up their college's CEF server + web client.
# No programming knowledge required — this script checks prerequisites, builds the
# containers, waits for the server to come up, and prints what to do next.
#
# Usage:
#   ./scripts/setup-college-server.sh
#
# Safe to re-run any time (e.g. after `git pull` to update) — it rebuilds and restarts
# in place; it does not touch your data.

set -euo pipefail
cd "$(dirname "$0")/.."

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

info()  { echo -e "${GREEN}✓${NC} $1"; }
warn()  { echo -e "${YELLOW}⚠${NC} $1"; }
fail()  { echo -e "${RED}✗ $1${NC}"; exit 1; }

echo "--------------------------------------------------"
echo " CEF Server Setup"
echo "--------------------------------------------------"

# ── 1. Check Docker is installed and running ────────────────────────────────

if ! command -v docker >/dev/null 2>&1; then
    fail "Docker is not installed. Install Docker Desktop from https://www.docker.com/products/docker-desktop/ and run this script again."
fi

if ! docker info >/dev/null 2>&1; then
    fail "Docker is installed but not running. Start Docker Desktop and run this script again."
fi

if ! docker compose version >/dev/null 2>&1; then
    fail "Docker Compose is not available. Update Docker Desktop to a recent version and run this script again."
fi

info "Docker is installed and running."

# ── 2. .env file: required LTI registration + optional Google Calendar creds ───
#
# Unlike Google Calendar (optional — students can use the app fully without it), the
# CEF_APP_BASE_URL/CEF_LTI_* fields are REQUIRED: this deployment only accepts logins via a
# verified LTI 1.3 launch from your LMS (see docs/adr/0006-lti-1.3-only-auth.md), and the server
# refuses to start at all without them. See DEPLOYMENT.md's "Registering CEF as an LTI tool"
# section for where these values come from — your LMS admin screen provides them.

if [ ! -f .env ]; then
    cat > .env <<'EOF'
# REQUIRED — this deployment's externally-reachable HTTPS origin (e.g. https://cef.yourschool.edu,
# no trailing slash). LTI 1.3 requires HTTPS; put a TLS-terminating reverse proxy in front first.
CEF_APP_BASE_URL=

# REQUIRED — from your LMS's "add external tool" / developer key screen. See DEPLOYMENT.md's
# "Registering CEF as an LTI tool" section.
CEF_LTI_ISSUER=
CEF_LTI_CLIENT_ID=
CEF_LTI_DEPLOYMENT_IDS=
CEF_LTI_AUTH_LOGIN_URL=
CEF_LTI_JWKS_URL=

# Optional — only needed to refresh an ALREADY-linked Google account (e.g. one linked via the
# desktop app). Leave blank to skip for now; you can add these and re-run this script at any time.
# See README.md's "Google Cloud Console & API Setup" section, step 3, for how to get these.
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# Optional — lets students link Google Calendar themselves from the web app (no desktop app
# needed). A DIFFERENT OAuth client than the one above — see README.md step 3b and
# docs/adr/0008-self-serve-google-oauth-web-flow.md.
CEF_GOOGLE_WEB_CLIENT_ID=
CEF_GOOGLE_WEB_CLIENT_SECRET=
EOF
    fail "Created .env — fill in the REQUIRED CEF_APP_BASE_URL/CEF_LTI_* fields (see DEPLOYMENT.md's \"Registering CEF as an LTI tool\" section), then re-run this script. Google Calendar fields can stay blank for now."
fi

# shellcheck disable=SC1091
set -a; source .env; set +a
for var in CEF_APP_BASE_URL CEF_LTI_ISSUER CEF_LTI_CLIENT_ID CEF_LTI_DEPLOYMENT_IDS CEF_LTI_AUTH_LOGIN_URL CEF_LTI_JWKS_URL; do
    if [ -z "${!var:-}" ]; then
        fail "$var is blank in .env — this is required (LTI is the only login path). See DEPLOYMENT.md's \"Registering CEF as an LTI tool\" section, fill it in, and re-run this script."
    fi
done
info "Found .env with LTI registration filled in."

# ── 3. Build and start ───────────────────────────────────────────────────────

echo ""
echo "Building and starting the server + web client (this can take several minutes the first time)..."
docker compose up -d --build server web

# ── 4. Wait for the server to actually respond ───────────────────────────────

echo ""
echo -n "Waiting for the server to come up"
READY=false
for _ in $(seq 1 60); do
    if docker compose exec -T server curl -sf http://localhost:8080/ 2>/dev/null | grep -q "Ktor"; then
        READY=true
        break
    fi
    echo -n "."
    sleep 2
done
echo ""

if [ "$READY" != "true" ]; then
    warn "The server didn't respond within the expected time. It may still be starting — check with:"
    echo "    docker compose logs -f server"
    exit 1
fi

info "Server is up."

# ── 5. Done ───────────────────────────────────────────────────────────────────

echo ""
echo "--------------------------------------------------"
echo -e "${BOLD}Setup complete.${NC}"
echo "--------------------------------------------------"
echo ""
echo "  Web app:        http://localhost"
echo "  View logs:      docker compose logs -f server"
echo "  Stop:           docker compose stop server web"
echo "  Update/restart: git pull && ./scripts/setup-college-server.sh"
echo ""
echo "First step: open the web app, go to Settings, and add a Gemini API key"
echo "(https://aistudio.google.com/apikey) — each student's account needs one set"
echo "before syllabus ingestion will work."
echo ""
echo "Backups run automatically every 24 hours (no setup needed) — see DEPLOYMENT.md"
echo "under 'Maintenance' for how to change that or restore from a backup."
