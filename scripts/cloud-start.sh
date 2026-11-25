#!/usr/bin/env bash
# Start everything in GitHub Codespaces. Needs the Codespaces secret GPR_TOKEN.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${GPR_TOKEN:?Add the Codespaces secret GPR_TOKEN (read:packages) and restart the codespace}"
export GITHUB_ACTOR="${GITHUB_USER:-student}" GITHUB_TOKEN="$GPR_TOKEN"

# Only this process tree sees the token. The npmrc keeps a variable name, not the value.
export NODE_AUTH_TOKEN="$GPR_TOKEN" NPM_CONFIG_USERCONFIG="$HOME/.npmrc-helex"
printf '//npm.pkg.github.com/:_authToken=${NODE_AUTH_TOKEN}\n' > "$NPM_CONFIG_USERCONFIG"

(cd "$ROOT/frontend" && npm ci)
"$ROOT/scripts/run-backend.sh" > "$HOME/backend.log" 2>&1 &
BACKEND=$!
trap 'kill $BACKEND 2>/dev/null || true' EXIT
echo "Waiting for the backend, the first start takes a few minutes..."
until curl -sf localhost:18440/actuator/health > /dev/null; do
  kill -0 $BACKEND 2>/dev/null || { tail -40 "$HOME/backend.log"; exit 1; }
  sleep 2
done
echo "Backend is up."
"$ROOT/scripts/run-frontend.sh"
