# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Unit tests for script/ci/changed_areas.py (stdlib unittest, no dependencies)."""

from __future__ import annotations

import importlib.util
import io
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parents[1] / "changed_areas.py"
_SPEC = importlib.util.spec_from_file_location("changed_areas", _SCRIPT)
assert _SPEC is not None and _SPEC.loader is not None
areas = importlib.util.module_from_spec(_SPEC)
sys.modules["changed_areas"] = areas
_SPEC.loader.exec_module(areas)


def touched(*paths: str) -> set:
    return {name for name, value in areas.areas_of(list(paths)).items() if value}


class AreasTest(unittest.TestCase):
    def test_documentation_alone_builds_no_java(self) -> None:
        self.assertEqual(touched("docs/content/en/plugins/hive.md", "README.md", "README.zh-CN.md"), {"docs"})
        self.assertEqual(touched("agents/grantforge-agent-hive/README.md"), set())
        self.assertEqual(touched("docs/lib/navigation.ts"), {"docs", "code"})

    def test_java_changes_run_java_and_what_depends_on_it(self) -> None:
        server = touched("core/grantforge-identity/src/main/java/org/devlive/grantforge/identity/Account.java")
        self.assertTrue({"java", "database", "fullstack", "code", "performance", "docker"} <= server)
        self.assertFalse({"web", "docs", "plugin_api", "hive", "hdfs"} & server)
        plugin = touched("plugins/grantforge-plugin-hive/src/main/java/org/devlive/grantforge/hive/HiveProvider.java")
        self.assertEqual(plugin, {"java", "fullstack", "code", "hive", "docker"})
        agent = touched("agents/grantforge-agent-hdfs-3.4/pom.xml")
        self.assertEqual(agent, {"java", "code", "hdfs", "docker", "dependencies"})
        engine = touched("core/grantforge-policy-engine/src/main/java/Engine.java")
        self.assertTrue({"hdfs", "zookeeper", "mysql", "hive", "database"} <= engine)
        api = touched("core/grantforge-plugin-api/src/main/java/Api.java")
        self.assertIn("plugin_api", api)

    def test_the_console_reaches_java_only_through_its_contract_and_manifest(self) -> None:
        console = touched("core/grantforge-web/src/views/Users.vue")
        self.assertEqual(console, {"web", "fullstack", "code", "docker"})
        self.assertTrue({"java", "web", "docs"} <= touched("core/grantforge-web/src/api/openapi.json"))
        self.assertTrue({"java", "database", "web"} <= touched("core/grantforge-web/src/permissions/manifest.json"))
        self.assertIn("docs", touched("core/grantforge-common/src/main/java/org/x/CommonErrorCode.java"))

    def test_build_setup_touches_every_java_job(self) -> None:
        for path in ("pom.xml", ".mvn/wrapper/maven-wrapper.properties", "configure/checks.xml"):
            self.assertTrue({"java", "database", "fullstack", "hive", "performance"} <= touched(path), path)
        self.assertEqual(touched("script/ci/hive_integration.sh"), {"hive", "ci_scripts", "code"})
        self.assertEqual(touched("plugins/grantforge-plugin-kafka/src/main/java/org/devlive/grantforge/kafka/KafkaProvider.java"),
                         {"java", "fullstack", "code", "kafka", "docker"})
        self.assertEqual(touched("agents/grantforge-agent-kafka/src/main/java/org/devlive/grantforge/kafka/agent/AclView.java"),
                         {"java", "code", "kafka", "docker"})
        self.assertEqual(touched("plugins/grantforge-plugin-hbase/src/main/java/org/devlive/grantforge/hbase/HBaseProvider.java"),
                         {"java", "fullstack", "code", "hbase", "docker"})
        self.assertEqual(touched("agents/grantforge-agent-hbase/src/main/java/org/devlive/grantforge/hbase/agent/Policies.java"),
                         {"java", "code", "hbase", "docker"})
        self.assertEqual(touched("plugins/grantforge-plugin-trino/src/main/java/org/devlive/grantforge/trino/TrinoProvider.java"),
                         {"java", "fullstack", "code", "trino", "docker"})
        self.assertEqual(touched("script/ci/trino_integration.sh"), {"trino", "ci_scripts", "code"})
        self.assertTrue({"java", "web", "docs", "database", "fullstack"} <= touched(".github/workflows/ci.yml"))
        self.assertTrue({"hdfs", "hive", "performance", "docker"} <= touched(".github/workflows/nightly.yml"))

    def test_changing_the_decision_touches_everything(self) -> None:
        self.assertEqual(touched("script/ci/cilib.py"), set(areas.AREAS))
        self.assertEqual(touched("script/ci/changed_areas.py"), set(areas.AREAS))
        self.assertEqual(set(name for name, value in areas.areas_of(None).items() if value), set(areas.AREAS))
        self.assertEqual(touched(), set())

    def test_the_last_matching_rule_decides(self) -> None:
        rules = areas.compile_rules(["a/**", "!a/b/**", "a/b/keep.txt"])
        self.assertTrue(areas.in_area("a/x.txt", rules))
        self.assertFalse(areas.in_area("a/b/x.txt", rules))
        self.assertTrue(areas.in_area("a/b/keep.txt", rules))
        self.assertFalse(areas.in_area("c.txt", rules))


class RangeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self._git("init", "-q", "-b", "dev")
        self._git("config", "user.email", "ci@example.com")
        self._git("config", "user.name", "CI")
        self.first = self._commit("docs/a.md")

    def tearDown(self) -> None:
        shutil.rmtree(self.root)

    def _git(self, *arguments: str, env_date: str = "") -> str:
        env = dict(os.environ)
        if env_date:
            env.update(GIT_AUTHOR_DATE=env_date, GIT_COMMITTER_DATE=env_date)
        return subprocess.run(["git", *arguments], cwd=self.root, check=True, stdout=subprocess.PIPE, env=env) \
            .stdout.decode("utf-8").strip()

    def _commit(self, path: str, date: str = "") -> str:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(target.read_text() + "x" if target.exists() else "x", encoding="utf-8")
        self._git("add", path)
        self._git("commit", "-q", "-m", f"chore: touch {path}", env_date=date)
        return self._git("rev-parse", "HEAD")

    def _run(self, *arguments: str) -> dict:
        output = self.root / "out.txt"
        if output.exists():
            output.unlink()
        with redirect_stdout(io.StringIO()):
            self.assertEqual(areas.main([*arguments, "--root", str(self.root), "--output", str(output)]), 0)
        return dict(line.split("=") for line in output.read_text(encoding="utf-8").splitlines())

    def test_a_push_compares_with_the_previous_tip(self) -> None:
        head = self._commit("docs/b.md")
        result = self._run("--base", self.first, "--head", head)
        self.assertEqual(result["docs"], "true")
        self.assertEqual(result["java"], "false")
        head = self._commit("core/grantforge-audit/src/main/java/A.java")
        self.assertEqual(self._run("--base", self.first, "--head", head)["java"], "true")

    def test_a_branch_compares_with_where_it_left_the_default_branch(self) -> None:
        self._git("checkout", "-q", "-b", "topic")
        head = self._commit("docs/b.md")
        self._git("checkout", "-q", "dev")
        self._commit("core/grantforge-audit/src/main/java/A.java")
        # Three dots: what changed on dev since topic left does not count.
        result = self._run("--base", "dev", "--head", head)
        self.assertEqual((result["docs"], result["java"]), ("true", "false"))
        result = self._run("--base", "0" * 40, "--head", head, "--fallback", "dev")
        self.assertEqual((result["docs"], result["java"]), ("true", "false"))

    def test_without_a_range_every_area_runs(self) -> None:
        head = self._commit("docs/b.md")
        self.assertEqual(self._run("--base", "0" * 40, "--head", head)["java"], "true")
        self.assertEqual(self._run("--base", "1234567890" * 4, "--head", head)["java"], "true")
        self.assertEqual(self._run("--all")["java"], "true")

    def test_a_schedule_takes_the_recent_commits(self) -> None:
        shutil.rmtree(self.root)
        self.root = Path(tempfile.mkdtemp())
        self._git("init", "-q", "-b", "dev")
        self._git("config", "user.email", "ci@example.com")
        self._git("config", "user.name", "CI")
        self._commit("agents/grantforge-agent-hive/pom.xml", date="2020-01-01T00:00:00Z")
        self.assertEqual(self._run("--since", "2019-01-01")["hive"], "true", "no older commit: everything")
        self.assertEqual(self._run("--since", "2021-01-01")["hive"], "false", "nothing since")
        self._commit("docs/b.md", date="2022-01-01T00:00:00Z")
        result = self._run("--since", "2021-01-01")
        self.assertEqual((result["hive"], result["docs"]), ("false", "true"))


if __name__ == "__main__":
    unittest.main()
