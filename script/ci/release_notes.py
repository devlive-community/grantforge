#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Write the release notes of a version from the commit history (D-90).

The notes list every commit since the previous release, grouped by its conventional
commit type, each with a link to the commit, after a link comparing the two tags.
Breaking changes come first; merge commits are left out. A release tag is ``v`` and a
version (``v2026.1.0``), or a bare version as AuthX tagged its releases (``1.0.6``);
marker tags such as ``legacy-2026.0.0`` are not releases. Without any earlier release
the whole history is listed.

Usage::

    python3 script/ci/release_notes.py v2026.1.0 [--head v2026.1.0] [--output FILE] [--repo OWNER/NAME]

``--head`` defaults to the tag; ``script/release/tag.sh`` passes ``HEAD`` to preview
the notes of a tag it has not made yet. The repository defaults to
``$GITHUB_REPOSITORY``, then to the ``origin`` remote.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

DEFAULT_ROOT = Path(__file__).resolve().parents[2]

# GitHub refuses release bodies above 125000 characters.
MAX_BODY = 120_000

_HEADER = re.compile(r"^(?P<type>[a-z]+)(?:\((?P<scope>[^)]+)\))?(?P<breaking>!)?: (?P<subject>.+)$")
_REMOTE = re.compile(r"github\.com[:/](?P<repo>[^/\s]+/[^/\s]+?)(?:\.git)?$")

# Sections in their order, with the commit types each holds; types not listed go to the last section.
SECTIONS: Tuple[Tuple[str, Tuple[str, ...]], ...] = (
    ("新功能", ("feat",)),
    ("问题修复", ("fix",)),
    ("性能", ("perf",)),
    ("重构", ("refactor",)),
    ("文档", ("docs",)),
    ("测试", ("test",)),
    ("构建与 CI", ("build", "ci")),
    ("其他", ()),
)


@dataclass(frozen=True)
class Commit:
    sha: str
    header: str
    body: str


def git(root: Path, *args: str) -> str:
    result = subprocess.run(["git", *args], cwd=root, check=True, stdout=subprocess.PIPE)
    return result.stdout.decode("utf-8").strip()


def previous_tag(root: Path, head: str) -> Optional[str]:
    """Return the latest release tag reachable from the commit before ``head``, or None for the first release."""
    result = subprocess.run(["git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", "--match", "[0-9]*",
                             f"{head}^"], cwd=root,
                            check=False, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return result.stdout.decode("utf-8").strip() or None if result.returncode == 0 else None


def commits(root: Path, previous: Optional[str], head: str) -> List[Commit]:
    """Return the commits since ``previous`` (all of them without one), oldest first, merges left out."""
    revision = f"{previous}..{head}" if previous else head
    # Raw output: str.strip() takes the \x1e and \x1f separators for white space.
    command = ["git", "log", "--reverse", "--no-merges", "--format=%H%x1f%s%x1f%b%x1e", revision]
    output = subprocess.run(command, cwd=root, check=True, stdout=subprocess.PIPE).stdout.decode("utf-8")
    found: List[Commit] = []
    for record in output.split("\x1e"):
        record = record.lstrip("\n")
        if record.strip():
            sha, header, body = record.split("\x1f", 2)
            found.append(Commit(sha, header, body))
    return found


def repository(root: Path, given: Optional[str]) -> str:
    """Return ``owner/name`` of the GitHub repository."""
    if given:
        return given
    if os.environ.get("GITHUB_REPOSITORY"):
        return os.environ["GITHUB_REPOSITORY"]
    match = _REMOTE.search(git(root, "remote", "get-url", "origin"))
    if not match:
        raise ValueError("cannot tell the GitHub repository; pass --repo OWNER/NAME")
    return match.group("repo")


def breaks(commit: Commit) -> bool:
    """Whether a commit is marked as breaking, by ``!`` in its header or a BREAKING CHANGE footer."""
    match = _HEADER.match(commit.header)
    return bool(match and match.group("breaking")) or "BREAKING CHANGE" in commit.body


def section_of(commit_type: str) -> str:
    for title, types in SECTIONS:
        if commit_type in types:
            return title
    return SECTIONS[-1][0]


def line(commit: Commit, repo: str) -> str:
    match = _HEADER.match(commit.header)
    text = commit.header
    if match:
        scope = match.group("scope")
        text = f"**{scope}**: {match.group('subject')}" if scope else match.group("subject")
    return f"- {text} ([{commit.sha[:8]}](https://github.com/{repo}/commit/{commit.sha}))"


def notes(found: Sequence[Commit], repo: str, previous: Optional[str], tag: str) -> str:
    """Return the notes: the comparison link, breaking changes, then the commits by section."""
    parts: List[str] = []
    if previous:
        parts.append(f"**完整变更**：[{previous}...{tag}](https://github.com/{repo}/compare/{previous}...{tag})"
                     f"，共 {len(found)} 个提交。")
    else:
        parts.append(f"首个发布版本，包含仓库的全部 {len(found)} 个提交。")
    breaking = [commit for commit in found if breaks(commit)]
    if breaking:
        parts.append("## 不兼容变更\n\n" + "\n".join(line(commit, repo) for commit in breaking))
    grouped: Dict[str, List[str]] = {title: [] for title, _ in SECTIONS}
    for commit in reversed(found):
        match = _HEADER.match(commit.header)
        grouped[section_of(match.group("type") if match else "")].append(line(commit, repo))
    for title, _ in SECTIONS:
        if grouped[title]:
            parts.append(f"## {title}\n\n" + "\n".join(grouped[title]))
    body = "\n\n".join(parts) + "\n"
    if len(body) > MAX_BODY:
        cut = body.rfind("\n", 0, MAX_BODY - 200)
        body = body[:cut] + f"\n\n……其余提交见 [提交历史](https://github.com/{repo}/commits/{tag})。\n"
    return body


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("tag", help="the release tag, such as v2026.1.0")
    parser.add_argument("--head", help="the commit the release is made of; the tag by default")
    parser.add_argument("--output", type=Path, help="file to write; standard output by default")
    parser.add_argument("--repo", help="GitHub repository OWNER/NAME")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    args = parser.parse_args(argv)
    root: Path = args.root.resolve()
    head = args.head or args.tag
    try:
        repo = repository(root, args.repo)
        previous = previous_tag(root, head)
        written = notes(commits(root, previous, head), repo, previous, args.tag)
    except (subprocess.CalledProcessError, ValueError) as failure:
        print(f"cannot write the release notes of {args.tag}: {failure}", file=sys.stderr)
        return 1
    if args.output:
        args.output.write_text(written, encoding="utf-8")
        since = f"since {previous}" if previous else "whole history"
        print(f"release notes of {args.tag} ({since}) written to {args.output}")
    else:
        sys.stdout.write(written)
    return 0


if __name__ == "__main__":
    sys.exit(main())
