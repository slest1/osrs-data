#!/usr/bin/env bash
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
# shellcheck source=scripts/upstream-common.sh
. "$here/upstream-common.sh"

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "usage: $0 <upstream-repo> [commit]" >&2
    exit 2
fi
repo="$1"
commit=$(resolve_upstream "$repo" "${2:-main}") || exit 2

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/a" "$tmp/b" "$root/patches"
extract_upstream "$repo" "$commit" "$tmp/a"
stage_local "$root" "$tmp/b"

for module in "${UPSTREAM_MODULES[@]}"; do
    python3 "$REDACTED_PATCH" make "$tmp/a/$module" "$tmp/b/$module" "$module" > "$tmp/$module.patch"
    mv "$tmp/$module.patch" "$root/patches/$module.patch"
    echo "patches/$module.patch: $(grep -c '^@@' "$root/patches/$module.patch" || true) hunk(s)"
done
printf '%s\n' "$commit" > "$root/UPSTREAM"
echo "recorded upstream $commit in UPSTREAM"
