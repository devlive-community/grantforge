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

Every vulnerable package fails the check. Legacy non-Maven report-only sources
are documented in ``dependency_report_only.txt``. Maven compatibility exceptions
in ``dependency_exceptions.json`` match exact coordinates and individual GHSA ids,
with per-project dependency trees proving every occurrence is provided/test.

Usage::

    python3 script/ci/check_dependencies.py [--rules FILE] [--exceptions FILE]
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
import uuid
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ci_tools  # noqa: E402
import dependency_exceptions as exceptions  # noqa: E402
from cilib import Exclusion, is_excluded, list_repository_files, load_exclusions  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RULES = Path(__file__).resolve().parent / "dependency_report_only.txt"
DEFAULT_EXCEPTIONS = Path(__file__).resolve().parent / "dependency_exceptions.json"
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
    purl: str = ""


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
                purl=info.get("purl") if isinstance(info.get("purl"), str) else "",
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


def maven_provenance_command(tree_name: str) -> List[str]:
    """Write one resolved JSON dependency tree per default reactor project."""
    return ["./mvnw", "--batch-mode", "--no-transfer-progress", "--quiet", "-DskipFrontend",
            "org.apache.maven.plugins:maven-dependency-plugin:tree", "-DoutputType=json",
            f"-DoutputFile={tree_name}", "-DappendOutput=false"]


def valid_report(report) -> bool:
    """Require OSV report containers while tolerating additional fields and missing package details."""
    if not isinstance(report, dict) or not isinstance(report.get("results"), list):
        return False
    for result in report["results"]:
        if not isinstance(result, dict):
            return False
        if result.get("source") is not None and not isinstance(result["source"], dict):
            return False
        packages = result.get("packages")
        if packages is not None and not isinstance(packages, list):
            return False
        for package in packages or []:
            if not isinstance(package, dict):
                return False
            if package.get("package") is not None and not isinstance(package["package"], dict):
                return False
            for field in ("vulnerabilities", "groups"):
                values = package.get(field)
                if values is not None and (not isinstance(values, list)
                                           or any(not isinstance(v, dict) for v in values)):
                    return False
    return True


def run_scanner(root: Path, scanner: Path,
                run: Optional[Callable[[List[str], Path], int]] = None) -> Tuple[int, Dict]:
    """Write the Maven SBOM, scan it with the lockfiles and return (exit code, parsed JSON report).

    A failing SBOM build is returned as exit code 2 so it can never pass as "clean".
    """
    runner = run or (lambda command, cwd: subprocess.run(command, cwd=cwd, check=False).returncode)
    sources = lockfiles(list_repository_files(root))
    tree_name = None
    if (root / "pom.xml").is_file():
        if runner(maven_sbom_command(), root) != 0:
            print("error: Maven could not write the SBOM", file=sys.stderr)
            return 2, {}
        sources.insert(0, MAVEN_SBOM)
        tree_name = f"target/security-dependency-tree-{uuid.uuid4().hex}.json"
        if runner(maven_provenance_command(tree_name), root) != 0:
            print("error: Maven could not write dependency provenance", file=sys.stderr)
            return 2, {}
    with tempfile.TemporaryDirectory() as scratch:
        output = Path(scratch) / "osv.json"
        code = runner(scan_command(scanner, sources, output), root)
        if code not in (_EXIT_CLEAN, _EXIT_VULNERABLE):
            return code, {}
        try:
            report = json.loads(output.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            print(f"error: cannot read osv-scanner JSON report: {error}", file=sys.stderr)
            return 2, {}
        if not valid_report(report):
            print("error: osv-scanner did not write an object with a valid results array", file=sys.stderr)
            return 2, {}
    if tree_name is not None:
        # This internal marker comes from the current invocation, never from scanner input.
        # Old target/ files cannot justify an exception when a project was not regenerated.
        report["_dependency_tree_path"] = tree_name
    return code, report


def main(argv: Optional[Sequence[str]] = None, root: Path = DEFAULT_ROOT,
         scan: Optional[Callable[[Path], Tuple[int, Dict]]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rules", type=Path, default=DEFAULT_RULES, help="report-only rule file")
    parser.add_argument("--exceptions", type=Path, default=DEFAULT_EXCEPTIONS,
                        help="exact Maven compatibility exceptions")
    args = parser.parse_args(argv)
    try:
        rules = load_exclusions(args.rules)
        precise = exceptions.load_exceptions(args.exceptions)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    scanner = scan or (lambda r: run_scanner(r, ci_tools.ensure("osv-scanner", r)))
    code, report = scanner(root)
    if code == _EXIT_NO_PACKAGES:
        if (root / "pom.xml").is_file() or lockfiles(list_repository_files(root)):
            print("error: osv-scanner found no packages despite available dependency manifests", file=sys.stderr)
            return 2
        print("dependencies ok (no packages found)")
        return 0
    if code not in (_EXIT_CLEAN, _EXIT_VULNERABLE):
        print(f"error: osv-scanner failed with exit code {code}", file=sys.stderr)
        return 2

    if not valid_report(report):
        print("error: osv-scanner returned an invalid JSON report", file=sys.stderr)
        return 2
    try:
        packages = parse_report(report, root)
    except (AttributeError, TypeError, ValueError) as error:
        print(f"error: cannot parse osv-scanner findings: {error}", file=sys.stderr)
        return 2
    if code == _EXIT_VULNERABLE and not packages:
        print("error: osv-scanner reported vulnerabilities without returning any findings", file=sys.stderr)
        return 2
    # Aggregate Maven findings always require exact exceptions; a manifest glob cannot
    # hide a new runtime dependency, version, project or vulnerability in the same BOM.
    maven = [package for package in packages if package.source == MAVEN_SBOM]
    blocking, reported = split_report_only([package for package in packages if package.source != MAVEN_SBOM], rules)
    candidates = [rule for rule in precise if any(
        package.version == rule.version and rule.vulnerability in package.ids for package in maven
    )]
    provenance = None
    if candidates:
        try:
            provenance = exceptions.load_provenance(root, report.get("_dependency_tree_path"))
        except ValueError as error:
            print(f"error: {error}", file=sys.stderr)
            return 2
    accepted = 0
    for package in maven:
        purl = provenance.purl(package.name, package.version, package.purl) if provenance else ""
        remaining = []
        for vulnerability in package.ids:
            rule = next((rule for rule in candidates if rule.purl == purl and rule.version == package.version
                         and rule.vulnerability == vulnerability and provenance and provenance.permits(rule)), None)
            if rule is None:
                remaining.append(vulnerability)
                continue
            accepted += 1
            projects = ", ".join(f"{project.path} [{'/'.join(sorted(project.scopes))}]" for project in rule.projects)
            print(f"compatibility exception: {package.source}: {purl} {vulnerability} "
                  f"({projects}; expires {rule.expires}): {rule.reason}")
        if remaining or not package.ids:
            blocking.append(replace(package, ids=tuple(remaining), purl=purl or package.purl))
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
    print(f"dependencies ok ({len(reported)} report-only finding(s), {accepted} compatibility exception(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
