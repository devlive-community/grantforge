# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/release_notes.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import importlib.util
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parents[1] / "release_notes.py"
_SPEC = importlib.util.spec_from_file_location("release_notes", _SCRIPT)
assert _SPEC is not None and _SPEC.loader is not None
notes = importlib.util.module_from_spec(_SPEC)
sys.modules["release_notes"] = notes
_SPEC.loader.exec_module(notes)

_PAGE = """---
title: {title}
description: What changed.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge
-->

The changes. See [the upgrade](/start/upgrade/) and [GitHub](https://github.com/x).
"""


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        changelog = self.root / "docs" / "content" / "changelog"
        changelog.mkdir(parents=True)
        (changelog / "rebuild.md").write_text(_PAGE.format(title="2026.0.0（重构版）"), encoding="utf-8")
        (changelog / "1.0.6.md").write_text(_PAGE.format(title='"1.0.6"'), encoding="utf-8")
        (self.root / "docs" / "public").mkdir(parents=True)
        (self.root / "docs" / "public" / "CNAME").write_text("docs.example.org\n", encoding="utf-8")

    def test_finds_the_page_by_its_title(self) -> None:
        self.assertEqual(notes.find_page(self.root, "2026.0.0").name, "rebuild.md")
        self.assertEqual(notes.find_page(self.root, "1.0.6").name, "1.0.6.md")
        self.assertIsNone(notes.find_page(self.root, "2026.0"))
        self.assertIsNone(notes.find_page(self.root, "1.0"))

    def test_a_title_names_a_version_only_when_nothing_numeric_follows(self) -> None:
        self.assertTrue(notes.names_version("2026.0.0", "2026.0.0"))
        self.assertTrue(notes.names_version("2026.0.0 (rebuild)", "2026.0.0"))
        self.assertFalse(notes.names_version("2026.0.01", "2026.0.0"))
        self.assertFalse(notes.names_version("2026.0.0.1", "2026.0.0"))

    def test_notes_drop_the_header_and_make_site_links_absolute(self) -> None:
        written = notes.notes(_PAGE.format(title="2026.0.0"), notes.site_url(self.root))
        self.assertEqual(written, "The changes. See [the upgrade](https://docs.example.org/start/upgrade/) and "
                                  "[GitHub](https://github.com/x).\n")

    def test_main_writes_the_notes_or_fails_without_a_page(self) -> None:
        output = self.root / "notes.md"
        self.assertEqual(notes.main(["2026.0.0", "--root", str(self.root), "--output", str(output)]), 0)
        self.assertTrue(output.read_text(encoding="utf-8").startswith("The changes."))
        self.assertEqual(notes.main(["2027.0.0", "--root", str(self.root)]), 1)


if __name__ == "__main__":
    unittest.main()
