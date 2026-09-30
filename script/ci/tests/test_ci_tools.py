# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/ci_tools.py (stdlib unittest, no network access)."""

from __future__ import annotations

import hashlib
import io
import os
import shutil
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ci_tools  # noqa: E402


def make_archive(path: Path, members: dict) -> str:
    """Create a gzip tar with the given {name: bytes} members and return its SHA-256."""
    with tarfile.open(path, "w:gz") as tar:
        for name, data in members.items():
            info = tarfile.TarInfo(name)
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
    return hashlib.sha256(path.read_bytes()).hexdigest()


class PlatformKeyTest(unittest.TestCase):
    def test_normalises_aliases(self) -> None:
        self.assertEqual(ci_tools.platform_key("Linux", "AMD64"), ("linux", "x86_64"))
        self.assertEqual(ci_tools.platform_key("Darwin", "aarch64"), ("darwin", "arm64"))
        self.assertEqual(ci_tools.platform_key("Darwin", "arm64"), ("darwin", "arm64"))

    def test_every_binary_tool_covers_ci_and_developer_platforms(self) -> None:
        for tool in ci_tools.BINARY_TOOLS.values():
            for key in (("linux", "x86_64"), ("darwin", "arm64"), ("darwin", "x86_64")):
                self.assertIn(key, tool.assets, f"{tool.name} lacks {key}")
                self.assertRegex(tool.assets[key].sha256, r"^[0-9a-f]{64}$")


class ToolsDirTest(unittest.TestCase):
    def test_default_and_override(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=False):
            os.environ.pop("GRANTFORGE_CI_TOOLS", None)
            self.assertEqual(ci_tools.tools_dir(Path("/repo")), Path("/repo/target/ci-tools"))
        with mock.patch.dict(os.environ, {"GRANTFORGE_CI_TOOLS": "/opt/tools"}):
            self.assertEqual(ci_tools.tools_dir(Path("/repo")), Path("/opt/tools"))


class BinaryInstallTest(unittest.TestCase):
    def setUp(self) -> None:
        self.work = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.work, True)
        self.root = self.work / "repo"
        self.root.mkdir()
        patcher = mock.patch.dict(os.environ, {"GRANTFORGE_CI_TOOLS": str(self.work / "tools")})
        patcher.start()
        self.addCleanup(patcher.stop)
        self.archive = self.work / "fake.tar.gz"
        self.sha = make_archive(self.archive, {"pkg/fake": b"#!/bin/sh\necho fake\n", "../evil": b"x"})
        self.tool = ci_tools.BinaryTool(
            name="fake", version="1.0", member="pkg/fake",
            assets={("linux", "x86_64"): ci_tools.Asset("https://example.org/fake.tar.gz", self.sha)},
        )
        self.downloads = 0

    def _download(self, url: str, target: str) -> None:
        self.downloads += 1
        shutil.copyfile(self.archive, target)

    def test_installs_verifies_and_reuses(self) -> None:
        binary = ci_tools.ensure_binary(self.tool, self.root, ("linux", "x86_64"), self._download)
        self.assertEqual(binary.read_bytes(), b"#!/bin/sh\necho fake\n")
        self.assertTrue(os.access(binary, os.X_OK))
        self.assertFalse((self.work / "evil").exists())
        again = ci_tools.ensure_binary(self.tool, self.root, ("linux", "x86_64"), self._download)
        self.assertEqual(again, binary)
        self.assertEqual(self.downloads, 1)

    def test_checksum_mismatch_installs_nothing(self) -> None:
        bad = ci_tools.BinaryTool("fake", "1.0", "pkg/fake",
                                  {("linux", "x86_64"): ci_tools.Asset("https://example.org/f.tgz", "0" * 64)})
        with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
            ci_tools.ensure_binary(bad, self.root, ("linux", "x86_64"), self._download)
        self.assertFalse((self.work / "tools" / "fake-1.0" / "fake").exists())

    def test_unsupported_platform(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "no pinned download"):
            ci_tools.ensure_binary(self.tool, self.root, ("windows", "x86_64"), self._download)

    def test_extract_rejects_non_file_member(self) -> None:
        archive = self.work / "dir.tar.gz"
        with tarfile.open(archive, "w:gz") as tar:
            info = tarfile.TarInfo("pkg")
            info.type = tarfile.DIRTYPE
            tar.addfile(info)
        with self.assertRaises(ValueError):
            ci_tools.extract_member(archive, "pkg", self.work / "out")


class EnsureTest(unittest.TestCase):
    def test_unknown_tool(self) -> None:
        with self.assertRaises(KeyError):
            ci_tools.ensure("nope", Path("/tmp"))

    def test_pip_tool_is_reused_when_present(self) -> None:
        work = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, work, True)
        with mock.patch.dict(os.environ, {"GRANTFORGE_CI_TOOLS": str(work)}):
            version = ci_tools.PIP_TOOLS["ruff"]
            binary = work / f"ruff-{version}" / "bin" / "ruff"
            binary.parent.mkdir(parents=True)
            binary.write_text("", encoding="utf-8")
            with mock.patch.object(ci_tools.subprocess, "run") as run:
                self.assertEqual(ci_tools.ensure("ruff", work), binary)
                run.assert_not_called()

    def test_main_usage_and_errors(self) -> None:
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(ci_tools.main([]), 2)
            self.assertEqual(ci_tools.main(["nope"]), 1)


if __name__ == "__main__":
    unittest.main()
