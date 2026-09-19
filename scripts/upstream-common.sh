# Sourced by sync-upstream.sh, check-drift.sh and update-patches.sh; not run directly.

UPSTREAM_MODULES=(questdata-schema questdata-tool)
UPSTREAM_PARTS=(src build.gradle.kts)
REDACTED_PATCH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/redacted_patch.py"

upstream_paths() {
    local module part
    for module in "${UPSTREAM_MODULES[@]}"; do
        for part in "${UPSTREAM_PARTS[@]}"; do
            printf '%s\n' "$module/$part"
        done
    done
}

resolve_upstream() {
    local repo="$1" rev="$2"
    if ! git -C "$repo" rev-parse --git-dir >/dev/null 2>&1; then
        echo "error: $repo is not a git repository" >&2
        return 1
    fi
    if ! git -C "$repo" rev-parse --verify --quiet "$rev^{commit}"; then
        echo "error: $rev is not a commit in $repo" >&2
        return 1
    fi
}

extract_upstream() {
    local repo="$1" commit="$2" dest="$3"
    local paths module part
    paths=$(upstream_paths)
    # shellcheck disable=SC2086 # the paths contain no whitespace
    git -C "$repo" archive --format=tar "$commit" -- $paths | tar -x -C "$dest"
    for module in "${UPSTREAM_MODULES[@]}"; do
        for part in "${UPSTREAM_PARTS[@]}"; do
            if [[ ! -e "$dest/$module/$part" ]]; then
                echo "error: upstream $commit has no $module/$part" >&2
                return 1
            fi
        done
    done
}

stage_local() {
    local root="$1" dest="$2" module part
    for module in "${UPSTREAM_MODULES[@]}"; do
        mkdir -p "$dest/$module"
        for part in "${UPSTREAM_PARTS[@]}"; do
            if [[ -e "$root/$module/$part" ]]; then
                cp -R "$root/$module/$part" "$dest/$module/$part"
            fi
        done
    done
}

apply_patches() {
    local root="$1" tree="$2" commit="$3" module patch
    for module in "${UPSTREAM_MODULES[@]}"; do
        patch="$root/patches/$module.patch"
        [[ -s "$patch" ]] || continue
        if ! python3 "$REDACTED_PATCH" apply "$patch" "$tree"; then
            echo "error: patches/$module.patch no longer applies to upstream $commit - the patch needs" \
                "refreshing: bring the change into $module/ by hand, keep the local edits, then run" \
                "scripts/update-patches.sh <upstream-repo> $commit" >&2
            return 1
        fi
    done
}
