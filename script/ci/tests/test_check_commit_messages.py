# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_commit_messages.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import importlib.util
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parents[1] / "check_commit_messages.py"
_SPEC = importlib.util.spec_from_file_location("check_commit_messages", _SCRIPT)
assert _SPEC is not None and _SPEC.loader is not None
chk = importlib.util.module_from_spec(_SPEC)
sys.modules["check_commit_messages"] = chk
_SPEC.loader.exec_module(chk)


class ValidateMessageTest(unittest.TestCase):
    def assertValid(self, message: str) -> None:
        self.assertEqual(chk.validate_message(message), [])

    def assertInvalid(self, message: str, fragment: str) -> None:
        errors = chk.validate_message(message)
        self.assertTrue(any(fragment in e for e in errors), f"{fragment!r} not in {errors}")

    def test_accepts_valid_messages(self) -> None:
        self.assertValid("feat(web): add role matrix")
        self.assertValid("fix: reject unknown endpoints\n\n- deny by default\n")
        self.assertValid("feat(api)!: rename listItems\n\nBREAKING CHANGE: callers must switch.")
        self.assertValid("chore(plugin/hdfs): bump hadoop client to 3.4.1")
        self.assertValid("revert: feat(web): add role matrix\n\nThis reverts commit abc1234.")

    def test_ignores_git_comments_and_trailing_blank_lines(self) -> None:
        self.assertValid("ci: add checks\n\n# Please enter the commit message\n\n\n")

    def test_rejects_empty(self) -> None:
        self.assertInvalid("# only comments\n", "empty")

    def test_rejects_bad_header_format(self) -> None:
        self.assertInvalid("update the readme", "header must match")
        self.assertInvalid("Feat: add x", "header must match")
        self.assertInvalid("feature(web): add x", "header must match")
        self.assertInvalid("feat(Web): add x", "header must match")
        self.assertInvalid("feat:add x", "header must match")

    def test_rejects_long_header_and_period(self) -> None:
        self.assertInvalid("feat: " + "a" * 70, "max 72")
        self.assertInvalid("feat: add x.", "period")

    def test_rejects_non_imperative_or_capitalised_subject(self) -> None:
        self.assertInvalid("fix: fixed the login", "imperative")
        self.assertInvalid("feat: adds matrix", "imperative")
        self.assertInvalid("feat: Add matrix", "lowercase")

    def test_rejects_vague_subject(self) -> None:
        self.assertInvalid("chore: update", "vague")
        self.assertInvalid("fix: fix bug", "vague")

    def test_revert_subject_keeps_original_header(self) -> None:
        # The quoted original header starts with a type, not a verb; it must not be flagged.
        self.assertValid("revert: Fix something odd")

    def test_requires_blank_second_line(self) -> None:
        self.assertInvalid("feat: add x\nbody right away", "line 2 must be blank")

    def test_body_line_length_allows_urls(self) -> None:
        self.assertInvalid("feat: add x\n\n" + "b" * 101, "line 3")
        self.assertValid("feat: add x\n\nsee https://example.org/" + "p" * 120)

    def test_rejects_cjk(self) -> None:
        self.assertInvalid("feat: add 权限 matrix", "CJK")
        self.assertInvalid("feat: add x\n\n説明", "CJK")

    def test_rejects_ai_attribution(self) -> None:
        self.assertInvalid("feat: add x\n\nCo-Authored-By: Claude <noreply@anthropic.com>", "AI attribution")
        self.assertInvalid("feat: add x\n\nGenerated with Claude Code", "AI attribution")
        self.assertInvalid("feat: add x\n\n\U0001F916 done", "AI attribution")
        self.assertValid("feat: add x\n\nCo-Authored-By: Jane Doe <jane@example.org>")

    def test_rejects_personal_paths(self) -> None:
        self.assertInvalid("fix: add x\n\nsee /Users/alice/code", "personal path")
        self.assertInvalid("fix: add x\n\nsee /home/bob/code", "personal path")

    def test_crlf_is_normalised(self) -> None:
        self.assertValid("feat: add x\r\n\r\n- body\r\n")


class RangeTest(unittest.TestCase):
    """End-to-end behaviour against a temporary git repository."""

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        self._git("init", "-q", "-b", "main")
        self._git("config", "user.name", "Tester")
        self._git("config", "user.email", "tester@example.org")

    def _git(self, *args: str) -> str:
        result = subprocess.run(["git", *args], cwd=self.root, check=True, stdout=subprocess.PIPE)
        return result.stdout.decode("utf-8").strip()

    def _commit(self, message: str) -> str:
        self._git("commit", "-q", "--allow-empty", "-m", message)
        return self._git("rev-parse", "HEAD")

    def _main(self, *args: str) -> int:
        return chk.main(["--root", str(self.root), *args])

    def test_range_checks_only_new_commits(self) -> None:
        base = self._commit("legacy message that breaks every rule.")
        head = self._commit("feat: add x")
        self.assertEqual(self._main("--base", base, "--head", head), 0)
        bad = self._commit("oops")
        self.assertEqual(self._main("--base", base, "--head", bad), 1)

    def test_zero_base_checks_head_only(self) -> None:
        self._commit("legacy bad message.")
        head = self._commit("fix: repair y")
        self.assertEqual(self._main("--base", "0" * 40, "--head", head), 0)
        self.assertEqual(self._main("--base", "", "--head", head), 0)

    def test_merge_commits_are_skipped(self) -> None:
        base = self._commit("chore: start")
        self._git("checkout", "-q", "-b", "topic")
        self._commit("feat: add topic")
        self._git("checkout", "-q", "main")
        self._commit("fix: repair main")
        self._git("merge", "-q", "--no-ff", "topic", "-m", "Merge branch 'topic'")
        self.assertEqual(self._main("--base", base, "--head", "HEAD"), 0)

    def test_commit_parsing(self) -> None:
        base = self._commit("chore: start")
        self._commit("feat: add x\n\n- body line")
        commits = chk.commits_in_range(self.root, base, "HEAD")
        self.assertEqual(len(commits), 1)
        self.assertEqual(commits[0].message.strip(), "feat: add x\n\n- body line")
        self.assertEqual(commits[0].parents, 1)

    def test_file_mode(self) -> None:
        message = self.root / "MSG"
        message.write_text("feat: add x\n", encoding="utf-8")
        self.assertEqual(self._main("--file", str(message)), 0)
        message.write_text("bad\n", encoding="utf-8")
        self.assertEqual(self._main("--file", str(message)), 1)


if __name__ == "__main__":
    unittest.main()
