#!/usr/bin/env bash
# Fetch the plugin hub's Quest Helper jar, export it for NORMAL and IRONMAN, and bundle both.
#
#   scripts/refresh.sh [--previous-manifest <manifest.json>] [--skip-fetch | --bundle-only]
#
#   --previous-manifest  the last published manifest; the bundle step refuses to shrink below it
#   --skip-fetch         reuse out/fetch/ from an earlier fetch (CI fetches first to compare)
#   --bundle-only        reuse out/fetch/ and out/export/ and only re-run the bundle step
#
# Everything is written under out/ in this repository and nowhere else. out/ is not a Gradle
# build directory, so `./gradlew clean` leaves it alone:
#   out/fetch/                      quest-helper.jar, quest-helper.json
#   out/export/{normal,ironman}/    the exporter's per-quest JSON (--strict --stub-client, skill level 1)
#   out/bundle/                     quests-{normal,ironman}.json(.gz), manifest.json
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
out="$root/out"
fetch_dir="$out/fetch"
export_dir="$out/export"
bundle_dir="$out/bundle"

previous=""
skip_fetch=0
bundle_only=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --previous-manifest)
            [[ $# -ge 2 ]] || { echo "refresh: --previous-manifest needs a path" >&2; exit 2; }
            previous="$2"
            shift 2
            ;;
        --skip-fetch) skip_fetch=1; shift ;;
        --bundle-only) skip_fetch=1; bundle_only=1; shift ;;
        -h|--help) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "refresh: unknown option $1" >&2; exit 2 ;;
    esac
done

gradle() {
    "$root/gradlew" -p "$root" --quiet "$@"
}

mkdir -p "$out"

# copied aside: the previous manifest may live in bundle_dir, which is cleared below
if [[ -n "$previous" ]]; then
    if [[ ! -f "$previous" ]]; then
        echo "refresh: previous manifest $previous does not exist" >&2
        exit 2
    fi
    cp "$previous" "$out/previous-manifest.json"
    previous="$out/previous-manifest.json"
fi

if [[ $skip_fetch -eq 0 ]]; then
    echo "== fetch"
    rm -rf "$fetch_dir"
    gradle :pipeline:fetch --args="--out '$fetch_dir'"
fi
jar="$fetch_dir/quest-helper.jar"
for f in "$jar" "$fetch_dir/quest-helper.json"; do
    [[ -f "$f" ]] || { echo "refresh: $f is missing; run without --skip-fetch" >&2; exit 1; }
done

if [[ $bundle_only -eq 0 ]]; then
    for type in NORMAL IRONMAN; do
        dir="$export_dir/$(printf '%s' "$type" | tr '[:upper:]' '[:lower:]')"
        echo "== export $type"
        rm -rf "$dir"
        gradle :questdata-tool:export \
            --args="--jar '$jar' --out '$dir' --strict --stub-client --account-type $type"
    done
fi

echo "== bundle"
rm -rf "$bundle_dir"
bundle_args="--export NORMAL='$export_dir/normal' --export IRONMAN='$export_dir/ironman'"
bundle_args+=" --quest-helper '$fetch_dir/quest-helper.json' --upstream '$root/UPSTREAM' --out '$bundle_dir'"
if [[ -n "$previous" ]]; then
    bundle_args+=" --previous-manifest '$previous'"
fi
gradle :pipeline:bundle --args="$bundle_args"
