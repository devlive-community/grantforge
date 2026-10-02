# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_java_bytecode.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import io
import shutil
import struct
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_java_bytecode as chk  # noqa: E402

POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modules><module>core/a</module>{extra}</modules>
</project>
"""


def class_bytes(major: int) -> bytes:
    return b"\xca\xfe\xba\xbe" + struct.pack(">HH", 0, major) + b"\x00" * 8


class ClassMajorTest(unittest.TestCase):
    def test_reads_major_version(self) -> None:
        self.assertEqual(chk.class_major(class_bytes(61)[:8]), 61)

    def test_rejects_invalid_header(self) -> None:
        self.assertIsNone(chk.class_major(b"\x00" * 8))
        self.assertIsNone(chk.class_major(b"\xca\xfe"))

    def test_java_name(self) -> None:
        self.assertEqual(chk.java_name(52), "Java 8")
        self.assertEqual(chk.java_name(61), "Java 17")
        self.assertEqual(chk.java_name(70), "Java 26")


class CheckTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        (self.root / "pom.xml").write_text(POM.format(extra=""), encoding="utf-8")
        self.module = self.root / "core" / "a"

    def _source(self, kind: str) -> None:
        path = self.module / "src" / kind / "java" / "A.java"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("class A {}\n", encoding="utf-8")

    def _class(self, output: str, name: str, data: bytes) -> None:
        path = self.module / "target" / output / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def test_accepts_classes_within_limit(self) -> None:
        self._source("main")
        self._class("classes", "A.class", class_bytes(52))
        self.assertEqual(chk.check(self.root, 52), (1, []))

    def test_rejects_newer_classes(self) -> None:
        self._source("main")
        self._class("classes", "A.class", class_bytes(61))
        count, errors = chk.check(self.root, 52)
        self.assertEqual(count, 1)
        self.assertIn("compiled for Java 17", errors[0])

    def test_reports_invalid_and_missing_output(self) -> None:
        self._source("main")
        self._source("test")
        self._class("classes", "Bad.class", b"nope")
        _, errors = chk.check(self.root, 61)
        self.assertTrue(any("invalid class file header" in e for e in errors))
        self.assertTrue(any("missing compiled classes" in e for e in errors))

    def test_module_without_sources_is_skipped(self) -> None:
        self.assertEqual(chk.check(self.root, 52), (0, ["no compiled Java classes found; build the project first"]))

    def test_pom_without_modules(self) -> None:
        (self.root / "pom.xml").write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>', encoding="utf-8")
        _, errors = chk.check(self.root, 52)
        self.assertIn("no Java modules declared in pom.xml", errors)

    def test_blank_module_entries_are_ignored(self) -> None:
        (self.root / "pom.xml").write_text(POM.format(extra="<module> </module>"), encoding="utf-8")
        self.assertEqual(chk.maven_modules(self.root), [self.module])

    def test_a_module_can_be_held_to_an_older_release_for_its_main_classes_only(self) -> None:
        self._source("main")
        self._source("test")
        self._class("classes", "A.class", class_bytes(52))
        self._class("test-classes", "ATest.class", class_bytes(61))
        self.assertEqual(chk.check(self.root, 52, ["core/a"], main_only=True), (1, []))
        self.assertIn("compiled for Java 17", chk.check(self.root, 52, ["core/a"])[1][0])
        self.assertEqual(chk.check(self.root, 52, ["core/b"])[1], ["not a module of pom.xml: core/b"])
        with mock.patch("sys.stdout", new_callable=io.StringIO):
            arguments = ["--root", str(self.root), "--max-major", "52", "--module", "core/a", "--main-only"]
            self.assertEqual(chk.main(arguments), 0)

    def test_main_exit_codes(self) -> None:
        self._source("main")
        self._class("classes", "A.class", class_bytes(61))
        with mock.patch("sys.stdout", new_callable=io.StringIO), mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(chk.main(["--root", str(self.root), "--max-major", "52"]), 1)
            self.assertEqual(chk.main(["--root", str(self.root), "--max-major", "61"]), 0)


if __name__ == "__main__":
    unittest.main()
