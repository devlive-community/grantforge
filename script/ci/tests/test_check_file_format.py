# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_file_format.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import io
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_file_format as chk  # noqa: E402


class CheckTextTest(unittest.TestCase):
    def test_clean_text(self) -> None:
        self.assertEqual(chk.check_text("a\n  b\n", "a.java"), [])
        self.assertEqual(chk.check_text("", "a.java"), [])

    def test_detects_each_rule(self) -> None:
        self.assertIn("LF required", chk.check_text("a\r\n", "a.java")[0])
        self.assertEqual(chk.check_text("a", "a.java"), ["does not end with a newline"])
        self.assertEqual(chk.check_text("a\nb \n", "a.java"), ["trailing whitespace on line 2"])
        self.assertEqual(chk.check_text("a\n\tb\n", "a.java"), ["tab indentation on line 2"])
        self.assertEqual(chk.check_text("a\n  \tb\n", "a.java"), ["tab indentation on line 2"])

    def test_inner_tabs_are_allowed(self) -> None:
        self.assertEqual(chk.check_text("a\tb\n", "a.properties"), [])

    def test_markdown_may_keep_trailing_spaces(self) -> None:
        self.assertEqual(chk.check_text("line  \nnext\n", "README.md"), [])


class FixTextTest(unittest.TestCase):
    def test_fixes_endings_whitespace_and_newline(self) -> None:
        self.assertEqual(chk.fix_text("a \r\nb\rc", "a.ts"), "a\nb\nc\n")

    def test_keeps_markdown_trailing_spaces(self) -> None:
        self.assertEqual(chk.fix_text("a  \nb", "a.md"), "a  \nb\n")

    def test_empty_stays_empty(self) -> None:
        self.assertEqual(chk.fix_text("", "a.ts"), "")


class RunTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        self.exclusions = self.root / "rules.txt"
        self.exclusions.write_text("rules.txt  # rule file\n**/*.png  # binary\n", encoding="utf-8")

    def _main(self, *extra: str) -> int:
        with mock.patch("sys.stdout", new_callable=io.StringIO), mock.patch("sys.stderr", new_callable=io.StringIO):
            return chk.main(["--root", str(self.root), "--exclusions", str(self.exclusions), *extra])

    def test_fix_repairs_what_it_can(self) -> None:
        (self.root / "a.ts").write_bytes(b"x \r\ny")
        (self.root / "b.java").write_bytes(b"\tclass B {}\n")
        (self.root / "logo.png").write_bytes(b"\x89PNG\xff")
        self.assertEqual(self._main(), 1)
        findings, fixed = chk.run(self.root, self.exclusions, [], fix=True)
        self.assertEqual(fixed, ["a.ts"])
        self.assertEqual((self.root / "a.ts").read_bytes(), b"x\ny\n")
        self.assertEqual([f.path for f in findings], ["b.java"])  # tabs are never rewritten automatically

    def test_non_utf8_needs_exclusion(self) -> None:
        (self.root / "data.bin").write_bytes(b"\xff\xfe")
        self.assertEqual(self._main(), 1)
        self.exclusions.write_text("rules.txt  # rule file\n*.bin  # binary\n", encoding="utf-8")
        self.assertEqual(self._main(), 0)

    def test_invalid_exclusions(self) -> None:
        self.exclusions.write_text("*.bin\n", encoding="utf-8")
        self.assertEqual(self._main(), 2)


if __name__ == "__main__":
    unittest.main()
