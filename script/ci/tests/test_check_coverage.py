# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_coverage.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import io
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_coverage as chk  # noqa: E402

POM = '<project xmlns="http://maven.apache.org/POM/4.0.0"><modules>{}</modules></project>'


def report_xml(line: tuple, branch: tuple = None) -> str:
    """Build a JaCoCo report with (missed, covered) counters plus a nested package counter."""
    counters = f'<counter type="LINE" missed="{line[0]}" covered="{line[1]}"/>'
    if branch is not None:
        counters += f'<counter type="BRANCH" missed="{branch[0]}" covered="{branch[1]}"/>'
    nested = '<package name="x"><counter type="LINE" missed="999" covered="0"/></package>'
    return f'<report name="m">{nested}{counters}</report>'


class RulesTest(unittest.TestCase):
    def _write(self, content: str) -> Path:
        handle = tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8")
        handle.write(content)
        handle.close()
        self.addCleanup(Path(handle.name).unlink)
        return Path(handle.name)

    def test_parses_rules_and_matches_first(self) -> None:
        rules = chk.load_rules(self._write("# c\ncore/grantforge-authz line=90 branch=85  # core engine\n"))
        self.assertEqual(chk.threshold_for("core/grantforge-authz", rules), chk.Threshold(90, 85))
        self.assertEqual(chk.threshold_for("core/grantforge-server", rules), chk.Threshold(80, 70))

    def test_rejects_missing_reason_malformed_and_lowered(self) -> None:
        for content in ("core/a line=90 branch=85\n", "core/a line=ninety branch=85  # x\n",
                        "core/a line=50 branch=85  # too low\n", "core/a line=90 branch=60  # too low\n"):
            with self.assertRaises(ValueError, msg=content):
                chk.load_rules(self._write(content))

    def test_missing_file(self) -> None:
        self.assertEqual(chk.load_rules(Path("/nonexistent/rules.txt")), [])


class ReportTest(unittest.TestCase):
    def test_reads_only_report_level_counters(self) -> None:
        with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False, encoding="utf-8") as handle:
            handle.write(report_xml((1, 9), (2, 8)))
        self.addCleanup(Path(handle.name).unlink)
        coverage = chk.read_report(Path(handle.name))
        self.assertEqual(coverage, chk.Coverage((9, 10), (8, 10)))

    def test_percent_of_empty_counter_is_full(self) -> None:
        self.assertEqual(chk.Coverage.percent((0, 0)), 100.0)
        self.assertEqual(chk.Coverage.percent((1, 4)), 25.0)


class CheckTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        (self.root / "pom.xml").write_text(POM.format("<module>core/a</module><module>core/docs</module>"),
                                          encoding="utf-8")
        source = self.root / "core/a/src/main/java/A.java"
        source.parent.mkdir(parents=True)
        source.write_text("class A {}\n", encoding="utf-8")
        (self.root / "core/docs").mkdir(parents=True)  # module without Java sources is ignored

    def _report(self, content: str) -> None:
        path = self.root / "core/a" / chk.REPORT
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_passes_above_thresholds(self) -> None:
        self._report(report_xml((1, 9), (1, 9)))
        summary, errors = chk.check(self.root, [])
        self.assertEqual(errors, [])
        self.assertEqual(len(summary), 1)

    def test_module_without_branches_passes_branch_gate(self) -> None:
        self._report(report_xml((0, 3)))
        self.assertEqual(chk.check(self.root, [])[1], [])

    def test_fails_below_thresholds(self) -> None:
        self._report(report_xml((5, 5), (5, 5)))
        errors = chk.check(self.root, [])[1]
        self.assertEqual(len(errors), 2)
        self.assertIn("line coverage 50.0% is below 80%", errors[0])

    def test_missing_report_fails(self) -> None:
        self.assertIn("missing", chk.check(self.root, [])[1][0])

    def test_main_exit_codes(self) -> None:
        rules = self.root / "rules.txt"
        rules.write_text("", encoding="utf-8")
        args = ["--root", str(self.root), "--thresholds", str(rules)]
        with mock.patch("sys.stdout", new_callable=io.StringIO), mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(chk.main(args), 1)
            self._report(report_xml((0, 10), (0, 2)))
            self.assertEqual(chk.main(args), 0)
            rules.write_text("core/a line=10 branch=10  # lowered\n", encoding="utf-8")
            self.assertEqual(chk.main(args), 2)


if __name__ == "__main__":
    unittest.main()
