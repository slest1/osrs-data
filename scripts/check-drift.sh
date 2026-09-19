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
mkdir -p "$tmp/expected" "$tmp/local"
extract_upstream "$repo" "$commit" "$tmp/expected" || exit 1
apply_patches "$root" "$tmp/expected" "$commit" || exit 3
stage_local "$root" "$tmp/local"

recorded=$(tr -d '[:space:]' < "$root/UPSTREAM" 2>/dev/null || true)
if (cd "$tmp" && diff -ru expected local); then
    echo "no drift: ${UPSTREAM_MODULES[*]} match upstream $commit + patches/*.patch"
    if [[ "$recorded" != "$commit" ]]; then
        echo "note: UPSTREAM records ${recorded:-nothing}; run sync-upstream.sh to record $commit"
    fi
    exit 0
fi
echo "drift: the copies differ from upstream $commit + patches/*.patch as shown above" \
    "(UPSTREAM records ${recorded:-nothing})" >&2
exit 1
