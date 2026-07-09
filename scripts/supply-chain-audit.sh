#!/bin/bash
#
# Weekly supply-chain audit for college_executive_function AND its sibling repo, oficio.
# Runs locally (via launchd, see docs/ops/supply-chain-hardening.md) because three of its
# five checks need local-machine access that a cloud/CI job cannot get: a fresh CI checkout
# has no real reflog history, and CI has no visibility into what's running on this machine.
#
# Checks, per repo:
#   1. git log vs git reflog --all divergence (tamper signal)
#   2. Grep build-tooling config files for obfuscation markers
#   3. Confirm the "Protect main" ruleset is still active with only the expected bypass actor
#   4. ps aux process audit (once, machine-wide, not per-repo)
#   5. Opens the GitHub Apps/OAuth authorization pages for manual review (cannot be automated
#      headlessly — needs an authenticated browser session)
#
# Findings are written to a dated log; only anomalies are surfaced via a macOS notification —
# a clean run stays quiet, per the "don't dump noise" principle in docs/ops/supply-chain-hardening.md.

set -uo pipefail

CEF_DIR="/Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function"
OFICIO_DIR="/Users/walterduquedeestrada/AndroidStudioProjects/oficio"
LOG_DIR="$HOME/Library/Logs/supply-chain-audit"
LOG_FILE="$LOG_DIR/$(date +%Y-%m-%d).log"
mkdir -p "$LOG_DIR"

FINDINGS=()

log() { echo "$1" | tee -a "$LOG_FILE"; }
finding() { FINDINGS+=("$1"); log "⚠️  FINDING: $1"; }

log "=== Supply-chain audit — $(date) ==="

# --- Check 1 + 2 + 3, per repo ---
audit_repo() {
    local name="$1" dir="$2" ruleset_id="$3" owner_repo="$4"
    log ""
    log "--- $name ($dir) ---"

    if [ ! -d "$dir/.git" ]; then
        finding "$name: repo directory not found at $dir — skipped"
        return
    fi
    cd "$dir" || return

    # 1. git log vs reflog divergence
    local log_head reflog_head
    log_head=$(git log -1 --format=%H main 2>/dev/null || echo "")
    reflog_head=$(git reflog show main 2>/dev/null | head -1 | awk '{print $1}' || echo "")
    if [ -z "$log_head" ]; then
        log "  git log: could not resolve main HEAD (skipped)"
    else
        log "  git log HEAD (main): $log_head"
        # A short reflog entry is a prefix of the full hash — presence check, not equality.
        if [ -n "$reflog_head" ] && [[ "$log_head" != "$reflog_head"* ]]; then
            finding "$name: git log HEAD ($log_head) doesn't match most recent reflog entry ($reflog_head) — possible history rewrite"
        fi
    fi

    # 2. Grep build-tooling configs for obfuscation markers
    local config_files=()
    while IFS= read -r -d '' f; do config_files+=("$f"); done < <(find "$dir" \
        -maxdepth 3 \
        \( -name "vite.config.ts" -o -name "vite.config.js" -o -name "build.gradle.kts" \
           -o -name "settings.gradle.kts" -o -name "eslint.config.js" -o -name "webpack.config.js" \
           -o -name "next.config.js" -o -name "tailwind.config.js" -o -name "babel.config.js" \) \
        -not -path "*/node_modules/*" -not -path "*/build/*" -not -path "*/.git/*" \
        -print0 2>/dev/null)

    for f in "${config_files[@]}"; do
        if grep -qE '\beval\(|atob\(|btoa\(|new Function\(' "$f" 2>/dev/null; then
            finding "$name: suspicious construct (eval/atob/btoa/new Function) in $f"
        fi
        if grep -qoE '[A-Za-z0-9+/]{200,}={0,2}' "$f" 2>/dev/null; then
            finding "$name: long base64-shaped literal (200+ chars) in $f"
        fi
    done
    log "  Scanned ${#config_files[@]} build-config file(s) — $([ ${#FINDINGS[@]} -eq 0 ] && echo clean)"

    # 3. Ruleset check
    if [ -n "$ruleset_id" ]; then
        local ruleset_json
        ruleset_json=$(gh api "repos/$owner_repo/rulesets/$ruleset_id" 2>/dev/null)
        if [ -z "$ruleset_json" ]; then
            finding "$name: could not fetch ruleset $ruleset_id (deleted? API error?)"
        else
            local enforcement bypass_count bypass_role
            enforcement=$(echo "$ruleset_json" | grep -o '"enforcement":"[^"]*"' | cut -d'"' -f4)
            if [ "$enforcement" != "active" ]; then
                finding "$name: ruleset $ruleset_id enforcement is '$enforcement', expected 'active'"
            fi
            bypass_count=$(echo "$ruleset_json" | grep -o '"actor_id"' | wc -l | tr -d ' ')
            if [ "$bypass_count" != "1" ]; then
                finding "$name: ruleset $ruleset_id has $bypass_count bypass actor(s), expected exactly 1 — review who/what was added"
            fi
            log "  Ruleset $ruleset_id: enforcement=$enforcement, bypass actors=$bypass_count"
        fi
    fi
}

audit_repo "CEF" "$CEF_DIR" "18722247" "borinquenkid/college_executive_function"
audit_repo "Oficio" "$OFICIO_DIR" "18723143" "borinquenkid/oficio"

# --- Check 4: process audit (machine-wide, once) ---
log ""
log "--- Process audit ---"
NODE_PROCS=$(ps aux | grep -E '[n]ode|[j]ava|[g]radle' | wc -l | tr -d ' ')
log "  $NODE_PROCS node/java/gradle process(es) currently running (informational — not compared against a baseline yet; review manually if this run coincides with a time you'd expect zero)"
ps aux | grep -E '[n]ode|[j]ava|[g]radle' >> "$LOG_FILE" 2>/dev/null || true

# --- Check 5: surface the GitHub Apps/OAuth pages (cannot be checked headlessly) ---
log ""
log "--- GitHub Apps / OAuth review (manual — opening in browser) ---"
open "https://github.com/settings/installations" 2>/dev/null || true
open "https://github.com/settings/applications" 2>/dev/null || true
log "  Opened github.com/settings/installations and /applications — review manually for anything unrecognized."

# --- Summary + notification ---
log ""
log "=== Summary: ${#FINDINGS[@]} finding(s) ==="

if [ ${#FINDINGS[@]} -gt 0 ]; then
    SUMMARY=$(printf '%s\n' "${FINDINGS[@]}" | head -3)
    osascript -e "display notification \"${#FINDINGS[@]} finding(s) — see $LOG_FILE\" with title \"Supply-chain audit\" subtitle \"$SUMMARY\"" 2>/dev/null || true
else
    log "Clean run — no anomalies. (No notification sent; see log if you want confirmation it ran.)"
fi
