# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_versions.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import importlib.util
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parents[1] / "check_versions.py"
_SPEC = importlib.util.spec_from_file_location("check_versions", _SCRIPT)
assert _SPEC is not None and _SPEC.loader is not None
chk = importlib.util.module_from_spec(_SPEC)
sys.modules["check_versions"] = chk
_SPEC.loader.exec_module(chk)

_ROOT_POM = """<project>
    <groupId>org.devlive.grantforge</groupId>
    <artifactId>grantforge</artifactId>
    <version>{version}</version>
    <properties>
        <other.version>1.2.3</other.version>
    </properties>
    <dependencies>
        <dependency><version>2026.9.9</version></dependency>
    </dependencies>
</project>
"""

_CHILD_POM = """<project>
    <parent>
        <artifactId>grantforge</artifactId>
        <version>{version}</version>
    </parent>
    <artifactId>grantforge-core</artifactId>
    <dependencies>
        <dependency><version>2026.9.9</version></dependency>
    </dependencies>
</project>
"""

_FILES = {
    "core/grantforge-web/package.json":
        '{{\n  "name": "web",\n  "version": "{version}",\n  "dependencies": {{"x": "2026.9.9"}}\n}}\n',
    "docs/package.json": '{{\n  "version": "{version}"\n}}\n',
    "sdk/grantforge-js/package.json": '{{\n  "version": "{version}"\n}}\n',
    "deploy/helm/grantforge/Chart.yaml": 'version: 0.1.0\nappVersion: "{version}"\n',
    "core/grantforge-web/src/layouts/AppLayout.vue": '<span>© 2026</span><span class="font-mono">v{version}</span>\n',
    "README.md": "![Version](https://img.shields.io/badge/version-{version}-4F46E5)\n",
    "core/grantforge-web/tests/samples/setup.ts":
        "start('grantforge-sample-shop-{version}.jar')\nstart('grantforge-sample-notes-{version}.jar')\n",
}


class VersionsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        self.write("pom.xml", _ROOT_POM, "2026.0.0")
        self.write("core/grantforge-core/pom.xml", _CHILD_POM, "2026.0.0")
        for path, template in _FILES.items():
            self.write(path, template, "2026.0.0")
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "add", "-A"], cwd=self.root, check=True)
        self.poms = chk.tracked_poms(self.root)

    def write(self, path: str, template: str, version: str) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(template.format(version=version), encoding="utf-8")

    def test_reads_the_root_version_and_finds_every_pom(self) -> None:
        self.assertEqual(chk.root_version(self.root), "2026.0.0")
        self.assertEqual(self.poms, ["core/grantforge-core/pom.xml", "pom.xml"])
        self.assertEqual(chk.find_mismatches(self.root, "2026.0.0", self.poms), [])

    def test_reports_a_copy_that_differs_and_a_carrier_without_one(self) -> None:
        self.write("core/grantforge-core/pom.xml", _CHILD_POM, "2026.1.0")
        (self.root / "README.md").write_text("no badge\n", encoding="utf-8")
        mismatches = chk.find_mismatches(self.root, "2026.0.0", self.poms)
        self.assertEqual(mismatches, [chk.Mismatch("core/grantforge-core/pom.xml", "2026.1.0"),
                                      chk.Mismatch("README.md", "no version found")])

    def test_sets_every_copy_but_leaves_dependencies_alone(self) -> None:
        chk.set_version(self.root, "2026.1.0-rc.1", self.poms)
        self.assertEqual(chk.find_mismatches(self.root, "2026.1.0-rc.1", self.poms), [])
        self.assertIn("<version>2026.9.9</version>", (self.root / "pom.xml").read_text(encoding="utf-8"))
        self.assertIn('"x": "2026.9.9"', (self.root / "core/grantforge-web/package.json").read_text(encoding="utf-8"))
        self.assertIn("version: 0.1.0", (self.root / "deploy/helm/grantforge/Chart.yaml").read_text(encoding="utf-8"))

    def test_refuses_a_version_of_another_form(self) -> None:
        for version in ("2026.1", "v2026.1.0", "2026.1.0-SNAPSHOT", "26.1.0"):
            with self.assertRaises(ValueError):
                chk.set_version(self.root, version, self.poms)

    def test_a_release_tag_is_v_and_the_version(self) -> None:
        self.assertIsNone(chk.check_tag("v2026.0.0", "2026.0.0"))
        self.assertIn("expected v2026.0.0", chk.check_tag("2026.0.0", "2026.0.0") or "")
        self.assertIsNotNone(chk.check_tag("v2026.0.1", "2026.0.0"))

    def test_main_checks_shows_and_sets(self) -> None:
        self.assertEqual(chk.main(["--root", str(self.root)]), 0)
        self.assertEqual(chk.main(["--root", str(self.root), "--tag", "v2026.0.1"]), 1)
        self.assertEqual(chk.main(["--root", str(self.root), "--set", "bad"]), 2)
        self.assertEqual(chk.main(["--root", str(self.root), "--set", "2026.2.0"]), 0)
        self.assertEqual(chk.root_version(self.root), "2026.2.0")


if __name__ == "__main__":
    unittest.main()
