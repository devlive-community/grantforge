# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_migrations.py (stdlib unittest, no dependencies)."""

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
import check_migrations as chk  # noqa: E402

MAIN = "core/identity/src/main/resources/db/changelog/identity.yaml"
TEST = "core/identity/src/test/resources/db/changelog/test.yaml"


def changelog(*changesets: tuple) -> str:
    """Render a changelog in the project layout from (id, author) pairs."""
    lines = ["databaseChangeLog:", "  - property:", "      name: text", "      value: VARCHAR"]
    for changeset_id, author in changesets:
        lines += ["  - changeSet:", f"      id: {changeset_id}", f"      author: {author}", "      changes:",
                  "        - createTable:", "            tableName: gf_x", "            columns:",
                  "              - column:", "                  name: id"]
    return "\n".join(lines) + "\n"


class ParseTest(unittest.TestCase):
    def test_reads_id_and_author_per_changeset(self) -> None:
        changesets = chk.parse_changesets(MAIN, changelog(("identity-0001-a", "grantforge"), ("identity-0002-b", "x")))
        self.assertEqual([(c.id, c.author, c.line) for c in changesets],
                         [("identity-0001-a", "grantforge", 5), ("identity-0002-b", "x", 14)])

    def test_first_id_after_the_marker_is_used(self) -> None:
        text = ("databaseChangeLog:\n  - changeSet:\n      author: grantforge\n"
                "      changes:\n        - x:\n            id: nested\n")
        changesets = chk.parse_changesets(MAIN, text)
        # The first id key after the marker is used; a missing top-level id is still reported by the rules.
        self.assertEqual(changesets[0].id, "nested")

    def test_quotes_are_stripped_and_empty_file_has_no_changesets(self) -> None:
        self.assertEqual(chk.parse_changesets(MAIN, "  - changeSet:\n      id: 'a-0001-b'\n")[0].id, "a-0001-b")
        self.assertEqual(chk.parse_changesets(MAIN, "databaseChangeLog: []\n"), [])


class RulesTest(unittest.TestCase):
    def test_valid_changesets_pass(self) -> None:
        changesets = chk.parse_changesets(MAIN, changelog(("identity-0001-create-gf-user", "grantforge")))
        self.assertEqual(chk.check_changesets(changesets), [])

    def test_reports_bad_ids_authors_and_duplicates(self) -> None:
        changesets = chk.parse_changesets(MAIN, changelog(
            ("Identity-1-x", "grantforge"), ("identity-0002-ok", "someone"),
            ("identity-0003-dup", "grantforge"), ("identity-0003-dup", "grantforge")))
        changesets.append(chk.ChangeSet(MAIN, 99, None, None))
        errors = chk.check_changesets(changesets)
        self.assertTrue(any("must look like" in e and "Identity-1-x" in e for e in errors))
        self.assertTrue(any("author must be 'grantforge'" in e and "someone" in e for e in errors))
        self.assertTrue(any("duplicate changeset id 'identity-0003-dup'" in e for e in errors))
        self.assertTrue(any(":99:" in e and "None" in e for e in errors))


class TypesTest(unittest.TestCase):
    CHANGELOG = """databaseChangeLog:
  - changeSet:
      id: demo-0001-create-gf-demo
      author: grantforge
      changes:
        - createTable:
            columns:
              - column:
                  name: name
                  type: ${text}(128)
              - column:
                  name: token
                  type: VARCHAR(64)
              - column:
                  name: body
                  type: ${longtext}
              - column:
                  name: enabled
                  type: ${flag}
              - column:
                  name: id
                  type: BIGINT
              - column:
                  name: note
                  type: ${text}
              - column:
                  name: active
                  type: BOOLEAN
              - column:
                  name: big
                  type: ${text}(4000)
        - modifyDataType:
            newDataType: CLOB
  - changeSet:
      id: oauth-0003-create-gf-oauth-signing-key
      author: grantforge
      changes:
        - createTable:
            columns:
              - column:
                  name: private_key
                  type: CLOB
"""

    def test_only_portable_types_pass(self) -> None:
        errors = chk.check_types("x.yaml", self.CHANGELOG)
        self.assertEqual([error.split(": type ")[1].split(" ")[0] for error in errors],
                         ["'${text}'", "'BOOLEAN'", "'${text}(4000)'", "'CLOB'"])
        self.assertTrue(all(": demo-0001-create-gf-demo:" in error for error in errors))


class RepositoryTest(unittest.TestCase):
    """End-to-end behaviour against a temporary git repository."""

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        self._git("init", "-q")
        self._git("config", "user.name", "Tester")
        self._git("config", "user.email", "tester@example.org")
        self._write(MAIN, changelog(("identity-0001-a", "grantforge")))
        self._write(TEST, changelog(("test-0001-a", "grantforge")))
        self._git("add", ".")
        self._git("commit", "-q", "-m", "chore: init")

    def _git(self, *args: str) -> None:
        subprocess.run(["git", *args], cwd=self.root, check=True, stdout=subprocess.DEVNULL)

    def _write(self, path: str, content: str) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def _main(self) -> int:
        with mock.patch("sys.stdout", new_callable=io.StringIO), mock.patch("sys.stderr", new_callable=io.StringIO):
            return chk.main(["--root", str(self.root)])

    def test_before_the_first_release_changes_are_allowed(self) -> None:
        self._write(MAIN, changelog(("identity-0001-a", "grantforge"), ("identity-0002-b", "grantforge")))
        info, errors = chk.run(self.root)
        self.assertEqual(errors, [])
        self.assertIn("no release tag yet", info[1])
        self.assertEqual(self._main(), 0)

    def test_released_production_changelog_is_locked(self) -> None:
        self._git("tag", "v1.0.0")
        self.assertEqual(self._main(), 0)

        self._write(MAIN, changelog(("identity-0001-a", "grantforge"), ("identity-0002-b", "grantforge")))
        _, errors = chk.run(self.root)
        self.assertEqual(len(errors), 1)
        self.assertIn("must not change", errors[0])

    def test_released_changelog_cannot_be_removed_but_test_changelogs_are_free(self) -> None:
        self._git("tag", "v1.0.0")
        self._write(TEST, changelog(("test-0001-a", "grantforge"), ("test-0002-b", "grantforge")))
        self.assertEqual(chk.run(self.root)[1], [])

        (self.root / MAIN).unlink()
        _, errors = chk.run(self.root)
        self.assertEqual(len(errors), 1)
        self.assertIn("must not be removed", errors[0])

    def test_rule_violations_fail(self) -> None:
        self._write(TEST, changelog(("bad id", "grantforge")))
        self.assertEqual(self._main(), 1)


if __name__ == "__main__":
    unittest.main()
