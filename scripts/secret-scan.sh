#!/usr/bin/env bash
set -euo pipefail

if ! command -v gitleaks >/dev/null 2>&1; then
  echo "gitleaks is required: https://github.com/gitleaks/gitleaks" >&2
  exit 127
fi

echo "Scanning current working tree..."
gitleaks dir . --redact --no-banner

echo "Scanning Git history..."
gitleaks git --redact --no-banner
