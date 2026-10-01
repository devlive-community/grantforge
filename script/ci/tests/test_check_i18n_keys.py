# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_i18n_keys.py (stdlib unittest, no dependencies)."""

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
import check_i18n_keys as chk  # noqa: E402

DICTIONARY = """const zhCN = {
  common: {
    retry: '重试',
    reload: '重新加载',
  },
  status: { active: '正常', locked: '已锁定' },
  nested: { deep: { value: 'x\\'s' } },
}

export default zhCN
"""


class DictionaryKeysTest(unittest.TestCase):
    def test_reads_nested_and_inline_objects(self) -> None:
        self.assertEqual(chk.dictionary_keys(DICTIONARY),
                         ["common.retry", "common.reload", "status.active", "status.locked", "nested.deep.value"])

    def test_rejects_unsupported_layouts(self) -> None:
        with self.assertRaises(ValueError):
            chk.dictionary_keys("export default {}")
        with self.assertRaises(ValueError):
            chk.dictionary_keys("const x = { a: `template` }")
        with self.assertRaises(ValueError):
            chk.dictionary_keys("const x = { 'a' }")
        with self.assertRaises(ValueError):
            chk.dictionary_keys("const x = { { a: 'b' } }")


class PropertiesTest(unittest.TestCase):
    def test_parses_keys_ignoring_comments(self) -> None:
        text = "# comment\n! other\n\nerror.a=Hello\nerror.b = World \nerror.c:\n"
        self.assertEqual(chk.read_properties(text), {"error.a": "Hello", "error.b": "World", "error.c": ""})


class RepositoryTest(unittest.TestCase):
    """End-to-end behaviour against a temporary git repository."""

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        self._write(chk.REFERENCE_DICTIONARY, DICTIONARY)
        self._write(chk.LOCALES + "en-US.ts", "const enUS = { common: { retry: 'Retry' } }\n")
        self._write(chk.WEB_SOURCE + "App.vue",
                    "<template>{{ t('common.retry') }} {{ t('common.reload') }} {{ t(\"status.active\") }}"
                    " {{ t('status.locked') }} {{ t('nested.deep.value') }}</template>\n")
        self._write(chk.WEB_SOURCE + "App.test.ts", "expect(text).toBe('重试')\n")
        self._write(chk.WEB_SOURCE + "note.ts", "// 注释里的中文不算\n/* 块注释 */\nexport const url = 'https://x'\n")
        self._write("core/s/src/main/resources/i18n/messages.properties", "error.a=A\nerror.b=B\n")
        self._write("core/s/src/main/resources/i18n/messages_zh_CN.properties", "error.a=甲\nerror.b=乙\n")

    def _write(self, path: str, content: str) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def _errors(self) -> list:
        paths = chk.list_repository_files(self.root)
        return chk.check_web(self.root, paths) + chk.check_server(self.root, paths)

    def test_clean_repository_passes(self) -> None:
        self.assertEqual(self._errors(), [])
        with mock.patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(chk.main(["--root", str(self.root)]), 0)

    def test_reports_hard_coded_text_once_per_file(self) -> None:
        self._write(chk.WEB_SOURCE + "views/X.vue", "<template><p>你好</p><p>世界</p></template>\n")
        errors = self._errors()
        self.assertEqual(len(errors), 1)
        self.assertIn("views/X.vue:1: hard-coded text", errors[0])

    def test_reports_unused_messages(self) -> None:
        self._write(chk.WEB_SOURCE + "App.vue", "<template>{{ t('common.retry') }}</template>\n")
        errors = self._errors()
        self.assertIn(f"{chk.REFERENCE_DICTIONARY}: message 'common.reload' is never used", errors)
        self.assertEqual(len([e for e in errors if "never used" in e]), 4)

    def test_keys_named_by_the_permission_manifest_count_as_used(self) -> None:
        self._write(chk.WEB_SOURCE + "App.vue", "<template>{{ t('common.retry') }} {{ t('common.reload') }}"
                    " {{ t('status.active') }} {{ t('status.locked') }}</template>\n")
        self.assertIn(f"{chk.REFERENCE_DICTIONARY}: message 'nested.deep.value' is never used", self._errors())
        self._write(chk.KEY_DATA + "manifest.json", '{"resources": [{"nameKey": "nested.deep.value"}]}\n')
        self._write(chk.WEB_SOURCE + "api/other.json", '{"x": "common.retry"}\n')
        self.assertEqual(self._errors(), [])

    def test_reports_unparsable_dictionary(self) -> None:
        self._write(chk.REFERENCE_DICTIONARY, "const zhCN = { a: 1 }\n")
        self.assertTrue(any("cannot parse" in e for e in self._errors()))

    def test_reports_server_bundle_drift_and_empty_messages(self) -> None:
        self._write("core/s/src/main/resources/i18n/messages_zh_CN.properties", "error.a=\nerror.extra=多余\n")
        self._write("core/s/src/main/resources/i18n/messages.properties", "error.a=A\nerror.b=\n")
        errors = self._errors()
        self.assertIn("core/s/src/main/resources/i18n/messages_zh_CN.properties: missing 'error.b'", errors)
        self.assertIn("core/s/src/main/resources/i18n/messages_zh_CN.properties: 'error.extra' is not in "
                      "messages.properties", errors)
        self.assertIn("core/s/src/main/resources/i18n/messages_zh_CN.properties: message 'error.a' is empty", errors)
        self.assertIn("core/s/src/main/resources/i18n/messages.properties: message 'error.b' is empty", errors)
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(chk.main(["--root", str(self.root)]), 1)

    def test_module_bundles_are_checked_and_keys_stay_unique(self) -> None:
        self._write("core/m/src/main/resources/i18n/identity.properties", "error.x=X\nerror.a=Again\n")
        self._write("core/m/src/main/resources/i18n/identity_zh_CN.properties", "error.x=\n")
        errors = self._errors()
        self.assertIn("core/m/src/main/resources/i18n/identity_zh_CN.properties: missing 'error.a'", errors)
        self.assertIn("core/m/src/main/resources/i18n/identity_zh_CN.properties: message 'error.x' is empty", errors)
        self.assertIn("core/s/src/main/resources/i18n/messages.properties: 'error.a' is also defined in "
                      "core/m/src/main/resources/i18n/identity.properties", errors)

    def test_repository_without_a_dictionary_only_checks_text(self) -> None:
        (self.root / chk.REFERENCE_DICTIONARY).unlink()
        self.assertEqual(self._errors(), [])


if __name__ == "__main__":
    unittest.main()
