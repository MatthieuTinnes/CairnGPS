#!/usr/bin/env bash
# Usage: bump-version.sh <current-version> <patch|minor|major>
# Prints VERSION_NAME=x.y.z and VERSION_CODE=n on stdout (GITHUB_OUTPUT format).
set -euo pipefail

current="${1:?current version required}"
bump="${2:?bump type required}"

if [[ ! "$current" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "error: VERSION_NAME '$current' is not major.minor.patch" >&2
  exit 1
fi

major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[2]}"; patch="${BASH_REMATCH[3]}"

case "$bump" in
  major) major=$((major + 1)); minor=0; patch=0 ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  patch) patch=$((patch + 1)) ;;
  *) echo "error: unknown bump type '$bump' (want patch|minor|major)" >&2; exit 1 ;;
esac

echo "VERSION_NAME=${major}.${minor}.${patch}"
echo "VERSION_CODE=$((major * 10000 + minor * 100 + patch))"
