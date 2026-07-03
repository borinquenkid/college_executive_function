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

# ── 2. Google Calendar credentials (optional) ────────────────────────────────
#
# Only needed to refresh already-linked Google Calendar accounts. Students can use
# the app fully without this — Calendar sync just won't be available until it's set.

if [ ! -f .env ]; then
    cat > .env <<'EOF'
# Optional — only needed for Google Calendar sync. Leave blank to skip for now;
# you can add these and re-run this script at any time.
# See README.md's "Google Cloud Console & API Setup" section for how to get these.
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
EOF
    warn "Created .env with blank Google Calendar credentials (Calendar sync will be unavailable until you fill these in — everything else works fine without them)."
else
    info "Found existing .env file — leaving it as-is."
fi

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
