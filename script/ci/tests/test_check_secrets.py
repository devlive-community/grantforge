# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_secrets.py (stdlib unittest; gitleaks is faked)."""

from __future__ import annotations

import io
import sys
import unittest
from pathlib import Path
from typing import List
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_secrets as chk  # noqa: E402


class BuildCommandTest(unittest.TestCase):
    def test_history_scan_is_redacted(self) -> None:
        self.assertEqual(chk.build_command(Path("/t/gitleaks"), worktree=False),
                         ["/t/gitleaks", "git", ".", "--redact", "--no-banner", "--exit-code", "1"])

    def test_worktree_scan(self) -> None:
        self.assertEqual(chk.build_command(Path("/t/gitleaks"), worktree=True)[1:3], ["dir", "."])


class MainTest(unittest.TestCase):
    def _main(self, code: int, *args: str) -> int:
        self.commands: List[List[str]] = []

        def run(command: List[str], cwd: Path) -> int:
            self.commands.append(command)
            return code

        with mock.patch("sys.stdout", new_callable=io.StringIO), mock.patch("sys.stderr", new_callable=io.StringIO):
            return chk.main(list(args), Path("/repo"), run, lambda name: Path(f"/t/{name}"))

    def test_exit_codes(self) -> None:
        self.assertEqual(self._main(0), 0)
        self.assertEqual(self._main(1), 1)
        self.assertEqual(self._main(126), 2)

    def test_worktree_flag_is_passed(self) -> None:
        self._main(0, "--worktree")
        self.assertEqual(self.commands[0][1], "dir")


if __name__ == "__main__":
    unittest.main()
