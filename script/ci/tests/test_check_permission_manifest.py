# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/check_permission_manifest.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import copy
import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any, Dict
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_permission_manifest as chk  # noqa: E402

MANIFEST: Dict[str, Any] = {"resources": [
    {"code": "system", "type": "MODULE", "name": "System", "children": [
        {"code": "system.user", "type": "PAGE", "name": "Users", "route": "/admin/users", "apis": ["system.user.read"],
         "children": [
             {"code": "system.user.btn.edit", "type": "ACTION", "name": "Edit", "apis": ["system.user.update"],
              "requires": ["system.user.btn.view"]},
             {"code": "system.user.btn.view", "type": "ACTION", "name": "View", "apis": ["system.user.read"]},
         ]},
    ]},
]}
CONTRACT = {"paths": {
    "/api/v1/users": {"get": {"x-permission": "system.user.read"}, "parameters": []},
    "/api/v1/users/{id}": {"put": {"x-permission": "system.user.update"}},
    "/api/v1/me": {"get": {"x-permission": "authenticated"}},
    "/api/v1/bootstrap": {"get": {"x-permission": "public"}},
}}
ROUTER = """const routes = [
  { path: '/auth/login', component: Login },
  { path: '/', component: Layout, children: [
    { path: 'admin/users', name: 'users', component: Users },
  ] },
]
"""


class CycleTest(unittest.TestCase):
    def test_finds_cycles_and_accepts_acyclic_graphs(self) -> None:
        self.assertIsNone(chk.find_cycle({"a": ["b", "c"], "b": ["c"]}))
        self.assertEqual(chk.find_cycle({"a": ["b"], "b": ["c"], "c": ["a"]}), ["a", "b", "c", "a"])
        self.assertEqual(chk.find_cycle({"x": ["y"], "y": ["y"]}), ["y", "y"])


class RepositoryTest(unittest.TestCase):
    """End-to-end behaviour against a temporary git repository."""

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        self.manifest = copy.deepcopy(MANIFEST)
        self._save()
        self._write(chk.CONTRACT, json.dumps(CONTRACT))
        self._write(chk.ROUTER, ROUTER)
        self._write(chk.SOURCES + "views/UsersView.vue",
                    "<button v-permission=\"'system.user.btn.edit'\">e</button>\n"
                    "<script>auth.holds('system.user.read'); t('titles.users'); const url = 'example.com'</script>\n")
        self._write(chk.SOURCES + "views/UsersView.test.ts", "auth.can('system.user.btn.gone')\n")

    def _write(self, path: str, content: str) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def _save(self) -> None:
        self._write(chk.MANIFEST, json.dumps(self.manifest))

    def _page(self) -> Dict[str, Any]:
        return self.manifest["resources"][0]["children"][0]

    def _errors(self) -> list:
        return chk.check(self.root, chk.list_repository_files(self.root))

    def test_consistent_repository_passes(self) -> None:
        self.assertEqual(self._errors(), [])
        with mock.patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(chk.main(["--root", str(self.root)]), 0)

    def test_reports_structural_problems(self) -> None:
        page = self._page()
        page["children"].append({"code": "system.user.btn.edit", "type": "ACTION", "name": "Again"})
        page["children"].append({"code": "other.thing", "type": "ACTION", "name": "Elsewhere"})
        page["children"].append({"code": "system.user.tab", "type": "PAGE", "name": "", "route": "/admin/users"})
        page["children"].append({"code": "system.user.x", "type": "WIDGET", "name": "X", "route": "/x"})
        page["children"].append({"type": "ACTION", "name": "Nameless"})
        self.manifest["resources"].append({"code": "lonely", "type": "ACTION", "name": "Lonely"})
        self.manifest["resources"].append({"code": "nowhere", "type": "PAGE", "name": "Nowhere"})
        self._save()
        errors = self._errors()
        for expected in ["'system.user.btn.edit' is declared twice",
                         "'other.thing' does not start with its parent's code 'system.user.'",
                         "'system.user.tab' is a PAGE below a PAGE", "'system.user.tab' has no name",
                         "'system.user.tab' and 'system.user' share the route /admin/users",
                         "'system.user.x' has unknown type 'WIDGET'", "'system.user.x' has a route but is not a page",
                         "an entry has no code", "'lonely' is a ACTION at the top level",
                         "page 'nowhere' has no route"]:
            self.assertIn(f"{chk.MANIFEST}: {expected}", errors)

    def test_reports_unknown_apis_and_requirements_and_cycles(self) -> None:
        page = self._page()
        edit, view = page["children"]
        edit["apis"].append("system.user.delete")
        edit["requires"].extend(["system.user.btn.missing", "system.user.btn.edit"])
        view["requires"] = ["system.user.btn.edit"]
        self._save()
        errors = self._errors()
        self.assertIn(f"{chk.MANIFEST}: 'system.user.btn.edit' needs 'system.user.delete', which no API of "
                      f"{chk.CONTRACT} declares", errors)
        self.assertIn(f"{chk.MANIFEST}: 'system.user.btn.edit' requires 'system.user.btn.missing', which is not in "
                      "the manifest", errors)
        self.assertIn(f"{chk.MANIFEST}: 'system.user.btn.edit' requires itself", errors)
        self.assertIn(f"{chk.MANIFEST}: 'requires' links form a cycle: system.user.btn.edit -> system.user.btn.view "
                      "-> system.user.btn.edit", errors)

    def test_every_permission_is_needed_or_listed_with_a_reason(self) -> None:
        contract = copy.deepcopy(CONTRACT)
        contract["paths"]["/api/v1/hooks"] = {"post": {"x-permission": "system.hook.call"}}
        self._write(chk.CONTRACT, json.dumps(contract))
        self.assertEqual(self._errors(), [f"{chk.CONTRACT}: no manifest entry needs 'system.hook.call'; add it to an "
                                          f"entry's 'apis' or list it in {chk.DIRECT_APIS} with a reason"])
        self._write(chk.DIRECT_APIS, "# header\nsystem.hook.call  # called by integrations\n")
        self.assertEqual(self._errors(), [])
        self._write(chk.DIRECT_APIS, "system.hook.call  # integrations\nsystem.user.read  # x\nsystem.gone  # y\n")
        self.assertEqual(self._errors(), [
            f"{chk.DIRECT_APIS}: 'system.gone' is not a permission of {chk.CONTRACT}",
            f"{chk.DIRECT_APIS}: 'system.user.read' is needed by a manifest entry; remove it from the list"])
        self._write(chk.DIRECT_APIS, "system.hook.call\n")
        self.assertTrue(any("must state a reason" in error for error in self._errors()))

    def test_page_routes_must_exist_in_the_router(self) -> None:
        self._write(chk.ROUTER, ROUTER.replace("admin/users", "admin/people"))
        self.assertEqual(self._errors(), [f"{chk.MANIFEST}: page 'system.user' has the route /admin/users, which "
                                          f"{chk.ROUTER} does not define"])

    def test_codes_quoted_in_the_console_must_exist(self) -> None:
        self._write(chk.SOURCES + "views/GroupsView.vue",
                    "<button v-permission=\"'system.group.btn.edit'\">e</button>\n"
                    "<p>{{ auth.holds(`system.user.gone`) }}</p>\n")
        self._write(chk.SOURCES + "api/schema.d.ts", "'system.anything.goes'\n")
        errors = self._errors()
        self.assertEqual(errors, [
            f"{chk.SOURCES}views/GroupsView.vue:1: 'system.group.btn.edit' is neither in the manifest nor a "
            "permission of the API",
            f"{chk.SOURCES}views/GroupsView.vue:2: 'system.user.gone' is neither in the manifest nor a permission of "
            "the API"])
        with mock.patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(chk.main(["--root", str(self.root)]), 1)

    def test_missing_or_broken_files_are_reported(self) -> None:
        self._write(chk.MANIFEST, "{ not json")
        self.assertTrue(self._errors()[0].startswith("cannot parse JSON"))
        (self.root / chk.ROUTER).unlink()
        self.assertEqual(self._errors(), [f"{chk.ROUTER}: missing"])


if __name__ == "__main__":
    unittest.main()
