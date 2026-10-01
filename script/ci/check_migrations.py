#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Guard the Liquibase changelogs.

Rules:

1. Every changeset ID looks like ``<module>-<4 digits>-<slug>`` (for example
   ``identity-0003-create-gf-user-group``), its author is ``grantforge``, and IDs are unique
   across all changelog files.
2. Production changelog files (``src/main/resources/db/changelog``) that were part of the latest
   release tag (``v*``) are immutable: databases that ran them store their checksums, so a
   change must be a new changeset instead. Before the first release nothing is locked.

The changelogs are YAML written in a fixed layout (``- changeSet:`` followed by ``id:`` and
``author:`` keys), which lets this check stay on the Python standard library.

Usage::

    python3 script/ci/check_migrations.py [--root DIR]
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cilib import list_repository_files  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
AUTHOR = "grantforge"

_CHANGELOG = re.compile(r"(?:^|/)src/(?:main|test)/resources/db/changelog/[^/]+\.ya?ml$")
_PRODUCTION = re.compile(r"(?:^|/)src/main/resources/db/changelog/")
_ID = re.compile(r"[a-z][a-z0-9]*-\d{4}-[a-z0-9]+(?:-[a-z0-9]+)*")
_CHANGESET = re.compile(r"^\s*-\s*changeSet:\s*$")
_KEY = re.compile(r"^\s*(id|author):\s*(\S.*?)\s*$")


@dataclass(frozen=True)
class ChangeSet:
    """One changeset found in a changelog file."""

    path: str
    line: int
    id: Optional[str]
    author: Optional[str]


def parse_changesets(path: str, text: str) -> List[ChangeSet]:
    """Return the changesets of a changelog, reading the first ``id``/``author`` after each marker."""
    changesets: List[ChangeSet] = []
    current: Optional[Dict[str, object]] = None
    for number, line in enumerate(text.splitlines(), start=1):
        if _CHANGESET.match(line):
            if current is not None:
                changesets.append(_to_changeset(path, current))
            current = {"line": number}
            continue
        match = _KEY.match(line)
        if current is not None and match is not None and match.group(1) not in current:
            current[match.group(1)] = match.group(2).strip("'\"")
    if current is not None:
        changesets.append(_to_changeset(path, current))
    return changesets


def _to_changeset(path: str, values: Dict[str, object]) -> ChangeSet:
    changeset_id = values.get("id")
    author = values.get("author")
    return ChangeSet(path, int(str(values["line"])), changeset_id if isinstance(changeset_id, str) else None,
                     author if isinstance(author, str) else None)


def check_changesets(changesets: Sequence[ChangeSet]) -> List[str]:
    """Apply the ID, author and uniqueness rules."""
    errors: List[str] = []
    seen: Dict[str, ChangeSet] = {}
    for changeset in changesets:
        where = f"{changeset.path}:{changeset.line}"
        if changeset.id is None or not _ID.fullmatch(changeset.id):
            errors.append(f"{where}: changeset id {changeset.id!r} must look like <module>-<4 digits>-<slug>")
        if changeset.author != AUTHOR:
            errors.append(f"{where}: changeset author must be '{AUTHOR}' but was {changeset.author!r}")
        if changeset.id is not None:
            first = seen.get(changeset.id)
            if first is not None:
                errors.append(f"{where}: duplicate changeset id {changeset.id!r} (first at {first.path}:{first.line})")
            else:
                seen[changeset.id] = changeset
    return errors


def latest_release_tag(root: Path) -> Optional[str]:
    """Return the most recent ``v*`` tag reachable from HEAD, or None before the first release."""
    result = subprocess.run(["git", "describe", "--tags", "--abbrev=0", "--match", "v*"],
                            cwd=root, check=False, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    tag = result.stdout.decode("utf-8").strip()
    return tag if result.returncode == 0 and tag else None


def released_file(root: Path, tag: str, path: str) -> Optional[bytes]:
    """Return a file's content at ``tag``, or None if it did not exist there."""
    result = subprocess.run(["git", "show", f"{tag}:{path}"], cwd=root, check=False,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return result.stdout if result.returncode == 0 else None


def check_released(root: Path, tag: str, paths: Sequence[str]) -> List[str]:
    """Report production changelog files that changed or disappeared since ``tag``."""
    errors: List[str] = []
    listed = subprocess.run(["git", "ls-tree", "-r", "--name-only", tag], cwd=root, check=True,
                            stdout=subprocess.PIPE).stdout.decode("utf-8").splitlines()
    for path in sorted(p for p in listed if _CHANGELOG.search(p) and _PRODUCTION.search(p)):
        before = released_file(root, tag, path)
        current = root / path
        if path not in paths or not current.is_file():
            errors.append(f"{path}: released in {tag} and must not be removed")
        elif before is not None and current.read_bytes() != before:
            errors.append(f"{path}: released in {tag} and must not change; add a new changeset instead")
    return errors


def run(root: Path) -> Tuple[List[str], List[str]]:
    """Return (informational messages, errors) for the repository."""
    paths = [p for p in list_repository_files(root) if _CHANGELOG.search(p)]
    changesets: List[ChangeSet] = []
    for path in paths:
        changesets.extend(parse_changesets(path, (root / path).read_text(encoding="utf-8")))
    errors = check_changesets(changesets)
    info = [f"{len(changesets)} changeset(s) in {len(paths)} changelog file(s)"]
    tag = latest_release_tag(root)
    if tag is None:
        info.append("no release tag yet: changelog immutability is not enforced")
    else:
        info.append(f"released changelogs are locked at {tag}")
        errors.extend(check_released(root, tag, paths))
    return info, errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    args = parser.parse_args(argv)

    info, errors = run(args.root.resolve())
    for line in info:
        print(line)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("migrations ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
