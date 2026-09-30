#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Enforce line and branch coverage for every Java module from its JaCoCo report.

Each module declared in the root pom that has ``src/main/java`` sources must have
``target/site/jacoco/jacoco.xml`` (written during ``mvn test``) and meet its
thresholds. Defaults are 80% lines and 70% branches; ``coverage_thresholds.txt``
may raise them per module (``<module glob> line=<pct> branch=<pct>  # reason``).
Thresholds only ever go up: a rule below the defaults is rejected.

Usage::

    python3 script/ci/check_coverage.py [--root DIR] [--thresholds FILE]
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple
from xml.etree import ElementTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cilib import glob_to_regex  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_THRESHOLDS = Path(__file__).resolve().parent / "coverage_thresholds.txt"
DEFAULT_LINE = 80.0
DEFAULT_BRANCH = 70.0
REPORT = Path("target/site/jacoco/jacoco.xml")

_POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
_RULE = re.compile(r"^(?P<glob>\S+)\s+line=(?P<line>\d+(?:\.\d+)?)\s+branch=(?P<branch>\d+(?:\.\d+)?)$")


@dataclass(frozen=True)
class Threshold:
    """Minimum line and branch coverage in percent."""

    line: float
    branch: float


@dataclass(frozen=True)
class Rule:
    glob: str
    threshold: Threshold
    reason: str


@dataclass(frozen=True)
class Coverage:
    """Covered/total counts for lines and branches of one module."""

    lines: Tuple[int, int]
    branches: Tuple[int, int]

    @staticmethod
    def percent(counts: Tuple[int, int]) -> float:
        covered, total = counts
        # A module without any branch (or line) has nothing left uncovered.
        return 100.0 if total == 0 else covered * 100.0 / total


def load_rules(path: Path) -> List[Rule]:
    """Parse the thresholds file; a missing file means only the defaults apply.

    Raises ``ValueError`` for malformed lines, missing reasons and thresholds below
    the defaults (the gate may be tightened per module, never loosened).
    """
    if not path.is_file():
        return []
    rules: List[Rule] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        body, _, reason = line.partition("#")
        match = _RULE.match(body.strip())
        if match is None or not reason.strip():
            raise ValueError(f"{path}:{number}: expected '<module glob> line=<pct> branch=<pct>  # reason'")
        threshold = Threshold(float(match.group("line")), float(match.group("branch")))
        if threshold.line < DEFAULT_LINE or threshold.branch < DEFAULT_BRANCH:
            raise ValueError(f"{path}:{number}: thresholds may not be lower than the defaults "
                             f"(line {DEFAULT_LINE:g}, branch {DEFAULT_BRANCH:g})")
        rules.append(Rule(match.group("glob"), threshold, reason.strip()))
    return rules


def threshold_for(module: str, rules: Sequence[Rule]) -> Threshold:
    """Return the first matching rule's threshold, or the defaults."""
    for rule in rules:
        if glob_to_regex(rule.glob).fullmatch(module):
            return rule.threshold
    return Threshold(DEFAULT_LINE, DEFAULT_BRANCH)


def read_report(path: Path) -> Coverage:
    """Read the report-level LINE and BRANCH counters of a JaCoCo XML report."""
    root = ElementTree.parse(path).getroot()
    counters: Dict[str, Tuple[int, int]] = {}
    # Only direct children of <report> hold the module totals; nested counters are per package/class.
    for counter in root.findall("counter"):
        missed = int(counter.get("missed", "0"))
        covered = int(counter.get("covered", "0"))
        counters[counter.get("type", "")] = (covered, covered + missed)
    return Coverage(counters.get("LINE", (0, 0)), counters.get("BRANCH", (0, 0)))


def java_modules(root: Path) -> List[str]:
    """Return root-pom modules (relative POSIX paths) that contain main Java sources."""
    pom = ElementTree.parse(root / "pom.xml").getroot()
    modules = [(m.text or "").strip() for m in pom.findall("m:modules/m:module", _POM_NS)]
    return [m for m in modules if m and any((root / m / "src/main/java").rglob("*.java"))]


def check(root: Path, rules: Sequence[Rule]) -> Tuple[List[str], List[str]]:
    """Return (summary lines, errors) for every Java module."""
    summary: List[str] = []
    errors: List[str] = []
    for module in java_modules(root):
        report = root / module / REPORT
        if not report.is_file():
            errors.append(f"{module}: missing {REPORT}; run the unit tests first (bash script/ci/java.sh test)")
            continue
        coverage = read_report(report)
        need = threshold_for(module, rules)
        line = Coverage.percent(coverage.lines)
        branch = Coverage.percent(coverage.branches)
        summary.append(f"{module}: line {line:.1f}% (min {need.line:g}%), branch {branch:.1f}% (min {need.branch:g}%)")
        if line < need.line:
            errors.append(f"{module}: line coverage {line:.1f}% is below {need.line:g}% "
                          f"({coverage.lines[0]}/{coverage.lines[1]} lines)")
        if branch < need.branch:
            errors.append(f"{module}: branch coverage {branch:.1f}% is below {need.branch:g}% "
                          f"({coverage.branches[0]}/{coverage.branches[1]} branches)")
    return summary, errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    parser.add_argument("--thresholds", type=Path, default=DEFAULT_THRESHOLDS, help="per-module threshold rules")
    args = parser.parse_args(argv)

    try:
        rules = load_rules(args.thresholds)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    summary, errors = check(args.root.resolve(), rules)
    for line in summary:
        print(line)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("coverage ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
