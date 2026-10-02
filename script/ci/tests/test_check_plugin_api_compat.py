# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_plugin_api_compat.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import io
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_plugin_api_compat as chk  # noqa: E402

SOURCE = "public static final ApiVersion CURRENT = new ApiVersion(2, 1, 0);"


class LatestReleaseTest(unittest.TestCase):
    def test_picks_the_highest_release_tag(self) -> None:
        self.assertEqual(chk.latest_release(["v1.9.0", "v1.10.0", "v1.2.3", "legacy-2026.0.0", "1.0.6"]), "v1.10.0")

    def test_ignores_other_tags(self) -> None:
        self.assertIsNone(chk.latest_release(["1.0.5", "legacy-2026.0.0", "v1.0", ""]))


class ApiMajorTest(unittest.TestCase):
    def test_reads_the_major_version(self) -> None:
        self.assertEqual(chk.api_major(SOURCE), 2)

    def test_refuses_sources_without_the_declaration(self) -> None:
        with self.assertRaises(ValueError):
            chk.api_major("class ApiVersion {}")


class VerdictTest(unittest.TestCase):
    def test_compatible_changes_pass(self) -> None:
        self.assertTrue(chk.verdict(True, 1, 1)[0])
        self.assertTrue(chk.verdict(True, 1, 2)[0])

    def test_breaks_need_a_new_major_version(self) -> None:
        passes, message = chk.verdict(False, 1, 1)
        self.assertFalse(passes)
        self.assertIn("raise the major part", message)
        self.assertTrue(chk.verdict(False, 1, 2)[0])

    def test_the_major_version_never_goes_back(self) -> None:
        self.assertFalse(chk.verdict(True, 2, 1)[0])


class MainTest(unittest.TestCase):
    def test_passes_before_the_first_release(self) -> None:
        with mock.patch.object(chk, "_git", return_value="1.0.5\nlegacy-2026.0.0\n"), \
                mock.patch("sys.stdout", new_callable=io.StringIO) as out:
            self.assertEqual(chk.main(["--root", "."]), 0)
        self.assertIn("no release tag yet", out.getvalue())

    def test_reports_the_comparison(self) -> None:
        with mock.patch.object(chk, "compare", return_value=(False, "broken")) as compare, \
                mock.patch("sys.stderr", new_callable=io.StringIO) as err:
            self.assertEqual(chk.main(["--base", "main", "--root", "."]), 1)
        compare.assert_called_once()
        self.assertEqual(compare.call_args.args[1], "main")
        self.assertIn("broken", err.getvalue())
        with mock.patch.object(chk, "compare", return_value=(True, "fine")), \
                mock.patch.object(chk, "_git", return_value="v1.0.0\n"), \
                mock.patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(chk.main(["--root", "."]), 0)

    def test_a_base_without_the_plugin_api_passes(self) -> None:
        with mock.patch.object(chk, "_git", side_effect=RuntimeError("no such path")):
            passes, message = chk.compare(Path("."), "v0.1.0")
        self.assertTrue(passes)
        self.assertEqual(message, "v0.1.0 has no plugin API; nothing to compare with")

    def test_git_failures_name_the_command(self) -> None:
        failed = subprocess.CompletedProcess(["git"], 128, "", "fatal: bad revision")
        with mock.patch.object(chk, "_run", return_value=failed):
            with self.assertRaisesRegex(RuntimeError, "git show x failed: fatal: bad revision"):
                chk._git(Path("."), "show", "x")


if __name__ == "__main__":
    unittest.main()
