# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_license_headers.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parents[1] / "check_license_headers.py"
_SPEC = importlib.util.spec_from_file_location("check_license_headers", _SCRIPT)
assert _SPEC is not None and _SPEC.loader is not None
chk = importlib.util.module_from_spec(_SPEC)
sys.modules["check_license_headers"] = chk
_SPEC.loader.exec_module(chk)

JAVA_HEADER = (
    "// Copyright (c) 2026 devlive-community/grantforge\n"
    "//\n"
    "// Licensed under the MIT License. See the LICENSE file in the\n"
    "// project root for full license text.\n"
)
HASH_HEADER = JAVA_HEADER.replace("//", "#")
MARKUP_HEADER = (
    "<!--\n"
    "  Copyright (c) 2026 devlive-community/grantforge\n"
    "\n"
    "  Licensed under the MIT License. See the LICENSE file in the\n"
    "  project root for full license text.\n"
    "-->\n"
)
LEGACY_APACHE = (
    "/**\n"
    " * Licensed to the Apache Software Foundation (ASF) under one\n"
    " * http://www.apache.org/licenses/LICENSE-2.0\n"
    " */\n"
)


class StyleResolutionTest(unittest.TestCase):
    def test_extension_filename_and_glob_rules(self) -> None:
        self.assertIs(chk.style_for("core/a/B.java"), chk.SLASH)
        self.assertIs(chk.style_for("web/src/App.vue"), chk.MARKUP)
        self.assertIs(chk.style_for("web/main.CSS"), chk.CSS)
        self.assertIs(chk.style_for("Dockerfile"), chk.HASH)
        self.assertIs(chk.style_for("docs/requirements.txt"), chk.HASH)
        self.assertIs(chk.style_for("a/b.sql"), chk.DASH)

    def test_unknown_types_return_none(self) -> None:
        self.assertIsNone(chk.style_for("notes.txt"))
        self.assertIsNone(chk.style_for("Makefile"))
        self.assertIsNone(chk.style_for(".hidden"))


class GlobTest(unittest.TestCase):
    def test_double_star_matches_zero_or_more_directories(self) -> None:
        regex = chk.glob_to_regex("**/*.png")
        self.assertTrue(regex.fullmatch("logo.png"))
        self.assertTrue(regex.fullmatch("a/b/logo.png"))
        self.assertFalse(regex.fullmatch("a/logo.png.txt"))

    def test_single_star_stays_within_segment(self) -> None:
        regex = chk.glob_to_regex("db/*.sql")
        self.assertTrue(regex.fullmatch("db/V1.sql"))
        self.assertFalse(regex.fullmatch("db/x/V1.sql"))

    def test_trailing_double_star_matches_everything_below(self) -> None:
        self.assertTrue(chk.glob_to_regex(".mvn/wrapper/**").fullmatch(".mvn/wrapper/a/b.jar"))


class ExclusionFileTest(unittest.TestCase):
    def _write(self, content: str) -> Path:
        handle = tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8")
        handle.write(content)
        handle.close()
        self.addCleanup(Path(handle.name).unlink)
        return Path(handle.name)

    def test_parses_rules_and_ignores_comments(self) -> None:
        rules = chk.load_exclusions(self._write("# comment\n\n**/*.png  # binary\n"))
        self.assertEqual([r.pattern for r in rules], ["**/*.png"])
        self.assertEqual(rules[0].reason, "binary")
        self.assertTrue(chk.is_excluded("x/y.png", rules))
        self.assertFalse(chk.is_excluded("x/y.java", rules))

    def test_rule_without_reason_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            chk.load_exclusions(self._write("**/*.png\n"))
        with self.assertRaises(ValueError):
            chk.load_exclusions(self._write("**/*.png   #   \n"))

    def test_missing_file_means_no_rules(self) -> None:
        self.assertEqual(chk.load_exclusions(Path("/nonexistent/exclusions.txt")), [])


class CheckTextTest(unittest.TestCase):
    def test_accepts_correct_header(self) -> None:
        self.assertIsNone(chk.check_text(JAVA_HEADER + "\npackage a;\n", "A.java", chk.SLASH))

    def test_accepts_header_only_file(self) -> None:
        self.assertIsNone(chk.check_text(JAVA_HEADER, "A.java", chk.SLASH))

    def test_reports_missing_header(self) -> None:
        self.assertEqual(chk.check_text("package a;\n", "A.java", chk.SLASH), "missing license header")
        self.assertEqual(chk.check_text("", "A.java", chk.SLASH), "missing license header")

    def test_reports_legacy_header_as_mismatch(self) -> None:
        reason = chk.check_text(LEGACY_APACHE + "package a;\n", "A.java", chk.SLASH)
        self.assertIsNotNone(reason)
        self.assertIn("mismatch", reason or "")

    def test_reports_wrong_year(self) -> None:
        text = JAVA_HEADER.replace("2026", "2025") + "\nx\n"
        self.assertIn("line 1", chk.check_text(text, "A.java", chk.SLASH) or "")

    def test_requires_blank_line_after_header(self) -> None:
        self.assertIn("must be blank", chk.check_text(JAVA_HEADER + "package a;\n", "A.java", chk.SLASH) or "")

    def test_shebang_stays_above_header(self) -> None:
        self.assertIsNone(chk.check_text("#!/bin/sh\n" + HASH_HEADER + "\necho\n", "a.sh", chk.HASH))
        self.assertIsNotNone(chk.check_text(HASH_HEADER + "#!/bin/sh\n", "a.sh", chk.HASH))

    def test_xml_declaration_and_doctype_stay_above_header(self) -> None:
        xml = '<?xml version="1.0" encoding="UTF-8"?>\n' + MARKUP_HEADER + "\n<a/>\n"
        self.assertIsNone(chk.check_text(xml, "pom.xml", chk.MARKUP))
        html = "<!doctype html>\n" + MARKUP_HEADER + "\n<html></html>\n"
        self.assertIsNone(chk.check_text(html, "index.html", chk.MARKUP))

    def test_markdown_front_matter_stays_above_header(self) -> None:
        md = "---\ntitle: x\n---\n" + MARKUP_HEADER + "\n# T\n"
        self.assertIsNone(chk.check_text(md, "a.md", chk.MARKUP))

    def test_bom_is_ignored(self) -> None:
        self.assertIsNone(chk.check_text("﻿" + JAVA_HEADER + "\nx\n", "A.java", chk.SLASH))

    def test_crlf_is_accepted(self) -> None:
        self.assertIsNone(chk.check_text((JAVA_HEADER + "\nx\n").replace("\n", "\r\n"), "A.java", chk.SLASH))


class FixTextTest(unittest.TestCase):
    def assertFixed(self, before: str, path: str, style: "chk.CommentStyle", expected: str) -> None:
        after = chk.fix_text(before, path, style)
        self.assertEqual(after, expected)
        self.assertIsNone(chk.check_text(after, path, style))
        # Idempotent: fixing a fixed file changes nothing.
        self.assertEqual(chk.fix_text(after, path, style), after)

    def test_inserts_header_before_content(self) -> None:
        self.assertFixed("package a;\n", "A.java", chk.SLASH, JAVA_HEADER + "\npackage a;\n")

    def test_replaces_legacy_apache_block(self) -> None:
        self.assertFixed(LEGACY_APACHE + "package a;\n", "A.java", chk.SLASH, JAVA_HEADER + "\npackage a;\n")

    def test_replaces_outdated_line_header(self) -> None:
        old = JAVA_HEADER.replace("2026", "2020")
        self.assertFixed(old + "\nx\n", "a.ts", chk.SLASH, JAVA_HEADER + "\nx\n")

    def test_keeps_non_license_leading_comment(self) -> None:
        doc = "/** Utility helpers. */\nclass A {}\n"
        self.assertFixed(doc, "A.java", chk.SLASH, JAVA_HEADER + "\n" + doc)

    def test_keeps_shebang_first(self) -> None:
        self.assertFixed("#!/bin/sh\necho hi\n", "a.sh", chk.HASH, "#!/bin/sh\n" + HASH_HEADER + "\necho hi\n")

    def test_keeps_xml_declaration_first_and_replaces_markup_license(self) -> None:
        before = '<?xml version="1.0"?>\n<!-- Licensed under Apache -->\n<a/>\n'
        self.assertFixed(before, "a.xml", chk.MARKUP, '<?xml version="1.0"?>\n' + MARKUP_HEADER + "\n<a/>\n")

    def test_keeps_markdown_front_matter_first(self) -> None:
        before = "---\ntitle: x\n---\n\n# Title\n"
        self.assertFixed(before, "a.md", chk.MARKUP, "---\ntitle: x\n---\n" + MARKUP_HEADER + "\n# Title\n")

    def test_css_block_style(self) -> None:
        expected = (
            "/*\n * Copyright (c) 2026 devlive-community/grantforge\n *\n"
            " * Licensed under the MIT License. See the LICENSE file in the\n"
            " * project root for full license text.\n */\n\n@import \"x\";\n"
        )
        self.assertFixed('@import "x";\n', "a.css", chk.CSS, expected)

    def test_preserves_bom_and_crlf(self) -> None:
        before = "﻿package a;\r\n"
        after = chk.fix_text(before, "A.java", chk.SLASH)
        self.assertTrue(after.startswith("﻿// Copyright"))
        self.assertNotIn("\n", after.replace("\r\n", ""))
        self.assertIsNone(chk.check_text(after, "A.java", chk.SLASH))

    def test_preserves_missing_trailing_newline(self) -> None:
        self.assertFixed("x", "a.ts", chk.SLASH, JAVA_HEADER + "\nx")

    def test_empty_file_gets_header_only(self) -> None:
        self.assertFixed("", "a.ts", chk.SLASH, JAVA_HEADER)

    def test_unterminated_block_is_not_removed(self) -> None:
        before = "/* License text without end\nclass A {}\n"
        self.assertTrue(chk.fix_text(before, "A.java", chk.SLASH).endswith(before))


class RunTest(unittest.TestCase):
    """End-to-end behaviour against a temporary git repository."""

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        (self.root / "exclusions.txt").write_text("exclusions.txt  # rules file\n**/*.png  # binary\n",
                                                  encoding="utf-8")

    def tearDown(self) -> None:
        subprocess.run(["rm", "-rf", str(self.root)], check=True)

    def _write(self, name: str, content: str) -> None:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_check_reports_then_fix_repairs(self) -> None:
        self._write("src/A.java", "class A {}\n")
        self._write("ok.ts", JAVA_HEADER + "\nexport {}\n")
        self._write("unknown.txt", "hello\n")
        (self.root / "logo.png").write_bytes(b"\x89PNG\x00\xff")

        findings, fixed = chk.run(self.root, self.root / "exclusions.txt", [], fix=False)
        self.assertEqual(sorted(f.path for f in findings), ["src/A.java", "unknown.txt"])
        self.assertEqual(fixed, [])

        findings, fixed = chk.run(self.root, self.root / "exclusions.txt", [], fix=True)
        self.assertEqual(fixed, ["src/A.java"])
        self.assertEqual([f.path for f in findings], ["unknown.txt"])  # unknown types are never guessed

    def test_ignored_files_are_skipped(self) -> None:
        self._write(".gitignore", HASH_HEADER + "\nbuild/\n")
        self._write("build/Gen.java", "class Gen {}\n")
        findings, _ = chk.run(self.root, self.root / "exclusions.txt", [], fix=False)
        self.assertEqual(findings, [])

    def test_non_utf8_file_is_reported(self) -> None:
        (self.root / "bad.ts").write_bytes(b"\xff\xfe\x00")
        findings, _ = chk.run(self.root, self.root / "exclusions.txt", [], fix=False)
        self.assertIn("not UTF-8", findings[0].reason)

    def test_main_exit_codes(self) -> None:
        self._write("A.java", "class A {}\n")
        args = ["--root", str(self.root), "--exclusions", str(self.root / "exclusions.txt")]
        self.assertEqual(chk.main(args), 1)
        self.assertEqual(chk.main([*args, "--fix"]), 0)
        self.assertEqual(chk.main(args), 0)
        (self.root / "exclusions.txt").write_text("**/*.png\n", encoding="utf-8")
        self.assertEqual(chk.main(args), 2)


if __name__ == "__main__":
    unittest.main()
