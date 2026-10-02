#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Keep the plugin API compatible within a major version.

Plugins are built against ``grantforge-plugin-api`` and loaded by servers of later releases, so a release may only
break the API (binary or source) when it raises the major part of ``ApiVersion.CURRENT``; the server refuses plugins
built for another major version. This check builds the plugin API of a base release in a temporary worktree and of the
working tree, compares the jars with japicmp and fails when they are incompatible while the major version stayed.

The base defaults to the latest release tag (``v<major>.<minor>.<patch>``); before the first release there is nothing
to compare with and the check passes. ``--base`` compares with any commit instead, such as the target branch of a
change. An HTML report is written to ``target/plugin-api-compat/report.html``.

Usage::

    python3 script/ci/check_plugin_api_compat.py [--base REF] [--root DIR]
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Iterable, List, Optional, Sequence, Tuple

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
MODULE = "core/grantforge-plugin-api"
API_VERSION = MODULE + "/src/main/java/org/devlive/grantforge/plugin/api/ApiVersion.java"
JAPICMP = "com.github.siom79.japicmp:japicmp:0.25.0:jar:jar-with-dependencies"
_RELEASE_TAG = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
_CURRENT = re.compile(r"ApiVersion\s+CURRENT\s*=\s*new\s+ApiVersion\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)")


def latest_release(tags: Iterable[str]) -> Optional[str]:
    """Return the highest release tag (``v1.2.3``), ignoring other tags; None if there is none."""
    releases: List[Tuple[Tuple[int, int, int], str]] = []
    for tag in tags:
        match = _RELEASE_TAG.match(tag.strip())
        if match:
            releases.append(((int(match.group(1)), int(match.group(2)), int(match.group(3))), tag.strip()))
    return max(releases)[1] if releases else None


def api_major(source: str) -> int:
    """Return the major part of ``ApiVersion.CURRENT`` declared in ApiVersion.java."""
    match = _CURRENT.search(source)
    if not match:
        raise ValueError("ApiVersion.CURRENT is not declared as new ApiVersion(major, minor, patch)")
    return int(match.group(1))


def verdict(compatible: bool, base_major: int, major: int) -> Tuple[bool, str]:
    """Return (passes, message) for a comparison result and the major versions before and after."""
    if major < base_major:
        return False, f"ApiVersion.CURRENT went back from major {base_major} to {major}"
    if compatible:
        return True, f"the plugin API is compatible with the base (major {base_major} -> {major})"
    if major > base_major:
        return True, (f"the plugin API breaks compatibility, as the raised major version "
                      f"({base_major} -> {major}) allows")
    return False, (f"the plugin API breaks compatibility within major version {major}: keep the old signatures, "
                   "or raise the major part of ApiVersion.CURRENT so servers refuse plugins built for the old API")


def _run(command: Sequence[str], cwd: Path) -> subprocess.CompletedProcess:
    return subprocess.run(list(command), cwd=cwd, check=False, text=True, capture_output=True)


def _git(root: Path, *arguments: str) -> str:
    result = _run(["git", *arguments], root)
    if result.returncode != 0:
        raise RuntimeError(f"git {' '.join(arguments)} failed: {result.stderr.strip()}")
    return result.stdout


def _build_jar(checkout: Path) -> Path:
    result = _run(["./mvnw", "--batch-mode", "--no-transfer-progress", "--quiet", "-Dmaven.test.skip=true",
                   "-DskipFrontend", "-pl", MODULE, "-am", "package"], checkout)
    if result.returncode != 0:
        raise RuntimeError(f"building the plugin API in {checkout} failed:\n{result.stdout}{result.stderr}")
    jars = [jar for jar in (checkout / MODULE / "target").glob("grantforge-plugin-api-*.jar")
            if not jar.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if len(jars) != 1:
        raise RuntimeError(f"expected one plugin API jar in {checkout / MODULE / 'target'}, found {len(jars)}")
    return jars[0]


def _japicmp(root: Path, work: Path) -> Path:
    result = _run(["./mvnw", "--batch-mode", "--no-transfer-progress", "--quiet", "dependency:copy",
                   f"-Dartifact={JAPICMP}", f"-DoutputDirectory={work}"], root)
    if result.returncode != 0:
        raise RuntimeError(f"fetching japicmp failed:\n{result.stdout}{result.stderr}")
    return next(work.glob("japicmp-*.jar"))


def compare(root: Path, base: str) -> Tuple[bool, str]:
    """Build both plugin APIs, compare them and return (passes, message)."""
    try:
        base_source = _git(root, "show", f"{base}:{API_VERSION}")
    except RuntimeError:
        return True, f"{base} has no plugin API; nothing to compare with"
    work = root / "target" / "plugin-api-compat"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix="plugin-api-base-") as temporary:
        checkout = Path(temporary) / "base"
        _git(root, "worktree", "add", "--detach", str(checkout), base)
        try:
            old_jar = shutil.copy(_build_jar(checkout), work / "old.jar")
        finally:
            _git(root, "worktree", "remove", "--force", str(checkout))
    new_jar = shutil.copy(_build_jar(root), work / "new.jar")
    result = _run(["java", "-jar", str(_japicmp(root, work)), "--old", str(old_jar), "--new", str(new_jar),
                   "--only-modified", "--ignore-missing-classes", "--error-on-binary-incompatibility",
                   "--error-on-source-incompatibility", "--html-file", str(work / "report.html")], root)
    print(result.stdout)
    major = api_major((root / API_VERSION).read_text(encoding="utf-8"))
    return verdict(result.returncode == 0, api_major(base_source), major)


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", help="the commit to compare with; the latest release tag by default")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    args = parser.parse_args(argv)
    root = args.root.resolve()

    base = args.base or latest_release(_git(root, "tag", "--list").splitlines())
    if base is None:
        print("no release tag yet (v<major>.<minor>.<patch>); the plugin API has nothing to stay compatible with")
        return 0
    passes, message = compare(root, base)
    print(message, file=sys.stdout if passes else sys.stderr)
    return 0 if passes else 1


if __name__ == "__main__":
    sys.exit(main())
