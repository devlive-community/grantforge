# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_forbidden_paths.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import importlib.util
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parents[1] / "check_forbidden_paths.py"
_SPEC = importlib.util.spec_from_file_location("check_forbidden_paths", _SCRIPT)
assert _SPEC is not None and _SPEC.loader is not None
chk = importlib.util.module_from_spec(_SPEC)
sys.modules["check_forbidden_paths"] = chk
_SPEC.loader.exec_module(chk)


class FindForbiddenTest(unittest.TestCase):
    def test_flags_private_memory_ide_build_and_secrets(self) -> None:
        paths = [
            ".claude/memory/plan.md",
            "sub/.Codex/AGENTS.md",
            ".idea/workspace.xml",
            "core/a/target/classes/A.class",
            "core/web/dist/index.js",
            "core/web/node_modules/x/index.js",
            "script/ci/__pycache__/a.cpython-312.pyc",
            "docs/.DS_Store",
            "pom.xml.versionsBackup",
            "config/.env",
            "config/.env.production",
            "deploy/server.pem",
            "hadoop/nn.keytab",
            "home/id_ed25519",
            "grantforge.iml",
        ]
        found = {f.path for f in chk.find_forbidden(paths)}
        self.assertEqual(found, set(paths))

    def test_accepts_normal_sources(self) -> None:
        paths = [
            "core/grantforge-web/src/App.vue",
            "script/bin/startup.sh",
            "docs/docs/distribution.md",  # 'dist' as a word prefix is fine
            "core/targets/Readme.md",  # 'targets' is not 'target'
            "src/environment.ts",
            "src/keyboard.ts",
            "src/monkey.java",
        ]
        self.assertEqual(chk.find_forbidden(paths), [])

    def test_allowed_template_is_not_flagged(self) -> None:
        self.assertEqual(chk.find_forbidden(["deploy/.env.example"]), [])

    def test_each_path_reported_once(self) -> None:
        # Matches both the .claude and bytecode rules but must be reported once.
        findings = chk.find_forbidden([".claude/x.pyc"])
        self.assertEqual(len(findings), 1)
        self.assertIn("Claude", findings[0].reason)


class GitignoreTest(unittest.TestCase):
    def test_missing_file_reports_all_required_entries(self) -> None:
        self.assertEqual(chk.missing_gitignore_entries(None), list(chk.REQUIRED_GITIGNORE))

    def test_entries_are_matched_by_whole_line(self) -> None:
        content = "  .claude/  \n# /.Codex/\n"
        self.assertEqual(chk.missing_gitignore_entries(content), ["/.Codex/"])

    def test_complete_file(self) -> None:
        self.assertEqual(chk.missing_gitignore_entries(".claude/\n/.Codex/\n"), [])


class MainTest(unittest.TestCase):
    """End-to-end behaviour against a temporary git repository."""

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        (self.root / ".gitignore").write_text(".claude/\n/.Codex/\n", encoding="utf-8")
        (self.root / "README.md").write_text("x\n", encoding="utf-8")
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)

    def test_clean_repository_passes(self) -> None:
        self.assertEqual(chk.main(["--root", str(self.root)]), 0)

    def test_force_added_private_memory_fails(self) -> None:
        memory = self.root / ".claude" / "CLAUDE.md"
        memory.parent.mkdir()
        memory.write_text("private\n", encoding="utf-8")
        subprocess.run(["git", "add", "-f", ".claude/CLAUDE.md"], cwd=self.root, check=True)
        self.assertEqual(chk.main(["--root", str(self.root)]), 1)

    def test_ignored_untracked_file_is_not_reported(self) -> None:
        (self.root / ".claude").mkdir()
        (self.root / ".claude" / "CLAUDE.md").write_text("private\n", encoding="utf-8")
        self.assertEqual(chk.main(["--root", str(self.root)]), 0)

    def test_removed_gitignore_entry_fails(self) -> None:
        (self.root / ".gitignore").write_text(".claude/\n", encoding="utf-8")
        self.assertEqual(chk.main(["--root", str(self.root)]), 1)


if __name__ == "__main__":
    unittest.main()
