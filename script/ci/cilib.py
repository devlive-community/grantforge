# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Shared helpers for the scripts in script/ci/: glob matching, rule files and git file listing."""

from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import List, Sequence


def glob_to_regex(pattern: str) -> "re.Pattern[str]":
    """Translate a gitignore-like glob into a regex over POSIX paths.

    ``**/`` matches zero or more directories, ``**`` matches anything,
    ``*`` matches within one path segment and ``?`` matches one character.
    """
    out: List[str] = []
    i = 0
    while i < len(pattern):
        if pattern.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
        elif pattern.startswith("**", i):
            out.append(".*")
            i += 2
        elif pattern[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pattern[i] == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(pattern[i]))
            i += 1
    return re.compile("".join(out))


@dataclass(frozen=True)
class Exclusion:
    """One exclusion rule; ``reason`` is mandatory so every exemption is justified."""

    pattern: str
    reason: str
    regex: "re.Pattern[str]"


def load_exclusions(path: Path) -> List[Exclusion]:
    """Parse the exclusion file.

    Format: one ``<glob>  # <reason>`` per line; blank lines and lines starting
    with ``#`` are ignored. A rule without a reason raises ``ValueError``.
    A missing file means "no exclusions".
    """
    if not path.is_file():
        return []
    rules: List[Exclusion] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        pattern, sep, reason = line.partition("#")
        pattern, reason = pattern.strip(), reason.strip()
        if not sep or not reason:
            raise ValueError(f"{path}:{number}: exclusion '{pattern}' must state a reason after '#'")
        rules.append(Exclusion(pattern, reason, glob_to_regex(pattern)))
    return rules


def is_excluded(path: str, rules: Sequence[Exclusion]) -> bool:
    """Return True when any exclusion rule matches the repository-relative path."""
    return any(rule.regex.fullmatch(path) for rule in rules)


def list_repository_files(root: Path) -> List[str]:
    """Return tracked plus untracked-but-not-ignored files that still exist on disk."""
    result = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    )
    names = {name for name in result.stdout.decode("utf-8").split("\0") if name}
    # Deleted-but-not-yet-staged files are still listed by --cached; skip them.
    return sorted(name for name in names if (root / name).is_file())
