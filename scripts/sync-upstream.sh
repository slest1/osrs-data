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
mkdir -p "$tmp/tree"
extract_upstream "$repo" "$commit" "$tmp/tree"
apply_patches "$root" "$tmp/tree" "$commit"

for module in "${UPSTREAM_MODULES[@]}"; do
    mkdir -p "$root/$module"
    for part in "${UPSTREAM_PARTS[@]}"; do
        rm -rf "${root:?}/$module/$part"
        cp -R "$tmp/tree/$module/$part" "$root/$module/$part"
    done
done
printf '%s\n' "$commit" > "$root/UPSTREAM"

echo "synced ${UPSTREAM_MODULES[*]} to upstream $commit with patches/*.patch applied"
if git -C "$root" rev-parse --git-dir >/dev/null 2>&1; then
    git -C "$root" status --short -- "${UPSTREAM_MODULES[@]}" UPSTREAM
fi
