#!/usr/bin/env bash
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
failures=0

fail() {
    echo "FAIL $*"
    failures=$((failures + 1))
}

expect() {
    local name="$1" want="$2" got=0
    shift 2
    "$@" >"$tmp/out" 2>&1 || got=$?
    if [[ "$got" == "$want" ]]; then
        echo "ok   $name (exit $got)"
    else
        fail "$name: exit $got, wanted $want"
        sed 's/^/     /' "$tmp/out"
    fi
}

stage() {
    local dest="$1" module part
    for module in questdata-schema questdata-tool; do
        mkdir -p "$dest/$module"
        for part in src build.gradle.kts; do
            cp -R "$root/$module/$part" "$dest/$module/$part"
        done
    done
}

replace_line() {
    awk -v n="$2" -v t="$3" 'NR == n { print t; next } { print }' "$1" > "$1.new" && mv "$1.new" "$1"
}

commit_all() {
    git -C "$1" add -A
    git -C "$1" -c user.name=drift-test -c user.email=drift-test@example.invalid \
        -c commit.gpgsign=false commit -q -m "$2"
}

tree_sum() {
    (cd "$1" && find questdata-schema questdata-tool -type f | LC_ALL=C sort | xargs shasum) | shasum
}

up="$tmp/upstream"
work="$tmp/work"
tool_build=questdata-tool/build.gradle.kts
main_java=questdata-tool/src/main/java/com/slest/questdata/Main.java
states_java=questdata-schema/src/main/java/com/slest/questdata/QuestStates.java

# the two extra files exercise a missing final newline, on an added line and on a context line
mkdir -p "$work"
cp -R "$root/scripts" "$work/scripts"
stage "$work"
printf 'keep\nreplaced' > "$work/questdata-schema/src/no-eol-added.txt"
printf 'public\nlast' > "$work/questdata-schema/src/no-eol-context.txt"

stage "$up"
replace_line "$up/$tool_build" 1 "// SECRET-LINE upstream only (build)"
replace_line "$up/$main_java" 20 " * SECRET-LINE upstream only (main)"
printf 'keep\nSECRET-LINE' > "$up/questdata-schema/src/no-eol-added.txt"
printf 'SECRET-LINE\nlast' > "$up/questdata-schema/src/no-eol-context.txt"
git -C "$up" init -q -b main
commit_all "$up" "stand-in upstream"
base=$(git -C "$up" rev-parse HEAD)

expect "update-patches writes the patches" 0 "$work/scripts/update-patches.sh" "$up"
if grep -q 'SECRET-LINE' "$work"/patches/*.patch; then
    fail "a patch stores a removed line in clear"
fi
[[ "$(cat "$work/UPSTREAM")" == "$base" ]] || fail "update-patches did not record $base in UPSTREAM"
cp -R "$work/patches" "$tmp/patches-first"
expect "update-patches again" 0 "$work/scripts/update-patches.sh" "$up"
diff -r "$tmp/patches-first" "$work/patches" > /dev/null || fail "regenerated patches differ"

expect "patched upstream matches the copies" 0 "$work/scripts/check-drift.sh" "$up"

cp "$work/$main_java" "$tmp/main.java"
printf 'X' | dd of="$work/$main_java" bs=1 seek=0 conv=notrunc 2>/dev/null
expect "one changed byte in a patched file fails" 1 "$work/scripts/check-drift.sh" "$up"
grep -q 'Main.java' "$tmp/out" || fail "the drift report does not name Main.java"
cp "$tmp/main.java" "$work/$main_java"

echo stray > "$work/questdata-schema/src/Stray.java"
expect "an extra file fails" 1 "$work/scripts/check-drift.sh" "$up"
rm "$work/questdata-schema/src/Stray.java"

expect "a bad commit is a usage error" 2 "$work/scripts/check-drift.sh" "$up" no-such-rev

printf '// new upstream line 1\n// new upstream line 2\n' | cat - "$up/$main_java" > "$tmp/m"
mv "$tmp/m" "$up/$main_java"
printf '// appended upstream\n' >> "$up/$states_java"
commit_all "$up" "unrelated upstream change"
moved=$(git -C "$up" rev-parse HEAD)
expect "an unsynced upstream change is drift" 1 "$work/scripts/check-drift.sh" "$up"
expect "sync brings it in through the patches" 0 "$work/scripts/sync-upstream.sh" "$up"
grep -q 'new upstream line 2' "$work/$main_java" || fail "sync did not bring the upstream change in"
if grep -rq 'SECRET-LINE' "$work/questdata-schema" "$work/questdata-tool"; then
    fail "sync wrote a scrubbed line into the copies"
fi
[[ "$(cat "$work/UPSTREAM")" == "$moved" ]] || fail "sync did not record $moved in UPSTREAM"
expect "synced copies pass" 0 "$work/scripts/check-drift.sh" "$up"

replace_line "$up/$main_java" 22 " * SECRET-LINE changed upstream"
commit_all "$up" "upstream edits a scrubbed line"
before=$(tree_sum "$work")
expect "check-drift reports the stale patch" 3 "$work/scripts/check-drift.sh" "$up"
grep -q 'needs refreshing' "$tmp/out" || fail "check-drift does not say the patch needs refreshing"
expect "sync refuses the stale patch" 1 "$work/scripts/sync-upstream.sh" "$up"
grep -q 'needs refreshing' "$tmp/out" || fail "sync does not say the patch needs refreshing"
[[ "$(tree_sum "$work")" == "$before" ]] || fail "a failed sync changed the copies"
[[ "$(cat "$work/UPSTREAM")" == "$moved" ]] || fail "a failed sync changed UPSTREAM"

if [[ $failures -gt 0 ]]; then
    echo "$failures failure(s)"
    exit 1
fi
echo "all passed"
