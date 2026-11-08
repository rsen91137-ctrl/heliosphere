#!/usr/bin/env bash
# Pushes ./out to the orphan branch given as $1 (default: devlibs).
set -eu
BRANCH=${1:-devlibs}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT/out"
git init -q
git checkout -q -b "$BRANCH"
git config user.name "github-actions"
git config user.email "actions@github.com"
git add -A
git commit -q -m "$BRANCH from ${GITHUB_SHA:-local}"
git push -f -q "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "$BRANCH"
