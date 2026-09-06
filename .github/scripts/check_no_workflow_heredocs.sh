#!/usr/bin/env bash
# Fail if GitHub Actions workflows use shell heredocs.
# After YAML indent strip, a closing EOF inside if/else often keeps leading spaces
# and bash treats the heredoc as unterminated (exit 2, no release published).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
shopt -s nullglob
files=("$ROOT"/.github/workflows/*.{yml,yaml})
if ((${#files[@]} == 0)); then
  echo "No workflow files found under .github/workflows/"
  exit 1
fi

bad=0
for f in "${files[@]}"; do
  if grep -nE '<<-?['\''"]?[A-Za-z_][A-Za-z0-9_]*' "$f"; then
    echo "ERROR: $f contains a shell heredoc (<<EOF / <<'EOF')."
    echo "Use .github/release-notes/*.md and .github/scripts/write_update_manifest.py instead."
    bad=1
  fi
done

if ((bad)); then
  exit 1
fi
echo "OK: no shell heredocs in .github/workflows/"
