#!/usr/bin/env bash
# Runs ON the Oracle VM. It is the forced command of the GitHub Actions SSH key (see "CI/CD" in CLAUDE.md), so it is
# the only thing that key can ever do. Can also be run by hand: bash deploy/deploy.sh
#
# main() is a function ending in `exit` so that bash has parsed the whole script before `git checkout` replaces it.
set -euo pipefail

HEALTH_URL="http://127.0.0.1:3322/actuator/health"
WAIT_SECONDS=240

wait_until_healthy() {
  local waited=0
  until curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; do
    waited=$((waited + 5))
    if [ "$waited" -ge "$WAIT_SECONDS" ]; then
      return 1
    fi
    sleep 5
  done
}

release() {
  docker compose up -d --build --remove-orphans || return 1
  wait_until_healthy
}

main() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.."

  if [ ! -f .env ]; then
    echo "ERROR: .env is missing in $(pwd). Copy .env.example to .env and fill it in first." >&2
    exit 1
  fi

  local previous current
  previous=$(git rev-parse HEAD)
  git fetch --quiet origin main
  # The VM is a mirror of origin/main: tracked files are overwritten, untracked ones (.env) are left alone.
  git checkout --quiet --force -B main origin/main
  current=$(git rev-parse HEAD)
  echo "Deploying ${previous:0:7} -> ${current:0:7}"

  if release; then
    docker image prune --force >/dev/null
    echo "Deployed ${current:0:7}: $(curl -fsS --max-time 5 "$HEALTH_URL")"
    exit 0
  fi

  echo "ERROR: the new version did not become healthy within ${WAIT_SECONDS}s. Last api logs:" >&2
  docker compose logs --no-color --tail=60 api >&2 || true

  echo "Rolling back to ${previous:0:7}..." >&2
  git checkout --quiet --force -B main "$previous"
  if release; then
    echo "Rolled back to ${previous:0:7}, which is healthy. The deployment of ${current:0:7} FAILED." >&2
  else
    echo "Rollback to ${previous:0:7} is not healthy either: check the server." >&2
  fi
  exit 1
}

main "$@"
exit
