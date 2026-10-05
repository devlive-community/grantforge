# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/lint_scripts.py (stdlib unittest; linters are faked)."""

from __future__ import annotations

import io
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from typing import List, Sequence
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import lint_scripts  # noqa: E402

PATHS = [
    "script/bin/startup.sh",
    "configure/docker/entrypoint.sh",
    "script/ci/cilib.py",
    "docs/tool.py",
    ".github/workflows/ci.yml",
    ".github/workflows/docs.yaml",
    ".github/ISSUE_TEMPLATE/bug.md",
    "deploy/helm/grantforge/Chart.yaml",
    "deploy/helm/grantforge/templates/service.yaml",
    "mvnw",
]


def fake_find(name: str) -> Path:
    return Path(f"/tools/{name}")


class FilterTest(unittest.TestCase):
    def test_selects_files_per_linter(self) -> None:
        self.assertEqual(lint_scripts.shell_files(PATHS), ["script/bin/startup.sh", "configure/docker/entrypoint.sh"])
        self.assertEqual(lint_scripts.python_files(PATHS), ["script/ci/cilib.py"])
        self.assertEqual(lint_scripts.workflow_files(PATHS),
                         [".github/workflows/ci.yml", ".github/workflows/docs.yaml"])
        self.assertEqual(lint_scripts.chart_dirs(PATHS), ["deploy/helm/grantforge"])


class BuildCommandsTest(unittest.TestCase):
    def test_commands_include_pinned_tools_and_files(self) -> None:
        commands = lint_scripts.build_commands(PATHS, fake_find)
        self.assertEqual(commands["shell"][:2], ["/tools/shellcheck", "-x"])
        self.assertEqual(commands["python"][:4], ["/tools/ruff", "check", "--config", str(lint_scripts.RUFF_CONFIG)])
        self.assertEqual(commands["workflows"][:2], ["/tools/actionlint", "-shellcheck=/tools/shellcheck"])
        self.assertEqual(commands["charts"], ["/tools/helm", "lint", "--strict", "deploy/helm/grantforge"])

    def test_no_files_means_no_command(self) -> None:
        commands = lint_scripts.build_commands(["README.md"], fake_find)
        self.assertEqual(commands, {"shell": None, "python": None, "workflows": None, "charts": None})


class MainTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        for name in ("a.sh", "script/x.py"):
            (self.root / name).parent.mkdir(parents=True, exist_ok=True)
            (self.root / name).write_text("x\n", encoding="utf-8")
        self.calls: List[List[str]] = []

    def _runner(self, code: int):
        def run(command: Sequence[str], cwd: Path) -> int:
            self.calls.append(list(command))
            return code
        return run

    def _main(self, args: Sequence[str], code: int = 0) -> int:
        with mock.patch("sys.stdout", new_callable=io.StringIO), mock.patch("sys.stderr", new_callable=io.StringIO):
            return lint_scripts.main(list(args), self.root, self._runner(code), fake_find)

    def test_runs_requested_linters_with_files(self) -> None:
        self.assertEqual(self._main([]), 0)
        self.assertEqual([c[0] for c in self.calls], ["/tools/shellcheck", "/tools/ruff"])  # no workflows present

    def test_failure_is_reported(self) -> None:
        self.assertEqual(self._main(["shell"], code=1), 1)

    def test_unknown_linter(self) -> None:
        self.assertEqual(self._main(["java"]), 2)
        self.assertEqual(self.calls, [])


if __name__ == "__main__":
    unittest.main()
