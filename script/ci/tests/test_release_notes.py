# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/release_notes.py (stdlib unittest, against a temporary git repository)."""

from __future__ import annotations

import importlib.util
import shutil
import subprocess
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

REPO = "acme/grantforge"


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        self._git("init", "-q", "-b", "dev")
        self._git("config", "user.name", "Tester")
        self._git("config", "user.email", "tester@example.org")

    def _git(self, *args: str) -> str:
        result = subprocess.run(["git", *args], cwd=self.root, check=True, stdout=subprocess.PIPE)
        return result.stdout.decode("utf-8").strip()

    def _commit(self, message: str) -> str:
        self._git("commit", "-q", "--allow-empty", "-m", message)
        return self._git("rev-parse", "HEAD")

    def _main(self, *args: str) -> int:
        return notes.main(["--root", str(self.root), "--repo", REPO, *args])

    def test_the_first_release_lists_the_whole_history_by_type(self) -> None:
        start = self._commit("chore: start")
        feature = self._commit("feat(web): add the console")
        self._commit("fix: repair the sign-in")
        self._commit("Legacy message")
        self._git("tag", "v2026.0.0")

        found = notes.commits(self.root, None, "v2026.0.0")
        self.assertEqual([commit.sha for commit in found][:2], [start, feature])
        written = notes.notes(found, REPO, None, "v2026.0.0")
        self.assertTrue(written.startswith("首个发布版本，包含仓库的全部 4 个提交。"))
        link = f"[{feature[:8]}](https://github.com/{REPO}/commit/{feature})"
        self.assertIn(f"## 新功能\n\n- **web**: add the console ({link})", written)
        self.assertIn("## 问题修复\n\n- repair the sign-in", written)
        self.assertIn("## 其他\n\n- Legacy message", written)
        self.assertLess(written.index("## 新功能"), written.index("## 问题修复"))
        self.assertNotIn("## 不兼容变更", written)

    def test_a_later_release_lists_what_came_after_the_previous_tag(self) -> None:
        self._commit("feat: add one")
        self._git("tag", "v2026.0.0")
        self._git("tag", "legacy-2026.0.0")
        self._commit("feat!: drop the old API")
        self._commit("refactor: tidy up\n\nBREAKING CHANGE: the setting is renamed")
        self._git("checkout", "-q", "-b", "topic")
        self._commit("perf: speed up")
        self._git("checkout", "-q", "dev")
        self._git("merge", "-q", "--no-ff", "topic", "-m", "Merge branch 'topic'")

        self.assertEqual(notes.previous_tag(self.root, "HEAD"), "v2026.0.0")
        written = notes.notes(notes.commits(self.root, "v2026.0.0", "HEAD"), REPO, "v2026.0.0", "v2026.1.0")
        compare = f"https://github.com/{REPO}/compare/v2026.0.0...v2026.1.0"
        self.assertTrue(written.startswith(f"**完整变更**：[v2026.0.0...v2026.1.0]({compare})，共 3 个提交。"))
        breaking = written[written.index("## 不兼容变更"):written.index("## 新功能")]
        self.assertIn("drop the old API", breaking)
        self.assertIn("tidy up", breaking)
        self.assertIn("## 性能\n\n- speed up", written)
        self.assertNotIn("Merge branch", written)
        self.assertNotIn("add one", written)

    def test_earlier_releases_tagged_without_v_count_but_marker_tags_do_not(self) -> None:
        self._commit("chore: release 1.0.6")
        self._git("tag", "1.0.6")
        self._commit("feat: rebuild")
        self._git("tag", "legacy-2026.0.0")
        self._commit("fix: repair")

        self.assertEqual(notes.previous_tag(self.root, "HEAD"), "1.0.6")
        written = notes.notes(notes.commits(self.root, "1.0.6", "HEAD"), REPO, "1.0.6", "v2026.0.0")
        self.assertIn("[1.0.6...v2026.0.0]", written)
        self.assertIn("共 2 个提交", written)

    def test_the_tag_itself_is_no_previous_release(self) -> None:
        self._commit("feat: add one")
        self._git("tag", "v2026.0.0")
        self._commit("fix: repair")
        self._git("tag", "v2026.0.1")

        self.assertEqual(notes.previous_tag(self.root, "v2026.0.1"), "v2026.0.0")
        self.assertIsNone(notes.previous_tag(self.root, "v2026.0.0"))

    def test_huge_bodies_are_cut_with_a_link(self) -> None:
        found = [notes.Commit(f"{index:040x}", "feat: " + "x" * 200, "") for index in range(800)]
        written = notes.notes(found, REPO, None, "v2026.0.0")
        self.assertLessEqual(len(written), notes.MAX_BODY)
        self.assertTrue(written.rstrip().endswith(f"(https://github.com/{REPO}/commits/v2026.0.0)。"))

    def test_main_writes_the_notes_and_reports_failures(self) -> None:
        self._commit("feat: add one")
        output = self.root / "notes.md"
        self.assertEqual(self._main("v2026.0.0", "--head", "HEAD", "--output", str(output)), 0)
        self.assertIn("- add one", output.read_text(encoding="utf-8"))
        self.assertEqual(self._main("v2026.0.0"), 1)

    def test_the_repository_comes_from_the_origin_remote(self) -> None:
        self._git("remote", "add", "origin", "git@github.com:devlive-community/grantforge.git")
        self.assertEqual(notes.repository(self.root, None if "GITHUB_REPOSITORY" not in notes.os.environ else
                                          notes.os.environ["GITHUB_REPOSITORY"]), notes.os.environ.get(
                                              "GITHUB_REPOSITORY", "devlive-community/grantforge"))
        self._git("remote", "set-url", "origin", "https://example.org/other.git")
        if "GITHUB_REPOSITORY" not in notes.os.environ:
            with self.assertRaises(ValueError):
                notes.repository(self.root, None)


if __name__ == "__main__":
    unittest.main()
