# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_dependencies.py (stdlib unittest; the scanner is faked)."""

from __future__ import annotations

import io
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Dict, Tuple
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_dependencies as chk  # noqa: E402
import cilib  # noqa: E402

ROOT = Path("/repo")


def report(*results: Tuple[str, str, str, Tuple[str, ...], str]) -> Dict:
    """Build a minimal OSV report from (path, name, version, ids, max_severity) tuples."""
    return {"results": [
        {
            "source": {"path": path, "type": "lockfile"},
            "packages": [{
                "package": {"name": name, "version": version, "ecosystem": "Maven"},
                "vulnerabilities": [{"id": i} for i in ids],
                "groups": [{"ids": list(ids), "max_severity": severity}],
            }],
        }
        for path, name, version, ids, severity in results
    ]}


class ParseReportTest(unittest.TestCase):
    def test_flattens_and_relativises_paths(self) -> None:
        packages = chk.parse_report(report(("/repo/core/a/pom.xml", "g:a", "1.0", ("GHSA-1", "CVE-2"), "7.5")), ROOT)
        self.assertEqual(packages, [chk.VulnerablePackage("core/a/pom.xml", "g:a", "1.0", ("GHSA-1", "CVE-2"), "7.5")])

    def test_picks_highest_numeric_severity(self) -> None:
        data = report(("/repo/pom.xml", "g:a", "1", ("A",), "5.0"))
        data["results"][0]["packages"][0]["groups"].append({"max_severity": "9.8"})
        data["results"][0]["packages"][0]["groups"].append({"max_severity": ""})
        self.assertEqual(chk.parse_report(data, ROOT)[0].max_severity, "9.8")

    def test_tolerates_missing_keys(self) -> None:
        self.assertEqual(chk.parse_report({}, ROOT), [])
        self.assertEqual(chk.parse_report({"results": None}, ROOT), [])
        packages = chk.parse_report({"results": [{"packages": [{}]}]}, ROOT)
        self.assertEqual(packages, [chk.VulnerablePackage("?", "?", "?", (), "unknown")])


class SplitTest(unittest.TestCase):
    def test_split_by_prefix(self) -> None:
        a = chk.VulnerablePackage("core/grantforge-common/pom.xml", "a", "1", (), "1")
        b = chk.VulnerablePackage("core/grantforge-web/pnpm-lock.yaml", "b", "1", (), "1")
        pattern = "core/grantforge-common/**"
        rules = [cilib.Exclusion(pattern, "legacy", cilib.glob_to_regex(pattern))]
        blocking, reported = chk.split_report_only([a, b], rules)
        self.assertEqual((blocking, reported), ([b], [a]))


class MainTest(unittest.TestCase):
    def setUp(self) -> None:
        handle = tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8")
        handle.close()
        self.rules = Path(handle.name)
        self.addCleanup(self.rules.unlink)

    def _main(self, code: int, data: Dict, rules: str = "") -> int:
        self.rules.write_text(rules, encoding="utf-8")
        with mock.patch("sys.stdout", new_callable=io.StringIO), mock.patch("sys.stderr", new_callable=io.StringIO):
            return chk.main(["--rules", str(self.rules)], ROOT, lambda root: (code, data))

    def test_clean(self) -> None:
        self.assertEqual(self._main(0, {"results": []}), 0)

    def test_no_packages(self) -> None:
        self.assertEqual(self._main(128, {}), 0)

    def test_vulnerable_fails_unless_report_only(self) -> None:
        data = report(("/repo/core/legacy/pom.xml", "g:a", "1", ("X",), "9.8"))
        self.assertEqual(self._main(1, data), 1)
        self.assertEqual(self._main(1, data, "core/legacy/**  # replaced in M1-01\n"), 0)

    def test_rule_without_reason_is_a_config_error(self) -> None:
        self.assertEqual(self._main(0, {}, "core/**\n"), 2)

    def test_scanner_error(self) -> None:
        self.assertEqual(self._main(127, {}), 2)


if __name__ == "__main__":
    unittest.main()
