#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Validate commit messages against the project's Conventional Commits rules.

Rules: ``<type>(<scope>)!: <subject>`` header of at most 72 characters, lowercase
imperative subject without a trailing period, blank second line, body lines of at
most 100 characters (URLs excepted), English only, no AI attribution trailers and
no absolute personal paths. Merge commits and commits authored by automation
bots (Dependabot, GitHub Actions), whose messages the project does not control, are skipped.

Usage::

    # every commit reachable from HEAD but not from BASE (PR or push range)
    python3 script/ci/check_commit_messages.py --base <sha> --head <sha>
    # a single message file (e.g. from a commit-msg hook)
    python3 script/ci/check_commit_messages.py --file .git/COMMIT_EDITMSG

When ``--base`` is empty or all zeros (a new branch push), only ``--head`` is checked.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Sequence

TYPES = ("feat", "fix", "perf", "refactor", "test", "docs", "style", "i18n", "build", "ci", "chore", "revert")
MAX_HEADER = 72
MAX_BODY_LINE = 100

_HEADER = re.compile(
    r"^(?P<type>" + "|".join(TYPES) + r")(?:\((?P<scope>[a-z0-9][a-z0-9._/-]*)\))?!?: (?P<subject>\S.*)$"
)

# Non-imperative first words (past tense, third person, gerund) seen in practice.
_NON_IMPERATIVE = frozenset(
    """added fixed updated removed changed implemented refactored improved created deleted renamed moved
    bumped upgraded cleaned replaced introduced supported enabled disabled
    adds fixes updates removes changes implements refactors improves creates deletes renames moves
    bumps upgrades cleans replaces introduces supports enables disables
    adding fixing updating removing changing implementing refactoring improving""".split()
)
_VAGUE = re.compile(
    r"^(fix(ed)? bugs?|update(d)? code|wip|temp|tmp|misc|changes|update|updates|minor (fix|changes)"
    r"|some (fix|fixes|changes)|clean ?up code)$",
    re.IGNORECASE,
)
# CJK ideographs, kana and hangul (English-only messages).
_CJK = re.compile("[぀-ヿ㐀-䶿一-鿿가-힯豈-﫿]")
_AI_ATTRIBUTION = re.compile(
    r"co-authored-by:.*(claude|anthropic|openai|chatgpt|gpt|copilot|cursor|gemini|codex)"
    r"|generated (with|by) (claude|ai|chatgpt|copilot|cursor)|noreply@anthropic\.com|\U0001F916",
    re.IGNORECASE,
)
_PERSONAL_PATH = re.compile(r"/Users/[^/ ]+/|/home/[^/ ]+/|[A-Z]:\\Users\\")
_ZERO_SHA = re.compile(r"^0+$")
# Bot author e-mails: their generated messages cannot follow the project format.
BOT_AUTHOR_EMAILS = frozenset({
    "49699333+dependabot[bot]@users.noreply.github.com",
    "41898282+github-actions[bot]@users.noreply.github.com",
})


@dataclass(frozen=True)
class Commit:
    sha: str
    message: str
    parents: int
    author_email: str = ""


def validate_message(message: str) -> List[str]:
    """Return the list of rule violations for one commit message (empty when valid).

    Git comment lines (``#``) and trailing blank lines are ignored, matching what
    ``git commit`` stores.
    """
    lines = [line for line in message.replace("\r\n", "\n").split("\n") if not line.startswith("#")]
    while lines and not lines[-1].strip():
        lines.pop()
    if not lines:
        return ["message is empty"]

    errors: List[str] = []
    header = lines[0]
    match = _HEADER.match(header)
    if match is None:
        errors.append(f"header must match '<type>(<scope>): <subject>' with type in {', '.join(TYPES)} "
                      f"(got: {header!r})")
    if len(header) > MAX_HEADER:
        errors.append(f"header is {len(header)} characters (max {MAX_HEADER})")
    if header.endswith("."):
        errors.append("header must not end with a period")

    if match is not None and match.group("type") != "revert":
        subject = match.group("subject")
        first_word = subject.split()[0]
        if first_word[:1].isupper():
            errors.append(f"subject must start with a lowercase verb (got: {first_word!r})")
        if first_word.lower() in _NON_IMPERATIVE:
            errors.append(f"subject must use the imperative mood (got: {first_word!r})")
        if _VAGUE.match(subject):
            errors.append(f"subject is too vague: {subject!r}")

    if len(lines) > 1 and lines[1].strip():
        errors.append("line 2 must be blank (separates header from body)")
    for number, line in enumerate(lines[1:], start=2):
        if "://" not in line and len(line) > MAX_BODY_LINE:
            errors.append(f"line {number} is {len(line)} characters (max {MAX_BODY_LINE})")

    text = "\n".join(lines)
    if _CJK.search(text):
        errors.append("message contains CJK characters (English only)")
    if _AI_ATTRIBUTION.search(text):
        errors.append("message contains AI attribution (remove Co-Authored-By / Generated-with lines)")
    if _PERSONAL_PATH.search(text):
        errors.append("message contains an absolute personal path")
    return errors


def _is_commit(root: Path, revision: str) -> bool:
    return subprocess.run(["git", "cat-file", "-e", f"{revision}^{{commit}}"], cwd=root, check=False,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def resolve_base(root: Path, base: Optional[str], head: str, fallback: Optional[str]) -> Optional[str]:
    """Return the range start to use, or None to check ``head`` alone.

    A push reports the branch's previous tip as ``base``. After a force push that commit is gone from the clone;
    the branch is then checked from where it left ``fallback`` (the default branch), as a pull request would be.
    """
    if not base or _ZERO_SHA.match(base):
        return None
    if _is_commit(root, base):
        return base
    if fallback and _is_commit(root, fallback):
        result = subprocess.run(["git", "merge-base", fallback, head], cwd=root, check=False, stdout=subprocess.PIPE)
        if result.returncode == 0:
            print(f"{base[:10]} is not in this clone (a force push?); checking from {fallback}")
            return result.stdout.decode("utf-8").strip()
    print(f"{base[:10]} is not in this clone; checking {head} alone")
    return None


def commits_in_range(root: Path, base: Optional[str], head: str) -> List[Commit]:
    """Return commits reachable from ``head`` but not ``base``; only ``head`` when base is unusable."""
    # A new-branch push reports an all-zero 'before' SHA: there is no range, so check head only.
    revision = ["-1", head] if not base or _ZERO_SHA.match(base) else [f"{base}..{head}"]
    # %x1e separates records, %x1f separates fields; neither appears in normal messages.
    result = subprocess.run(
        ["git", "log", "--format=%H%x1f%P%x1f%ae%x1f%B%x1e", *revision],
        cwd=root, check=True, stdout=subprocess.PIPE,
    )
    commits: List[Commit] = []
    for record in result.stdout.decode("utf-8").split("\x1e"):
        record = record.strip("\n")
        if not record:
            continue
        sha, parents, email, message = record.split("\x1f", 3)
        commits.append(Commit(sha, message, len(parents.split()), email))
    return commits


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="", help="exclusive range start (PR base or push 'before')")
    parser.add_argument("--head", default="HEAD", help="inclusive range end")
    parser.add_argument("--fallback", default="", help="branch to check from when --base is gone, such as origin/dev")
    parser.add_argument("--file", type=Path, help="validate a single message file instead of a range")
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root")
    args = parser.parse_args(argv)

    if args.file is not None:
        errors = validate_message(args.file.read_text(encoding="utf-8"))
        for error in errors:
            print(f"{args.file}: {error}", file=sys.stderr)
        return 1 if errors else 0

    failed = 0
    base = resolve_base(args.root, args.base, args.head, args.fallback or None)
    commits = commits_in_range(args.root, base, args.head)
    for commit in commits:
        if commit.parents > 1 or commit.author_email in BOT_AUTHOR_EMAILS:
            continue  # merge commits and bot commits are generated by the platform
        errors = validate_message(commit.message)
        if errors:
            failed += 1
            header = commit.message.split("\n", 1)[0]
            print(f"{commit.sha[:10]} {header}", file=sys.stderr)
            for error in errors:
                print(f"    - {error}", file=sys.stderr)
    if failed:
        print(f"\n{failed} of {len(commits)} commit(s) violate the commit message rules.", file=sys.stderr)
        return 1
    print(f"commit messages ok ({len(commits)} checked)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
