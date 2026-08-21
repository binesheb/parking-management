#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Refusing to update: working tree has local changes. Commit, stash, or discard them first." >&2
  exit 1
fi

branch="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$branch" != "main" ]]; then
  echo "Refusing to update from '$branch': this updater only tracks main." >&2
  exit 1
fi

git fetch --prune origin main

local_sha="$(git rev-parse HEAD)"
remote_sha="$(git rev-parse origin/main)"

if [[ "$local_sha" == "$remote_sha" ]]; then
  echo "Already up to date with origin/main."
  exit 0
fi

if ! git merge-base --is-ancestor HEAD origin/main; then
  echo "Refusing to update: local main has diverged from origin/main. Resolve manually before retrying." >&2
  exit 1
fi

git merge --ff-only origin/main
"$repo_root/scripts/bootstrap.sh"

echo "Update and firmware validation completed successfully."
