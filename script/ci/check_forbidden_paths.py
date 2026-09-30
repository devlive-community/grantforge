#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Fail when the repository tracks files that must never be committed.

Two checks run:

1. No tracked (or staged) path may match a forbidden pattern: private agent
   memory (``.claude/``, ``.Codex/``), IDE state, build output, dependency
   folders, OS litter, and likely secrets such as private keys or ``.env`` files.
2. ``.gitignore`` must keep the entries that stop the private memory
   directories from being added in the first place.

Usage::

    python3 script/ci/check_forbidden_paths.py [--root DIR]
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

DEFAULT_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class Rule:
    """A forbidden path pattern (regex over repository-relative POSIX paths) and why."""

    pattern: "re.Pattern[str]"
    reason: str


def _rule(regex: str, reason: str) -> Rule:
    return Rule(re.compile(regex), reason)


# Any path segment equal to one of these directory names is forbidden.
_DIR = r"(?:^|.*/)"
FORBIDDEN: Tuple[Rule, ...] = (
    _rule(_DIR + r"\.claude/", "private Claude project memory"),
    _rule(_DIR + r"\.Codex/", "private Codex project memory"),
    _rule(_DIR + r"\.idea/", "IDE state"),
    _rule(r".*\.iml$", "IDE module file"),
    _rule(_DIR + r"\.vscode/", "IDE state"),
    _rule(_DIR + r"target/", "Maven build output"),
    _rule(_DIR + r"dist/", "build output"),
    _rule(_DIR + r"node_modules/", "installed dependencies"),
    _rule(_DIR + r"\.pnpm-store/", "pnpm package store"),
    _rule(_DIR + r"(?:coverage|test-results|playwright-report)/", "test output"),
    _rule(_DIR + r"__pycache__/", "Python bytecode cache"),
    _rule(r".*\.py[cod]$", "Python bytecode"),
    _rule(r"(?:^|.*/)\.DS_Store$", "macOS metadata"),
    _rule(r".*\.(?:versionsBackup|releaseBackup)$", "Maven versions plugin backup"),
    _rule(r"(?:^|.*/)\.env(?:\..+)?$", "environment file may contain secrets"),
    _rule(r".*\.(?:pem|key|p12|pfx|jks|keystore|keytab)$", "private key or keystore"),
    _rule(r"(?:^|.*/)id_(?:rsa|dsa|ecdsa|ed25519)(?:\.pub)?$", "SSH key"),
)

# Entries that must stay in .gitignore (compared after stripping whitespace).
REQUIRED_GITIGNORE: Tuple[str, ...] = (".claude/", "/.Codex/")

# Paths that match a forbidden rule but are legitimately tracked, with the reason.
ALLOWED: Tuple[Tuple[str, str], ...] = (
    (".env.example", "documented template without secrets"),
)


@dataclass(frozen=True)
class Finding:
    path: str
    reason: str


def tracked_paths(root: Path) -> List[str]:
    """Return every path in the index (tracked or staged), as POSIX strings."""
    result = subprocess.run(["git", "ls-files", "-z", "--cached"], cwd=root, check=True, stdout=subprocess.PIPE)
    return sorted(name for name in result.stdout.decode("utf-8").split("\0") if name)


def find_forbidden(paths: Sequence[str]) -> List[Finding]:
    """Return one finding per path that matches a forbidden rule and is not allowed."""
    allowed = {path for path, _ in ALLOWED}
    findings: List[Finding] = []
    for path in paths:
        if path.rsplit("/", 1)[-1] in allowed:
            continue
        for rule in FORBIDDEN:
            if rule.pattern.match(path):
                findings.append(Finding(path, rule.reason))
                break
    return findings


def missing_gitignore_entries(gitignore: Optional[str]) -> List[str]:
    """Return the required entries absent from the given .gitignore content (None = no file)."""
    present = {line.strip() for line in (gitignore or "").splitlines()}
    return [entry for entry in REQUIRED_GITIGNORE if entry not in present]


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    args = parser.parse_args(argv)
    root = args.root.resolve()

    findings = find_forbidden(tracked_paths(root))
    gitignore_path = root / ".gitignore"
    gitignore = gitignore_path.read_text(encoding="utf-8") if gitignore_path.is_file() else None
    missing = missing_gitignore_entries(gitignore)

    for finding in findings:
        print(f"{finding.path}: must not be committed ({finding.reason}); run: git rm --cached -- '{finding.path}'",
              file=sys.stderr)
    for entry in missing:
        print(f".gitignore: missing required entry '{entry}'", file=sys.stderr)
    if findings or missing:
        return 1
    print("forbidden paths ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
