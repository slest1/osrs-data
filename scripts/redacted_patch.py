#!/usr/bin/env python3
"""Redacted patches: unified-diff hunks where each run of removed lines is "-<count> sha256:<hex>"."""

import difflib
import hashlib
import os
import sys

HEADER = "# redacted-patch v1"
NO_EOL = "\\ No newline at end of file"


class PatchError(Exception):
    pass


def read_lines(path):
    with open(path, "rb") as f:
        return f.read().splitlines(keepends=True)


def files_under(root):
    found = []
    for dirpath, _, names in os.walk(root):
        for name in names:
            found.append(os.path.relpath(os.path.join(dirpath, name), root))
    return sorted(found)


def text(line):
    return line.rstrip(b"\n").decode("utf-8")


def emit_plain(out, marker, line):
    out.append(marker + text(line))
    if not line.endswith(b"\n"):
        out.append(NO_EOL)


def make(old_root, new_root, label):
    old_files = files_under(old_root)
    new_files = files_under(new_root)
    if old_files != new_files:
        added = sorted(set(new_files) - set(old_files))
        removed = sorted(set(old_files) - set(new_files))
        raise PatchError("file sets differ (only edits are supported): added %s, removed %s"
                         % (added, removed))
    out = [HEADER, "# %s: upstream -> local copy; removed lines are stored as SHA-256 only" % label]
    for rel in old_files:
        a = read_lines(os.path.join(old_root, rel))
        b = read_lines(os.path.join(new_root, rel))
        if a == b:
            continue
        out.append("file " + os.path.join(label, rel).replace(os.sep, "/"))
        matcher = difflib.SequenceMatcher(None, a, b, autojunk=False)
        for group in matcher.get_grouped_opcodes(3):
            i1, i2, j1, j2 = group[0][1], group[-1][2], group[0][3], group[-1][4]
            out.append("@@ -%d,%d +%d,%d @@" % (i1 + 1, i2 - i1, j1 + 1, j2 - j1))
            for tag, a1, a2, b1, b2 in group:
                if tag == "equal":
                    for line in a[a1:a2]:
                        emit_plain(out, " ", line)
                    continue
                if tag in ("replace", "delete"):
                    removed = b"".join(a[a1:a2])
                    out.append("-%d sha256:%s" % (a2 - a1, hashlib.sha256(removed).hexdigest()))
                if tag in ("replace", "insert"):
                    for line in b[b1:b2]:
                        emit_plain(out, "+", line)
    return "\n".join(out) + "\n"


def parse(patch_path):
    with open(patch_path, "rb") as f:
        raw = f.read().decode("utf-8").split("\n")
    if raw and raw[-1] == "":
        raw.pop()
    if not raw or raw[0] != HEADER:
        raise PatchError("%s is not a redacted patch (no '%s' header)" % (patch_path, HEADER))
    files = []
    hunk = None
    last = None
    for line in raw[1:]:
        if line.startswith("# "):
            continue
        if line.startswith("file "):
            files.append((line[5:], []))
            hunk, last = None, None
        elif line.startswith("@@ "):
            if not files:
                raise PatchError("hunk before any 'file' line in %s" % patch_path)
            start = int(line.split()[1][1:].split(",")[0])
            hunk, last = (start, [], []), None
            files[-1][1].append(hunk)
        elif hunk is None:
            raise PatchError("line outside a hunk in %s: %r" % (patch_path, line))
        elif line == NO_EOL:
            if last == "+":
                hunk[2][-1] = hunk[2][-1][:-1]
            elif last == " ":
                hunk[2][-1] = hunk[2][-1][:-1]
                hunk[1][-1] = ("line", hunk[2][-1])
            else:
                raise PatchError("stray '%s' in %s" % (NO_EOL, patch_path))
        elif line.startswith(" "):
            content = (line[1:] + "\n").encode("utf-8")
            hunk[1].append(("line", content))
            hunk[2].append(content)
            last = " "
        elif line.startswith("-"):
            count, digest = line[1:].split(" ")
            if not digest.startswith("sha256:"):
                raise PatchError("unreadable removed-lines entry in %s: %r" % (patch_path, line))
            hunk[1].append(("hash", int(count), digest[len("sha256:"):]))
            last = "-"
        elif line.startswith("+"):
            hunk[2].append((line[1:] + "\n").encode("utf-8"))
            last = "+"
        else:
            raise PatchError("unreadable line in %s: %r" % (patch_path, line))
    return files


def matches(lines, at, items):
    pos = at
    for item in items:
        if item[0] == "line":
            if pos >= len(lines) or lines[pos] != item[1]:
                return False
            pos += 1
        else:
            count, digest = item[1], item[2]
            if pos + count > len(lines):
                return False
            if hashlib.sha256(b"".join(lines[pos:pos + count])).hexdigest() != digest:
                return False
            pos += count
    return True


def old_length(items):
    return sum(1 if item[0] == "line" else item[1] for item in items)


def apply(patch_path, tree):
    for rel, hunks in parse(patch_path):
        path = os.path.join(tree, rel)
        if not os.path.isfile(path):
            raise PatchError("%s: no such file in the upstream tree" % rel)
        lines = read_lines(path)
        offset = 0
        floor = 0
        for number, (start, items, new_lines) in enumerate(hunks, 1):
            expected = start - 1 + offset
            candidates = sorted(range(floor, len(lines) + 1), key=lambda p: (abs(p - expected), p))
            at = next((p for p in candidates if matches(lines, p, items)), None)
            if at is None:
                raise PatchError("%s: hunk %d (upstream line %d) no longer matches upstream"
                                 % (rel, number, start))
            length = old_length(items)
            lines[at:at + length] = new_lines
            offset = at - (start - 1) + len(new_lines) - length
            floor = at + len(new_lines)
        with open(path, "wb") as f:
            f.write(b"".join(lines))


def main(argv):
    try:
        if len(argv) == 5 and argv[1] == "make":
            sys.stdout.write(make(argv[2], argv[3], argv[4]))
            return 0
        if len(argv) == 4 and argv[1] == "apply":
            apply(argv[2], argv[3])
            return 0
    except PatchError as e:
        print("redacted_patch: " + str(e), file=sys.stderr)
        return 1
    print("usage: redacted_patch.py make <old-dir> <new-dir> <label> | apply <patch> <tree>",
          file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
