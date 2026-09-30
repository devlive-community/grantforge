#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Check the basic text format rules declared in the root .editorconfig.

Every tracked (or untracked but not ignored) text file must:

* be valid UTF-8,
* use LF line endings,
* end with a newline (empty files are fine),
* have no trailing whitespace (Markdown is exempt: two trailing spaces are a line break),
* indent with spaces, not tabs.

Files listed in ``format_exclusions.txt`` (with a reason) are skipped. ``--fix``
repairs line endings, the final newline and trailing whitespace; tab indentation
and encoding problems must be fixed by hand.

Usage::

    python3 script/ci/check_file_format.py [--fix] [paths ...]
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

# Allow "import cilib" when this file is loaded by path (tests) rather than executed directly.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from cilib import is_excluded, list_repository_files, load_exclusions  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_EXCLUSIONS = Path(__file__).resolve().parent / "format_exclusions.txt"

_TRAILING = re.compile(r"[ \t]+$", re.MULTILINE)
_TAB_INDENT = re.compile(r"^ *\t", re.MULTILINE)


@dataclass(frozen=True)
class Finding:
    path: str
    reason: str


def check_text(text: str, path: str) -> List[str]:
    """Return every rule violated by ``text`` (the decoded content of ``path``)."""
    problems: List[str] = []
    if "\r" in text:
        problems.append("uses CR/CRLF line endings (LF required)")
    if text and not text.endswith("\n"):
        problems.append("does not end with a newline")
    if not path.endswith(".md"):
        match = _TRAILING.search(text.replace("\r", ""))
        if match is not None:
            problems.append(f"trailing whitespace on line {text.count(chr(10), 0, match.start()) + 1}")
    match = _TAB_INDENT.search(text)
    if match is not None:
        problems.append(f"tab indentation on line {text.count(chr(10), 0, match.start()) + 1}")
    return problems


def fix_text(text: str, path: str) -> str:
    """Normalise line endings, trailing whitespace (except Markdown) and the final newline."""
    fixed = text.replace("\r\n", "\n").replace("\r", "\n")
    if not path.endswith(".md"):
        fixed = _TRAILING.sub("", fixed)
    if fixed and not fixed.endswith("\n"):
        fixed += "\n"
    return fixed


def run(root: Path, exclusions: Path, paths: Sequence[str], fix: bool) -> Tuple[List[Finding], List[str]]:
    """Check (and optionally fix) files; return (remaining findings, fixed paths)."""
    rules = load_exclusions(exclusions)
    findings: List[Finding] = []
    fixed: List[str] = []
    for relative in (list(paths) or list_repository_files(root)):
        if is_excluded(relative, rules):
            continue
        file_path = root / relative
        raw = file_path.read_bytes()
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            findings.append(Finding(relative, "is not valid UTF-8 (binary files need an exclusion)"))
            continue
        problems = check_text(text, relative)
        if problems and fix:
            repaired = fix_text(text, relative)
            if repaired != text:
                file_path.write_bytes(repaired.encode("utf-8"))
                fixed.append(relative)
            problems = check_text(repaired, relative)
        findings.extend(Finding(relative, problem) for problem in problems)
    return findings, fixed


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", nargs="*", help="repository-relative files to process (default: all)")
    parser.add_argument("--fix", action="store_true", help="repair line endings, final newline and trailing spaces")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    parser.add_argument("--exclusions", type=Path, default=DEFAULT_EXCLUSIONS, help="exclusion rule file")
    args = parser.parse_args(argv)

    try:
        findings, fixed = run(args.root.resolve(), args.exclusions, args.paths, args.fix)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    for path in fixed:
        print(f"fixed: {path}")
    for finding in findings:
        print(f"{finding.path}: {finding.reason}", file=sys.stderr)
    if findings:
        print(f"\n{len(findings)} format problem(s). Run: python3 script/ci/check_file_format.py --fix",
              file=sys.stderr)
        return 1
    print("file format ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
