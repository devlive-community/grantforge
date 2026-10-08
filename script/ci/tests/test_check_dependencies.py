# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_dependencies.py (stdlib unittest; the scanner is faked)."""

from __future__ import annotations

import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
import uuid
from pathlib import Path
from typing import Dict, List, Tuple
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


class CommandsTest(unittest.TestCase):
    def test_lockfiles_are_selected_by_name(self) -> None:
        paths = ["core/web/pnpm-lock.yaml", "docs/requirements.txt", "a/requirements-dev.txt", "x/package-lock.json",
                 "y/yarn.lock", "pom.xml", "README.md", "docs/requirements.txt.bak"]
        self.assertEqual(chk.lockfiles(paths), ["a/requirements-dev.txt", "core/web/pnpm-lock.yaml",
                                                "docs/requirements.txt", "x/package-lock.json", "y/yarn.lock"])

    def test_maven_sbom_command_skips_frontend(self) -> None:
        command = chk.maven_sbom_command()
        self.assertEqual(command[0], "./mvnw")
        self.assertIn("-DskipFrontend", command)
        self.assertEqual(command[-1], "org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom")

    def test_scan_command_lists_every_source(self) -> None:
        command = chk.scan_command(Path("/t/osv"), ["target/bom.json", "web/pnpm-lock.yaml"], Path("/o.json"))
        self.assertEqual(command[:3], ["/t/osv", "scan", "source"])
        self.assertEqual(command[-4:], ["--lockfile", "target/bom.json", "--lockfile", "web/pnpm-lock.yaml"])

    def test_provenance_command_uses_a_per_project_json_file(self) -> None:
        name = f"target/security-dependency-tree-{uuid.uuid4().hex}.json"
        command = chk.maven_provenance_command(name)
        self.assertIn("org.apache.maven.plugins:maven-dependency-plugin:tree", command)
        self.assertIn("-DoutputType=json", command)
        self.assertIn(f"-DoutputFile={name}", command)
        self.assertIn("-DappendOutput=false", command)


class RunScannerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        (self.root / "web").mkdir()
        (self.root / "web/pnpm-lock.yaml").write_text("lockfileVersion: '6.0'\n", encoding="utf-8")
        self.calls: List[List[str]] = []

    def _runner(self, maven_code: int, scan_code: int, report: Dict):
        def run(command: List[str], cwd: Path) -> int:
            self.calls.append(command)
            if command[0] == "./mvnw":
                return maven_code
            Path(command[command.index("--output-file") + 1]).write_text(json.dumps(report), encoding="utf-8")
            return scan_code
        return run

    def test_scans_maven_sbom_first_then_lockfiles(self) -> None:
        (self.root / "pom.xml").write_text("<project/>", encoding="utf-8")
        code, report = chk.run_scanner(self.root, Path("/t/osv"), self._runner(0, 0, {"results": []}))
        self.assertEqual(code, 0)
        self.assertEqual(report["results"], [])
        self.assertRegex(report["_dependency_tree_path"], r"^target/security-dependency-tree-[0-9a-f]{32}\.json$")
        self.assertIn(f"-DoutputFile={report['_dependency_tree_path']}", self.calls[1])
        scan = self.calls[2]
        self.assertEqual(scan[scan.index("--lockfile") + 1], chk.MAVEN_SBOM)
        self.assertEqual(scan[-1], "web/pnpm-lock.yaml")

    def test_failed_sbom_is_an_error_not_clean(self) -> None:
        (self.root / "pom.xml").write_text("<project/>", encoding="utf-8")
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(chk.run_scanner(self.root, Path("/t/osv"), self._runner(1, 0, {})), (2, {}))
        self.assertEqual(len(self.calls), 1)

    def test_repository_without_pom_skips_maven(self) -> None:
        chk.run_scanner(self.root, Path("/t/osv"), self._runner(0, 0, {"results": []}))
        self.assertEqual([c[0] for c in self.calls], ["/t/osv"])

    def test_provenance_failure_is_not_clean(self) -> None:
        (self.root / "pom.xml").write_text("<project/>", encoding="utf-8")
        calls = []

        def runner(command, cwd):
            calls.append(command)
            return 1 if "org.apache.maven.plugins:maven-dependency-plugin:tree" in command else 0

        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(chk.run_scanner(self.root, Path("/t/osv"), runner), (2, {}))
        self.assertEqual(len(calls), 2)

    def test_each_scan_uses_a_new_tree_name_and_overwrites_scanner_input(self) -> None:
        (self.root / "pom.xml").write_text("<project/>", encoding="utf-8")
        first = chk.run_scanner(self.root, Path("/t/osv"), self._runner(
            0, 0, {"results": [], "_dependency_tree_path": "old"}))[1]
        second = chk.run_scanner(self.root, Path("/t/osv"), self._runner(0, 0, {"results": []}))[1]
        self.assertNotEqual(first["_dependency_tree_path"], "old")
        self.assertNotEqual(first["_dependency_tree_path"], second["_dependency_tree_path"])

    def test_missing_report_is_an_error_for_clean_or_vulnerable_status(self) -> None:
        for code in (0, 1):
            with self.subTest(code=code), mock.patch("sys.stderr", new_callable=io.StringIO):
                self.assertEqual(chk.run_scanner(self.root, Path("/t/osv"), lambda command, cwd, status=code: status),
                                 (2, {}))

    def test_invalid_json_and_invalid_report_shapes_are_errors(self) -> None:
        for code in (0, 1):
            for content in ("not JSON", "[]", "{}", '{"results":null}', '{"results":{}}',
                            '{"results":[null]}', '{"results":[{"packages":"invalid"}]}'):
                def runner(command, cwd, payload=content, status=code):
                    Path(command[command.index("--output-file") + 1]).write_text(payload, encoding="utf-8")
                    return status

                with self.subTest(code=code, content=content), mock.patch("sys.stderr", new_callable=io.StringIO):
                    self.assertEqual(chk.run_scanner(self.root, Path("/t/osv"), runner), (2, {}))

    def test_valid_clean_report_and_vulnerability_report_keep_scanner_status(self) -> None:
        self.assertEqual(chk.run_scanner(self.root, Path("/t/osv"), self._runner(0, 0, {"results": []})),
                         (0, {"results": []}))
        findings = report(("/repo/pnpm-lock.yaml", "package", "1", ("GHSA-1",), "9.8"))
        self.assertEqual(chk.run_scanner(self.root, Path("/t/osv"), self._runner(0, 1, findings)), (1, findings))


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
        handle = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
        handle.write('{"schema_version": 1, "exceptions": []}')
        handle.close()
        self.exceptions = Path(handle.name)
        self.addCleanup(self.exceptions.unlink)

    def _main(self, code: int, data: Dict, rules: str = "") -> int:
        self.rules.write_text(rules, encoding="utf-8")
        with mock.patch("sys.stdout", new_callable=io.StringIO), mock.patch("sys.stderr", new_callable=io.StringIO):
            return chk.main(["--rules", str(self.rules), "--exceptions", str(self.exceptions)],
                            ROOT, lambda root: (code, data))

    def test_clean(self) -> None:
        self.assertEqual(self._main(0, {"results": []}), 0)

    def test_no_packages_is_allowed_only_without_dependency_manifests(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "--quiet", str(root)], check=True)
            for manifest in (None, "pom.xml", "pnpm-lock.yaml"):
                if manifest:
                    (root / manifest).write_text("", encoding="utf-8")
                with self.subTest(manifest=manifest), mock.patch("sys.stdout", new_callable=io.StringIO), \
                        mock.patch("sys.stderr", new_callable=io.StringIO):
                    code = chk.main(["--rules", str(self.rules), "--exceptions", str(self.exceptions)],
                                    root, lambda path: (128, {}))
                    self.assertEqual(code, 2 if manifest else 0)
                if manifest:
                    (root / manifest).unlink()

    def test_empty_vulnerability_report_is_an_error(self) -> None:
        for data in ({}, {"results": []}, {"results": [{"packages": []}]}):
            with self.subTest(data=data):
                self.assertEqual(self._main(1, data), 2)

    def test_invalid_clean_report_is_not_treated_as_success(self) -> None:
        for data in ({}, {"results": None}, {"results": {}}):
            with self.subTest(data=data):
                self.assertEqual(self._main(0, data), 2)

    def test_vulnerable_fails_unless_report_only(self) -> None:
        data = report(("/repo/core/legacy/pom.xml", "g:a", "1", ("X",), "9.8"))
        self.assertEqual(self._main(1, data), 1)
        self.assertEqual(self._main(1, data, "core/legacy/**  # replaced in M1-01\n"), 0)

    def test_rule_without_reason_is_a_config_error(self) -> None:
        self.assertEqual(self._main(0, {}, "core/**\n"), 2)

    def test_scanner_error(self) -> None:
        self.assertEqual(self._main(127, {}), 2)

    def test_report_only_glob_cannot_hide_maven_findings(self) -> None:
        data = report(("/repo/target/bom.json", "g:a", "1", ("X",), "9.8"))
        self.assertEqual(self._main(1, data, "target/**  # compatibility build\n"), 1)


if __name__ == "__main__":
    unittest.main()
