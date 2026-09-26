# osrs-data

Versioned, trimmed JSON bundles of [Quest Helper](https://github.com/Zoinkwiz/quest-helper)'s quest
data for Old School RuneScape, built from the exact Quest Helper jar that RuneLite's plugin hub
serves and published as GitHub releases for an MCP server (or anything else) to download.

Each release carries one bundle per account type — `NORMAL` and `IRONMAN` — covering all 261
entries Quest Helper defines as quests an account completes: 167 members' quests, 24 free-to-play
quests, 22 miniquests and 48 achievement diary tiers. A bundle holds, for every entry, its
requirements, rewards, the quest panel outline and every step's text, location and requirements.

## Releases

A scheduled workflow (`.github/workflows/refresh-quests.yml`) runs daily. It asks the plugin hub
which Quest Helper jar it serves and compares that jar's hash with the one recorded in the latest
release. Only when the jar changed (or the run is dispatched with `force`) does it export, bundle
and publish.

- **Tag / name:** `quests-<questHelperVersion>-<first 8 characters of jarHash>`, for example
  `quests-4.17.1-5BKt5NHC`. The hash part distinguishes two hub builds that carry the same
  version string.
- **Assets:** `quests-normal.json.gz`, `quests-ironman.json.gz`, `manifest.json`.
- The newest release is marked *latest*, so
  `https://github.com/<owner>/osrs-data/releases/latest/download/manifest.json` always names the
  current data.

## Consuming a bundle

1. Download `manifest.json` and the `.json.gz` you want from the same release.
2. Check `format == "osrs-questdata-bundle-manifest"` and `formatVersion == 1`.
3. Verify the download against the manifest entry with that `name`: its size must equal `bytes`
   and its SHA-256 must equal `sha256`. Refuse the file otherwise.
4. Decompress, then verify the JSON against the same entry's `uncompressedBytes` and
   `uncompressedSha256`. This is the bundle's real identity: it does not depend on which gzip
   implementation compressed it, so it is the value to compare across releases or to pin.
5. Check the bundle's own `format == "osrs-questdata-bundle"` and `formatVersion == 1`.

```sh
base=https://github.com/<owner>/osrs-data/releases/latest/download
curl -fsSLO "$base/manifest.json"
curl -fsSLO "$base/quests-normal.json.gz"

entry='.files[] | select(.name == "quests-normal.json.gz")'
echo "$(jq -r "$entry.sha256" manifest.json)  quests-normal.json.gz" | sha256sum -c -
gunzip -c quests-normal.json.gz > quests-normal.json
echo "$(jq -r "$entry.uncompressedSha256" manifest.json)  quests-normal.json" | sha256sum -c -
# macOS: use `shasum -a 256 -c -` in place of `sha256sum -c -`
```

The checksums come from the same release as the files, so they catch truncated or corrupted
downloads and caching mistakes, not a tampered release. Pin a tag rather than `latest` if you need
a fixed data set.

## Bundle format v1

`quests-<accounttype>.json` is a single compact JSON object (UTF-8, no insignificant whitespace):

```jsonc
{
  "format": "osrs-questdata-bundle",
  "formatVersion": 1,
  "accountType": "NORMAL",            // or "IRONMAN"
  "skillLevel": 1,                    // the skill level the export was profiled at
  "source": {
    "runeliteVersion": "1.12.39",     // RuneLite client version the hub manifest belongs to
    "questHelperVersion": "4.17.1",   // Quest Helper's displayed version on the hub
    "questHelperJarHash": "5BKt5NHCMQGfZxzx3NU1loph_1RujcPxn-_fL39e2vI",
    "exporterSchemaVersion": 3,       // schema version of the exporter's per-quest files
    "upstreamCommit": "<40-hex commit of the upstream exporter>"
  },
  "attribution": {
    "notice": "Quest data from Quest Helper by Zoinkwiz and contributors, BSD 2-Clause License",
    "licenseText": "BSD 2-Clause License\n\nCopyright (c) 2020, Zoinkwiz\n..."
  },
  "entries": [ /* one object per quest, sorted by questKey */ ]
}
```

### Entries

| field | type | notes |
|---|---|---|
| `questKey` | string | Quest Helper's enum name, e.g. `DORICS_QUEST`; unique, the sort key |
| `name` | string | display name |
| `questId` | int | the game's quest id; `-1` for entries the game has no quest id for (the achievement diaries and a few miniquests) |
| `type` | string | `P2P`, `F2P`, `MINIQUEST` or `ACHIEVEMENT_DIARY` |
| `difficulty` | string | `NOVICE` … `GRANDMASTER` for quests; `MINIQUEST` and `ACHIEVEMENT_DIARY` for those types |
| `rewards` | object | `questPoints`, `experience[] {skill, amount, lamp}`, `items[] {name, itemId, quantity}`, `unlocks[] {text}` |
| `itemRequirements`, `itemRecommended`, `generalRequirements` | Requirement[] | |
| `combatRequirements`, `notes` | string[] | |
| `panels` | Panel[] | the quest panel's sections, in order |
| `steps` | Step[] | every step of the quest's step graph |

**Panel:** `header` (string), `id` (int), `stepIds` (string[], referring to `steps[].id`),
`requirements`, `recommended` (Requirement[]), `children` (Panel[], same shape, recursively).

**Step:** `id` (string, e.g. `step-12`; stable within a bundle), `type` (Quest Helper's step class,
e.g. `NpcStep`, `ObjectStep`, `ConditionalStep`), `text` (string[]), `worldPoint`
(`{x, y, plane}`, absent when the step has no location), `requirements`, `recommended`,
`teleports` (Requirement[]).

**Requirement:** `type` (Quest Helper's requirement class, e.g. `ItemRequirement`,
`SkillRequirement`, `QuestRequirement`), `displayText` (string), `data` (object). `data` is kept
exactly as exported and its keys depend on `type`, for example `id`, `name`, `quantity`,
`alternates` for items or `skill`, `level` for skills. Composite requirements nest further
requirements inside `data`.

Rules that hold throughout:

- A field whose value is null is **absent**, never `null`. Lists are present, possibly empty.
- Key order is fixed as documented; `entries` are sorted by `questKey`.
- There are no timestamps anywhere. The same inputs always produce byte-identical files, and the
  gzip files carry no name and an mtime of 0.
- What is deliberately **not** in a bundle: progress varbits/varps, completion values and
  stage-to-step mappings (what a client needs to track an account's progress), and per step the
  overlay text, NPC/object/widget targets, locking, substeps, conditional branches, dialog and
  widget choices. Per panel, the locking step and vars. These drive a live client, not an answer.

A consumer should reject a `formatVersion` it does not know. Additive changes that do not change
the meaning of existing fields may appear within version 1; anything else bumps the version.

### manifest.json

```json
{
  "format": "osrs-questdata-bundle-manifest",
  "formatVersion": 1,
  "source": { "...": "same block as in the bundles" },
  "files": [
    {
      "name": "quests-ironman.json.gz",
      "accountType": "IRONMAN",
      "bytes": 555979,
      "sha256": "<sha256 of the .json.gz>",
      "uncompressedBytes": 6147511,
      "uncompressedSha256": "<sha256 of the JSON inside>",
      "entries": 261
    }
  ]
}
```

`files` lists exactly the published bundle files, one `.json.gz` per account type, sorted by name.
`bytes`/`sha256` describe the compressed file as downloaded. `uncompressedBytes`/`uncompressedSha256`
describe the JSON inside it. The refresh also writes the raw `.json` files locally for
convenience. They are not published and not listed.

## Running a refresh locally

Needs JDK 17 and network access (RuneLite's bootstrap, plugin hub and Maven repository, and Maven
Central). Gradle is provisioned by the wrapper. If your default `java` is not a JDK 17+ for your
machine's architecture, point `JAVA_HOME` at one first.

```sh
scripts/refresh.sh                                   # fetch -> export NORMAL + IRONMAN -> bundle
scripts/refresh.sh --previous-manifest path/to/manifest.json   # also refuse to shrink below it
scripts/refresh.sh --skip-fetch                      # reuse out/fetch/
scripts/refresh.sh --bundle-only                     # reuse out/fetch/ and out/export/
```

Everything is written under `out/` (git-ignored). It is not a Gradle build directory, so
`./gradlew clean` never removes it:

| path | contents |
|---|---|
| `out/fetch/` | `quest-helper.jar` and `quest-helper.json` (`runeliteVersion`, `questHelperVersion`, `jarHash`, `jarSize`) |
| `out/export/normal/`, `out/export/ironman/` | the exporter's per-quest JSON |
| `out/bundle/` | `quests-{normal,ironman}.json.gz`, `manifest.json`, and the raw `quests-*.json` |

The steps are also Gradle tasks:

```sh
./gradlew :pipeline:fetch --args="--out $PWD/out/fetch"
./gradlew :questdata-tool:export --args="--jar $PWD/out/fetch/quest-helper.jar --out $PWD/out/export/normal --strict --stub-client"
./gradlew :pipeline:bundle --args="--export NORMAL=$PWD/out/export/normal --export IRONMAN=$PWD/out/export/ironman --quest-helper $PWD/out/fetch/quest-helper.json --out $PWD/out/bundle [--previous-manifest <file>]"
```

Pass absolute paths: Gradle runs each tool from its own directory.

### The exporter

`:questdata-tool:export` loads the given Quest Helper jar into its own class loader and exports
every quest to one JSON file per quest (schema version 3), with no game client. It supports the
plugin-hub build of Quest Helper (package `com.questhelper`). Options:

| option | |
|---|---|
| `--jar <path>` | the jar to export (default: the newest `~/.runelite/plugins/quest-helper*.jar`) |
| `--out <dir>` | output directory, **required** |
| `--strict` | exit 1 if any quest fails to export or exports with no steps |
| `--stub-client` | also export quests whose helpers read the account while building their steps. They run against a logged-out stand-in client, so all 261 export. The refresh always uses it |
| `--skill-level N` | the skill level the stand-in reports (default 1). A handful of quests offer different steps at 99 |
| `--account-type T` | `NORMAL`, `IRONMAN` or `ULTIMATE_IRONMAN` (default `NORMAL`). Eight entries differ between NORMAL and IRONMAN, mostly achievement diaries whose item sources change |
| `--quest <KEY>`, `--verbose` | export one quest; list the quests that could not be exported |

The export is byte-for-byte deterministic, so a re-export is reviewable as a diff.

### Quest state

The exporter's per-quest files (`out/export/`, not the bundles) also carry what is needed to tell
whether an account has finished a quest without Quest Helper on the class path. That is
`questId`, `progressVarbitId`/`progressVarpId`, `completeValue` and `stateSkill`.
`com.slest.questdata.QuestStates.stateOf` reproduces Quest Helper's own `getState` from them plus
four reads of the game, in this order:

1. An entry with a game quest id asks the game's quest-status script, which is authoritative.
2. A skill-tracked entry is finished once that skill reaches `completeValue`.
3. Otherwise the progress var decides: `completeValue` means finished, 0 means not started, and
   anything else means in progress.
4. With nothing to go on, the entry is not started.

The 53 entries without a game quest id (the 48 achievement diaries and 5 miniquests) are judged by
their progress var against `completeValue`. `:questdata-tool:parity` checks `stateOf` against
Quest Helper's own `getState` for every quest. It needs a Quest Helper jar, so it is a manual task
and not part of `check`. Bundles leave these fields out, because they only matter to a program
reading a live game.

### How the jar is found

1. `https://static.runelite.net/bootstrap.json` lists the launcher's artifacts; `client-<version>.jar`
   gives the current RuneLite version.
2. `https://repo.runelite.net/plugins/manifest/<version>_full.js` is the hub's signed plugin
   manifest: a 4-byte big-endian signature length, the signature, then JSON with `jars[]`
   (`internalName`, `jarHash`, `jarSize`) and `display[]` (`internalName`, `version`, …). The
   signature is verified exactly as RuneLite's client verifies it: `SHA256withRSA` over the JSON
   bytes, against RuneLite's plugin-hub certificate. The pipeline vendors that certificate as
   `pipeline/src/main/resources/com/slest/osrsdata/pipeline/runelite-externalplugins.crt`, a
   byte-for-byte copy of `net/runelite/client/externalplugins/externalplugins.crt` from the
   RuneLite client 1.12.39 jar (`CN=runelite-plugin`, valid until 2032-04-16), SHA-256 fingerprint

   ```
   1A:A5:EF:F5:2A:14:7C:8D:96:C9:66:73:0F:B0:7C:9E:FF:38:BA:31:C5:D1:AB:DD:AF:8A:50:26:F3:F3:9D:A3
   ```

   Nothing in the JSON is read before the signature verifies. If it stops verifying, the fetch
   fails and names that fingerprint. RuneLite may have rotated its certificate. Check the one in
   the current client jar, and replace the vendored copy only if it really is RuneLite's. The
   pipeline then reads the `quest-helper` entry from both lists.
3. `https://repo.runelite.net/plugins/jar/quest-helper_<jarHash>.jar` is downloaded and must match
   the manifest's `jarSize` and `jarHash` — the unpadded base64url SHA-256 of the jar — or the
   fetch fails without writing anything.

### Guards

The bundle step checks everything before it writes anything, and exits non-zero with a message
naming the problem when:

- an entry has no steps (null steps do not count) or no panels and is not listed in
  `pipeline/src/main/resources/com/slest/osrsdata/pipeline/unwalkable-allowlist.txt` (currently
  empty: every entry of the current export has both);
- `--previous-manifest` is given and an account type has fewer entries than it had there;
- source metadata is missing: the RuneLite or Quest Helper version, the jar hash, the exporter's
  `schemaVersion` in any export file, or a full upstream commit in `UPSTREAM`;
- an export directory is missing or empty, or mixes schema versions.

## The upstream exporter

`questdata-schema/` (the per-quest JSON schema) and `questdata-tool/` (the exporter) are copies of
the upstream exporter with a small set of local edits. Only each module's `src/` and
`build.gradle.kts` are copied. `UPSTREAM` records the upstream commit, and `patches/` holds the
local edits, one patch per module, from upstream at that commit to the copy here. Those edits
remove references that do not belong in this repository and limit the exporter to the plugin-hub
build of Quest Helper. They also make `--out` required.

The patches use a redacted format (`scripts/redacted_patch.py`). They are unified-diff hunks
whose removed lines are stored only as a line count and a SHA-256, so the text an edit removes
never enters this repository. Context and added lines stay readable. A hunk applies only where
its context and hashes match exactly, with no fuzz. The scripts need `git` and `python3`, and
they read the upstream repository only through `git rev-parse` and `git archive`, working in
temporary directories:

```sh
scripts/check-drift.sh <upstream-repo> [commit]    # upstream + patches vs the copies
scripts/sync-upstream.sh <upstream-repo> [commit]  # rewrite the copies from upstream + patches
scripts/update-patches.sh <upstream-repo> [commit] # regenerate patches/ from upstream vs the copies
scripts/test-upstream-scripts.sh                   # self-contained test of all three
```

`commit` defaults to the upstream repository's `main`.

`check-drift.sh` exit codes:

| exit | meaning |
|---|---|
| 0 | the copies are exactly upstream plus the patches |
| 1 | the copies differ; the diff is printed |
| 2 | bad arguments |
| 3 | a patch no longer applies to that commit |

**Don't edit the copies casually.** Every edit must be carried in the patches, or `check-drift.sh`
reports it.

To follow a new upstream commit, run `sync-upstream.sh`. If the patches still apply, it rewrites
the copies and `UPSTREAM`. Then build, run a refresh, and commit the copies with `UPSTREAM`.

If upstream changed a line that a patch edits, both scripts stop and say the patch needs
refreshing. To refresh it:

1. Bring the upstream change into the copies by hand, keeping the local edits.
2. Run `update-patches.sh <upstream-repo> <commit>`, which regenerates `patches/` and records the
   commit.
3. Check that `check-drift.sh` passes, then commit.

## Repository layout

| path | |
|---|---|
| `questdata-schema/`, `questdata-tool/` | the upstream exporter, plus the edits in `patches/` |
| `UPSTREAM`, `patches/` | the upstream commit the copies follow, and the local edits to it |
| `pipeline/` | `fetch` and `bundle` (`com.slest.osrsdata.pipeline`), with offline JUnit 5 tests |
| `scripts/` | `refresh.sh`; `check-drift.sh`, `sync-upstream.sh`, `update-patches.sh`, `redacted_patch.py` and their test |
| `.github/workflows/refresh-quests.yml` | the daily refresh and release |

`./gradlew build` compiles everything and runs the pipeline's tests; it never touches the network
beyond resolving dependencies.

## License and attribution

The code in this repository is MIT-licensed (see `LICENSE`).

The quest data in every bundle comes from Quest Helper by Zoinkwiz and contributors and is
distributed under Quest Helper's BSD 2-Clause License. Each bundle carries the notice and the full
licence text in its `attribution` block; see also `THIRD_PARTY_NOTICES.md`.
