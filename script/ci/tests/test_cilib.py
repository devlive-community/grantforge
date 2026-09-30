# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/cilib.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import cilib  # noqa: E402


class GlobTest(unittest.TestCase):
    def test_double_star_matches_zero_or_more_directories(self) -> None:
        regex = cilib.glob_to_regex("**/*.png")
        self.assertTrue(regex.fullmatch("logo.png"))
        self.assertTrue(regex.fullmatch("a/b/logo.png"))
        self.assertFalse(regex.fullmatch("a/logo.png.txt"))

    def test_single_star_stays_within_segment(self) -> None:
        regex = cilib.glob_to_regex("db/*.sql")
        self.assertTrue(regex.fullmatch("db/V1.sql"))
        self.assertFalse(regex.fullmatch("db/x/V1.sql"))

    def test_trailing_double_star_matches_everything_below(self) -> None:
        self.assertTrue(cilib.glob_to_regex(".mvn/wrapper/**").fullmatch(".mvn/wrapper/a/b.jar"))


class ExclusionFileTest(unittest.TestCase):
    def _write(self, content: str) -> Path:
        handle = tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8")
        handle.write(content)
        handle.close()
        self.addCleanup(Path(handle.name).unlink)
        return Path(handle.name)

    def test_parses_rules_and_ignores_comments(self) -> None:
        rules = cilib.load_exclusions(self._write("# comment\n\n**/*.png  # binary\n"))
        self.assertEqual([r.pattern for r in rules], ["**/*.png"])
        self.assertEqual(rules[0].reason, "binary")
        self.assertTrue(cilib.is_excluded("x/y.png", rules))
        self.assertFalse(cilib.is_excluded("x/y.java", rules))

    def test_rule_without_reason_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            cilib.load_exclusions(self._write("**/*.png\n"))
        with self.assertRaises(ValueError):
            cilib.load_exclusions(self._write("**/*.png   #   \n"))

    def test_missing_file_means_no_rules(self) -> None:
        self.assertEqual(cilib.load_exclusions(Path("/nonexistent/exclusions.txt")), [])


class ListRepositoryFilesTest(unittest.TestCase):
    def test_lists_tracked_and_untracked_but_not_ignored_or_deleted(self) -> None:
        root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, root, True)
        subprocess.run(["git", "init", "-q"], cwd=root, check=True)
        (root / ".gitignore").write_text("ignored/\n", encoding="utf-8")
        for name in ("tracked.txt", "gone.txt", "new.txt", "ignored/x.txt"):
            (root / name).parent.mkdir(parents=True, exist_ok=True)
            (root / name).write_text("x\n", encoding="utf-8")
        subprocess.run(["git", "add", "tracked.txt", "gone.txt"], cwd=root, check=True)
        (root / "gone.txt").unlink()
        self.assertEqual(cilib.list_repository_files(root), [".gitignore", "new.txt", "tracked.txt"])


if __name__ == "__main__":
    unittest.main()
