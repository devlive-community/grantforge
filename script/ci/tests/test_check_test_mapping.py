# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_test_mapping.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import importlib.util
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parents[1] / "check_test_mapping.py"
_SPEC = importlib.util.spec_from_file_location("check_test_mapping", _SCRIPT)
assert _SPEC is not None and _SPEC.loader is not None
chk = importlib.util.module_from_spec(_SPEC)
sys.modules["check_test_mapping"] = chk
_SPEC.loader.exec_module(chk)

WEB = "core/grantforge-web/src"


class ExpectedTestsTest(unittest.TestCase):
    def test_java_main_class_maps_to_test_or_it(self) -> None:
        self.assertEqual(
            chk.expected_tests("core/authz/src/main/java/org/x/Engine.java"),
            ("core/authz/src/test/java/org/x/EngineTest.java", "core/authz/src/test/java/org/x/EngineIT.java"),
        )

    def test_java_package_and_module_info_are_skipped(self) -> None:
        self.assertIsNone(chk.expected_tests("core/a/src/main/java/org/x/package-info.java"))
        self.assertIsNone(chk.expected_tests("core/a/src/main/java/module-info.java"))

    def test_java_test_sources_and_resources_are_not_mapped(self) -> None:
        self.assertIsNone(chk.expected_tests("core/a/src/test/java/org/x/EngineTest.java"))
        self.assertIsNone(chk.expected_tests("core/a/src/main/resources/app.properties"))

    def test_web_ts_and_vue_map_to_sibling_test(self) -> None:
        self.assertEqual(chk.expected_tests(f"{WEB}/lib/api.ts"), (f"{WEB}/lib/api.test.ts",))
        self.assertEqual(chk.expected_tests(f"{WEB}/components/UiSelect.vue"),
                         (f"{WEB}/components/UiSelect.test.ts",))

    def test_web_tests_and_declarations_are_not_mapped(self) -> None:
        self.assertIsNone(chk.expected_tests(f"{WEB}/lib/api.test.ts"))
        self.assertIsNone(chk.expected_tests(f"{WEB}/lib/api.spec.ts"))
        self.assertIsNone(chk.expected_tests(f"{WEB}/env.d.ts"))
        self.assertIsNone(chk.expected_tests(f"{WEB}/assets/main.css"))
        self.assertIsNone(chk.expected_tests("core/grantforge-web/vite.config.ts"))

    def test_ci_scripts_map_to_tests_folder(self) -> None:
        self.assertEqual(chk.expected_tests("script/ci/cilib.py"), ("script/ci/tests/test_cilib.py",))
        self.assertIsNone(chk.expected_tests("script/ci/tests/test_cilib.py"))
        self.assertIsNone(chk.expected_tests("script/bin/startup.sh"))


class FindMissingTest(unittest.TestCase):
    def test_reports_only_sources_without_any_expected_test(self) -> None:
        paths = [
            "m/src/main/java/A.java", "m/src/test/java/ATest.java",
            "m/src/main/java/B.java", "m/src/test/java/BIT.java",
            "m/src/main/java/C.java",
            f"{WEB}/Ok.vue", f"{WEB}/Ok.test.ts",
            f"{WEB}/Missing.ts",
        ]
        missing = chk.find_missing(paths)
        self.assertEqual([m.source for m in missing], ["core/grantforge-web/src/Missing.ts", "m/src/main/java/C.java"])

    def test_excluded_sources_are_skipped(self) -> None:
        missing = chk.find_missing([f"{WEB}/main.ts"], lambda p: p.endswith("main.ts"))
        self.assertEqual(missing, [])

    def test_split_report_only(self) -> None:
        items = [chk.Missing("core/a.ts", ("x",)), chk.Missing("script/ci/b.py", ("y",))]
        blocking, reported = chk.split_report_only(items, ["core/"])
        self.assertEqual([m.source for m in blocking], ["script/ci/b.py"])
        self.assertEqual([m.source for m in reported], ["core/a.ts"])


class MainTest(unittest.TestCase):
    """End-to-end behaviour against a temporary git repository."""

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        self.exclusions = self.root / "exclusions.txt"
        self.exclusions.write_text("", encoding="utf-8")

    def _write(self, name: str) -> None:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("x\n", encoding="utf-8")

    def _main(self, *extra: str) -> int:
        return chk.main(["--root", str(self.root), "--exclusions", str(self.exclusions), *extra])

    def test_missing_test_fails_then_passes(self) -> None:
        self._write("script/ci/tool.py")
        self.assertEqual(self._main(), 1)
        self._write("script/ci/tests/test_tool.py")
        self.assertEqual(self._main(), 0)

    def test_report_only_prefix_does_not_fail(self) -> None:
        self._write("core/m/src/main/java/A.java")
        self.assertEqual(self._main("--report-only", "core/"), 0)
        self.assertEqual(self._main(), 1)

    def test_exclusion_with_reason(self) -> None:
        self._write(f"{WEB}/main.ts")
        self.exclusions.write_text("core/grantforge-web/src/main.ts  # bootstrap only\n", encoding="utf-8")
        self.assertEqual(self._main(), 0)

    def test_exclusion_without_reason_is_a_config_error(self) -> None:
        self.exclusions.write_text("core/**\n", encoding="utf-8")
        self.assertEqual(self._main(), 2)


if __name__ == "__main__":
    unittest.main()
