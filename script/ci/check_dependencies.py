#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Scan dependency manifests and lockfiles for known vulnerabilities with OSV-Scanner.

Java dependencies are taken from a CycloneDX SBOM that Maven writes to
``target/bom.json``, so the scan sees exactly the versions Maven resolves
(including security overrides and internal modules that are not published).
Tracked lockfiles (pnpm, npm, yarn, pip requirements) are scanned as they are.
OSV-Scanner is pinned in ci_tools.py.

Every vulnerable package fails the check, except for sources matching a rule in
``dependency_report_only.txt`` (glob + mandatory reason), which are listed
without failing. That file is meant to stay empty.

Usage::

    python3 script/ci/check_dependencies.py [--rules FILE]
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ci_tools  # noqa: E402
from cilib import Exclusion, is_excluded, list_repository_files, load_exclusions  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RULES = Path(__file__).resolve().parent / "dependency_report_only.txt"
MAVEN_SBOM = "target/bom.json"
# Lockfile names OSV-Scanner understands that may be tracked in this repository.
_LOCKFILE = re.compile(r"(?:^|/)(?:pnpm-lock\.yaml|package-lock\.json|yarn\.lock|requirements[^/]*\.txt)$")

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


def lockfiles(paths: Sequence[str]) -> List[str]:
    """Return the tracked lockfiles OSV-Scanner should read."""
    return sorted(path for path in paths if _LOCKFILE.search(path))


def maven_sbom_command() -> List[str]:
    """Return the Maven invocation that writes the aggregate SBOM to ``target/bom.json``."""
    return ["./mvnw", "--batch-mode", "--no-transfer-progress", "--quiet", "-DskipFrontend",
            "org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom"]


def scan_command(scanner: Path, sources: Sequence[str], output: Path) -> List[str]:
    """Return the OSV-Scanner invocation over explicit sources (no pom re-resolution)."""
    command = [str(scanner), "scan", "source", "--format", "json", "--output-file", str(output)]
    for source in sources:
        command += ["--lockfile", source]
    return command


def run_scanner(root: Path, scanner: Path,
                run: Optional[Callable[[List[str], Path], int]] = None) -> Tuple[int, Dict]:
    """Write the Maven SBOM, scan it with the lockfiles and return (exit code, parsed JSON report).

    A failing SBOM build is returned as exit code 2 so it can never pass as "clean".
    """
    runner = run or (lambda command, cwd: subprocess.run(command, cwd=cwd, check=False).returncode)
    sources = lockfiles(list_repository_files(root))
    if (root / "pom.xml").is_file():
        if runner(maven_sbom_command(), root) != 0:
            print("error: Maven could not write the SBOM", file=sys.stderr)
            return 2, {}
        sources.insert(0, MAVEN_SBOM)
    with tempfile.TemporaryDirectory() as scratch:
        output = Path(scratch) / "osv.json"
        code = runner(scan_command(scanner, sources, output), root)
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
