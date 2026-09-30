#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Scan dependency manifests and lockfiles for known vulnerabilities with OSV-Scanner.

OSV-Scanner (pinned in ci_tools.py) resolves Maven poms, the pnpm lockfile and
Python requirement files and checks them against osv.dev. Every vulnerable
package fails the check, except for manifests matching a rule in
``dependency_report_only.txt`` (glob + mandatory reason), which are listed without
failing. That file is meant to stay empty; it is used only while the legacy
backend awaiting replacement is still in the tree.

Usage::

    python3 script/ci/check_dependencies.py [--rules FILE]
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ci_tools  # noqa: E402
from cilib import Exclusion, is_excluded, load_exclusions  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RULES = Path(__file__).resolve().parent / "dependency_report_only.txt"

# OSV-Scanner exit codes: 0 = clean, 1 = vulnerabilities found, 128 = no packages found.
_EXIT_CLEAN, _EXIT_VULNERABLE, _EXIT_NO_PACKAGES = 0, 1, 128


@dataclass(frozen=True)
class VulnerablePackage:
    source: str  # manifest path relative to the repository root
    name: str
    version: str
    ids: Tuple[str, ...]
    max_severity: str


def parse_report(report: Dict, root: Path) -> List[VulnerablePackage]:
    """Flatten an OSV-Scanner JSON report into one entry per vulnerable package.

    Missing keys are tolerated so that format additions in newer scanner versions
    never crash the check; a package without vulnerability ids is still reported.
    """
    prefix = f"{root.resolve().as_posix().rstrip('/')}/"
    packages: List[VulnerablePackage] = []
    for result in report.get("results") or []:
        path = str((result.get("source") or {}).get("path") or "?")
        source = path[len(prefix):] if path.startswith(prefix) else path
        for package in result.get("packages") or []:
            info = package.get("package") or {}
            ids = tuple(v.get("id", "?") for v in package.get("vulnerabilities") or [])
            severities = [g.get("max_severity") for g in package.get("groups") or [] if g.get("max_severity")]
            packages.append(VulnerablePackage(
                source=source,
                name=str(info.get("name", "?")),
                version=str(info.get("version", "?")),
                ids=ids,
                max_severity=max(severities, key=_severity_value) if severities else "unknown",
            ))
    return packages


def _severity_value(score: str) -> float:
    try:
        return float(score)
    except ValueError:
        return -1.0


def split_report_only(packages: Sequence[VulnerablePackage],
                      rules: Sequence[Exclusion]) -> Tuple[List[VulnerablePackage], List[VulnerablePackage]]:
    """Split into (blocking, report-only) by matching each manifest path against the rules."""
    blocking = [p for p in packages if not is_excluded(p.source, rules)]
    reported = [p for p in packages if is_excluded(p.source, rules)]
    return blocking, reported


def run_scanner(root: Path, scanner: Path) -> Tuple[int, Dict]:
    """Run OSV-Scanner recursively over ``root`` and return (exit code, parsed JSON report)."""
    with tempfile.TemporaryDirectory() as scratch:
        output = Path(scratch) / "osv.json"
        code = subprocess.run(
            [str(scanner), "scan", "source", "--recursive", "--format", "json", "--output-file", str(output), "."],
            cwd=root, check=False,
        ).returncode
        report = json.loads(output.read_text(encoding="utf-8")) if output.is_file() else {}
    return code, report


def main(argv: Optional[Sequence[str]] = None, root: Path = DEFAULT_ROOT,
         scan: Optional[Callable[[Path], Tuple[int, Dict]]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rules", type=Path, default=DEFAULT_RULES, help="report-only rule file")
    args = parser.parse_args(argv)
    try:
        rules = load_exclusions(args.rules)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    scanner = scan or (lambda r: run_scanner(r, ci_tools.ensure("osv-scanner", r)))
    code, report = scanner(root)
    if code == _EXIT_NO_PACKAGES:
        print("dependencies ok (no packages found)")
        return 0
    if code not in (_EXIT_CLEAN, _EXIT_VULNERABLE):
        print(f"error: osv-scanner failed with exit code {code}", file=sys.stderr)
        return 2

    blocking, reported = split_report_only(parse_report(report, root), rules)
    for package in reported:
        print(f"report only: {package.source}: {package.name} {package.version} "
              f"(max CVSS {package.max_severity}; {', '.join(package.ids)})")
    for package in blocking:
        print(f"{package.source}: {package.name} {package.version} is vulnerable "
              f"(max CVSS {package.max_severity}; {', '.join(package.ids)})", file=sys.stderr)
    if blocking:
        print(f"\n{len(blocking)} vulnerable package(s). Upgrade them or document why they are not exploitable.",
              file=sys.stderr)
        return 1
    print(f"dependencies ok ({len(reported)} report-only finding(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
