#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Keep every copy of the release version equal to the root pom's, or change them all.

GrantForge versions are ``YEAR.MINOR.PATCH`` (``2026.0.0``), optionally with a
release candidate suffix (``2026.1.0-rc.1``); a release is the tag ``v`` plus the
version. The version is written in the Maven poms (as the project or the parent
version), the npm packages, the Helm chart's appVersion, the console's sidebar,
the README badge and the sample jars the browser tests start. This check fails
when one of them differs from the root pom.

Usage::

    python3 script/ci/check_versions.py [--root DIR]            # check
    python3 script/ci/check_versions.py --set 2026.1.0           # change every copy
    python3 script/ci/check_versions.py --show                   # print the version
    python3 script/ci/check_versions.py --tag v2026.1.0          # check a release tag
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Sequence

DEFAULT_ROOT = Path(__file__).resolve().parents[2]

VERSION = re.compile(r"^\d{4}\.\d+\.\d+(?:-rc\.\d+)?$")
_V = r"\d{4}\.\d+\.\d+(?:-rc\.\d+)?"

# The part of a pom before its first section holds the parent's and the project's own version.
_POM_HEAD_END = re.compile(r"<(?:properties|modules|dependencyManagement|dependencies|build|profiles)>")
_POM_VERSION = re.compile(r"(<version>)(" + _V + r")(</version>)")


@dataclass(frozen=True)
class Carrier:
    """A file holding the version, and the pattern whose second group is the version."""

    path: str
    pattern: "re.Pattern[str]"


_PACKAGE = r'^(  "version": ")(' + _V + r')(")'

# Files other than the poms; each pattern must match at least once.
CARRIERS = (
    Carrier("core/grantforge-web/package.json", re.compile(_PACKAGE, re.M)),
    Carrier("docs/package.json", re.compile(_PACKAGE, re.M)),
    Carrier("sdk/grantforge-js/package.json", re.compile(_PACKAGE, re.M)),
    Carrier("deploy/helm/grantforge/Chart.yaml", re.compile(r'^(appVersion: ")(' + _V + r')(")', re.M)),
    Carrier("core/grantforge-web/src/layouts/AppLayout.vue",
            re.compile(r'(<span class="font-mono">v)(' + _V + r')(</span>)')),
    Carrier("README.md", re.compile(r"(badge/version-)(" + _V + r")(-)")),
    Carrier("core/grantforge-web/tests/samples/setup.ts",
            re.compile(r"(grantforge-sample-[a-z]+-)(" + _V + r")(\.jar)")),
)


@dataclass(frozen=True)
class Mismatch:
    path: str
    found: str


def tracked_poms(root: Path) -> List[str]:
    """Return the tracked pom.xml files."""
    result = subprocess.run(["git", "ls-files", "-z", "--", "pom.xml", "*/pom.xml"], cwd=root, check=True,
                            stdout=subprocess.PIPE)
    return sorted(name for name in result.stdout.decode("utf-8").split("\0") if name)


def pom_head(text: str) -> str:
    """Return the part of a pom that declares its parent and its own coordinates."""
    end = _POM_HEAD_END.search(text)
    return text[:end.start()] if end else text


def root_version(root: Path) -> str:
    """Return the version of the root pom."""
    found = _POM_VERSION.findall(pom_head((root / "pom.xml").read_text(encoding="utf-8")))
    if not found:
        raise ValueError("the root pom.xml declares no version")
    return found[-1][1]


def _versions(text: str, pattern: "re.Pattern[str]") -> List[str]:
    return [match.group(2) for match in pattern.finditer(text)]


def find_mismatches(root: Path, expected: str, poms: Sequence[str]) -> List[Mismatch]:
    """Return every copy of the version that is not the expected one; a carrier without any copy is one too."""
    mismatches: List[Mismatch] = []
    for pom in poms:
        for version in _versions(pom_head((root / pom).read_text(encoding="utf-8")), _POM_VERSION):
            if version != expected:
                mismatches.append(Mismatch(pom, version))
    for carrier in CARRIERS:
        path = root / carrier.path
        versions = _versions(path.read_text(encoding="utf-8"), carrier.pattern) if path.is_file() else []
        if not versions:
            mismatches.append(Mismatch(carrier.path, "no version found"))
        mismatches.extend(Mismatch(carrier.path, version) for version in versions if version != expected)
    return mismatches


def _rewrite(path: Path, version: str, pattern: "re.Pattern[str]", head_only: bool) -> None:
    text = path.read_text(encoding="utf-8")

    def replace(match: "re.Match[str]") -> str:
        return match.group(1) + version + match.group(3)

    if head_only:
        head = pom_head(text)
        text = pattern.sub(replace, head) + text[len(head):]
    else:
        text = pattern.sub(replace, text)
    path.write_text(text, encoding="utf-8")


def set_version(root: Path, version: str, poms: Sequence[str]) -> None:
    """Write the version into every copy."""
    if not VERSION.match(version):
        raise ValueError(f"{version} is not YEAR.MINOR.PATCH, optionally with -rc.N")
    for pom in poms:
        _rewrite(root / pom, version, _POM_VERSION, head_only=True)
    for carrier in CARRIERS:
        _rewrite(root / carrier.path, version, carrier.pattern, head_only=False)


def check_tag(tag: str, version: str) -> Optional[str]:
    """Return why a release tag does not fit the version, or None when it does."""
    if tag != "v" + version:
        return f"tag {tag} does not match the version {version} (expected v{version})"
    return None


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--set", metavar="VERSION", help="change every copy of the version")
    action.add_argument("--show", action="store_true", help="print the version")
    action.add_argument("--tag", help="check that a release tag names the version")
    args = parser.parse_args(argv)
    root: Path = args.root.resolve()
    poms = tracked_poms(root)
    if args.set:
        try:
            set_version(root, args.set, poms)
        except ValueError as error:
            print(error, file=sys.stderr)
            return 2
        print(f"version set to {args.set} in {len(poms) + len(CARRIERS)} files")
    version = root_version(root)
    if args.show:
        print(version)
        return 0
    if args.tag:
        problem = check_tag(args.tag, version)
        if problem:
            print(problem, file=sys.stderr)
            return 1
    if not VERSION.match(version):
        print(f"root pom version {version} is not YEAR.MINOR.PATCH, optionally with -rc.N", file=sys.stderr)
        return 1
    mismatches = find_mismatches(root, version, poms)
    for mismatch in mismatches:
        print(f"{mismatch.path}: {mismatch.found} (expected {version})", file=sys.stderr)
    if mismatches:
        print(f"{len(mismatches)} version mismatch(es). Run: python3 script/ci/check_versions.py --set {version}",
              file=sys.stderr)
        return 1
    print(f"versions ok ({version})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
