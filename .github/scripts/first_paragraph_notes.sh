#!/usr/bin/env bash
# First paragraph of a git commit message → one-line releaseNotes (no Co-authored-by).
# Usage: first_paragraph_notes.sh [commit]
set -euo pipefail

COMMIT="${1:-HEAD}"
git log -1 --pretty=%B "$COMMIT" \
  | sed '/^Co-authored-by:/Id' \
  | sed '/^$/q' \
  | tr '\n' ' ' \
  | sed 's/[[:space:]]*$//'
