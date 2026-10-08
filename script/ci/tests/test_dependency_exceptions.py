# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Fail-closed compatibility exceptions, including real-shaped BOM/tree provenance."""

from __future__ import annotations

import datetime
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_dependencies as checker  # noqa: E402
import dependency_exceptions as exceptions  # noqa: E402

PURL = "pkg:maven/org.apache.hadoop/hadoop-common@2.7.7?type=jar"
GHSA = "GHSA-f5fw-25gw-5m92"
OTHER_GHSA = "GHSA-8wm5-8h9c-47pc"
TREE = "target/security-dependency-tree-0123456789abcdef0123456789abcdef.json"
AGENT = "agents/compat/pom.xml"


def rule():
    return {
        "source": "target/bom.json", "purl": PURL, "version": "2.7.7", "vulnerability": GHSA,
        "projects": [{"path": AGENT, "scopes": ["provided", "test"]}],
        "reason": "Only the pinned native ABI is used; Hadoop is provided by the target cluster.",
        "expires": "9999-12-31", "references": ["https://hadoop.apache.org/docs/r2.7.7/"],
    }


def node(scope="provided", name="hadoop-common", group="org.apache.hadoop", version="2.7.7", children=None):
    return {"groupId": group, "artifactId": name, "version": version, "type": "jar", "scope": scope,
            "children": children or []}


class SchemaTest(unittest.TestCase):
    def setUp(self):
        self.directory = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.directory, True)
        self.file = self.directory / "exceptions.json"

    def load(self, rows, today=None):
        self.file.write_text(json.dumps({"schema_version": 1, "exceptions": rows}), encoding="utf-8")
        return exceptions.load_exceptions(self.file, today)

    def test_exact_rule_and_empty_document(self):
        self.assertEqual(self.load([]), [])
        parsed = self.load([rule()])[0]
        self.assertEqual(parsed.purl, PURL)
        self.assertEqual(parsed.projects[0].scopes, frozenset({"provided", "test"}))

    def test_invalid_schema_and_broad_rules_fail_closed(self):
        invalid = [
            ("source", "target/**"), ("source", "docs/pnpm-lock.yaml"),
            ("purl", "pkg:maven/org.apache.hadoop/hadoop-common@*?type=jar"),
            ("purl", "pkg:maven/org.apache.hadoop/hadoop-common@2.7.7?other=jar"),
            ("purl", "pkg:maven/org.apache.hadoop/hadoop-common@2.7.7?other="),
            ("purl", "pkg:maven/org.apache.hadoop/hadoop-common@2.7.7?type=jar&type=jar"),
            ("purl", "pkg:maven/org.apache.hadoop/hadoop-common@[2.7,3.0)?type=jar"),
            ("version", "2.10.2"), ("vulnerability", "GHSA-*"), ("vulnerability", "CVE-2026-12345"),
            ("projects", []), ("projects", [{"path": "agents/**/pom.xml", "scopes": ["provided"]}]),
            ("projects", [{"path": "../outside/pom.xml", "scopes": ["provided"]}]),
            ("projects", [{"path": AGENT, "scopes": ["compile"]}]),
            ("projects", [{"path": AGENT, "scopes": ["runtime"]}]),
            ("projects", [{"path": AGENT, "scopes": ["provided", "provided"]}]),
            ("reason", " "), ("references", []), ("references", ["http://example.com/"]),
            ("expires", "2026-99-99"), ("expires", "20261008"),
        ]
        for key, value in invalid:
            with self.subTest(key=key, value=value):
                changed = rule()
                changed[key] = value
                with self.assertRaises(ValueError):
                    self.load([changed])
        changed = rule()
        changed["ignore_all"] = True
        with self.assertRaises(ValueError):
            self.load([changed])
        with self.assertRaises(ValueError):
            self.load([rule(), rule()])

    def test_expired_rules_are_errors_and_expiry_day_is_inclusive(self):
        changed = rule()
        changed["expires"] = "2026-10-08"
        self.assertEqual(len(self.load([changed], datetime.date(2026, 10, 8))), 1)
        with self.assertRaisesRegex(ValueError, "expired"):
            self.load([changed], datetime.date(2026, 10, 9))

    def test_missing_file_and_unknown_schema_are_errors(self):
        with self.assertRaises(ValueError):
            exceptions.load_exceptions(self.file)
        for version in (True, 2, "1"):
            self.file.write_text(json.dumps({"schema_version": version, "exceptions": []}), encoding="utf-8")
            with self.assertRaises(ValueError):
                exceptions.load_exceptions(self.file)


class ProvenanceFixture(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp()).resolve()
        self.addCleanup(shutil.rmtree, self.root, True)
        self.projects = {"pom.xml": "root", AGENT: "compat", "core/server/pom.xml": "server"}
        for source, artifact in self.projects.items():
            path = self.root / source
            path.parent.mkdir(parents=True, exist_ok=True)
            modules = ("<modules><module>agents/compat</module><module>core/server</module></modules>"
                       if source == "pom.xml" else "")
            path.write_text(f"<project><artifactId>{artifact}</artifactId>{modules}</project>", encoding="utf-8")
            self.write_tree(source, [node()] if source == AGENT else [])
        self.components = [{"group": "org.apache.hadoop", "name": "hadoop-common", "version": "2.7.7",
                            "purl": PURL, "scope": "required"}]
        self.write_bom()
        self.rules_file = self.root / "exceptions.json"
        self.rules_file.write_text(json.dumps({"schema_version": 1, "exceptions": [rule()]}), encoding="utf-8")
        self.parsed = exceptions.load_exceptions(self.rules_file)[0]

    def write_tree(self, source, dependencies, filename=TREE):
        path = (self.root / source).parent / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(node("", self.projects[source], "org.test", "1", dependencies)), encoding="utf-8")

    def write_bom(self):
        path = self.root / "target/bom.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"components": self.components}), encoding="utf-8")

    def load(self):
        return exceptions.load_provenance(self.root, TREE)


class ProvenanceTest(ProvenanceFixture):

    def test_bom_required_scope_does_not_override_provided_tree(self):
        proof = self.load()
        self.assertEqual(proof.purl("hadoop-common", "2.7.7"), PURL)
        self.assertEqual(proof.purl("org.apache.hadoop:hadoop-common", "2.7.7", PURL), PURL)
        self.assertTrue(proof.permits(self.parsed))

    def test_runtime_compile_unknown_or_new_project_cannot_be_excepted(self):
        for scope in ("compile", "runtime", "unknown", ""):
            with self.subTest(scope=scope):
                self.write_tree(AGENT, [node(scope)])
                self.assertFalse(self.load().permits(self.parsed))
        self.write_tree(AGENT, [node()])
        self.write_tree("core/server/pom.xml", [node("test")])
        self.assertFalse(self.load().permits(self.parsed))
        self.write_tree("core/server/pom.xml", [node("compile")])
        self.assertFalse(self.load().permits(self.parsed))

    def test_all_paths_within_one_project_must_have_allowed_scopes(self):
        self.write_tree(AGENT, [node(), node("runtime")])
        self.assertFalse(self.load().permits(self.parsed))

    def test_transitive_abi_scope_is_inherited_but_unknown_is_not_allowed(self):
        child = node("compile")
        parent = node("provided", "native-api", "org.example", "1", [child])
        self.write_tree(AGENT, [parent])
        self.assertTrue(self.load().permits(self.parsed))
        child["scope"] = "unexpected-scope"
        self.write_tree(AGENT, [parent])
        self.assertFalse(self.load().permits(self.parsed))
        parent["scope"] = "unknown"
        child["scope"] = "provided"
        self.write_tree(AGENT, [parent])
        self.assertFalse(self.load().permits(self.parsed))

    def test_every_reactor_tree_and_current_nonce_are_required(self):
        path = self.root / "core/server" / TREE
        path.unlink()
        self.write_tree("core/server/pom.xml", [],
                        "target/security-dependency-tree-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.json")
        with self.assertRaisesRegex(ValueError, "provenance"):
            self.load()
        for filename in (None, "target/security-dependency-tree.json", "../../tree.json"):
            with self.subTest(filename=filename), self.assertRaises(ValueError):
                exceptions.load_provenance(self.root, filename)

    def test_profile_modules_cannot_hide_runtime_occurrences(self):
        self.write_tree("core/server/pom.xml", [node("runtime")])
        for activation in ("<activation><activeByDefault>true</activeByDefault></activation>", ""):
            with self.subTest(activation=activation):
                (self.root / "pom.xml").write_text(
                    "<project><artifactId>root</artifactId>"
                    "<modules><module>agents/compat</module></modules>"
                    f"<profiles><profile><id>business</id>{activation}"
                    "<modules><module>core/server</module></modules></profile></profiles></project>",
                    encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "profile-defined reactor modules"):
                    self.load()

    def test_wrong_project_bom_or_missing_occurrence_cannot_supply_proof(self):
        self.write_tree(AGENT, [])
        self.assertFalse(self.load().permits(self.parsed))
        path = self.root / "agents/compat" / TREE
        path.write_text(json.dumps(node("", "server", "org.test", "1", [node()])), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "another Maven project"):
            self.load()
        self.write_tree(AGENT, [node()])
        self.components[0]["version"] = "3.5.0"
        self.write_bom()
        with self.assertRaisesRegex(ValueError, "identity"):
            self.load()

    def test_missing_bom_and_malformed_trees_fail_closed(self):
        (self.root / "target/bom.json").unlink()
        with self.assertRaises(ValueError):
            self.load()
        self.write_bom()
        (self.root / "agents/compat" / TREE).write_text("not JSON", encoding="utf-8")
        with self.assertRaises(ValueError):
            self.load()

    def test_bare_name_ambiguity_and_supplied_purl_mismatch_are_not_guessed(self):
        self.assertEqual(self.load().purl("hadoop-common", "2.7.7", "pkg:maven/other/hadoop-common@2.7.7"), "")
        self.components.append({"group": "other", "name": "hadoop-common", "version": "2.7.7",
                                "purl": "pkg:maven/other/hadoop-common@2.7.7?type=jar"})
        self.write_bom()
        self.assertEqual(self.load().purl("hadoop-common", "2.7.7"), "")


class MainExactExceptionTest(ProvenanceFixture):
    def check(self, ids=(GHSA,), name="hadoop-common", version="2.7.7", source="target/bom.json", metadata=True):
        legacy = self.root / "report-only.txt"
        legacy.write_text("", encoding="utf-8")
        data = {"results": [{"source": {"path": str(self.root / source)}, "packages": [{
            "package": {"name": name, "version": version, "ecosystem": "Maven"},
            "vulnerabilities": [{"id": identifier} for identifier in ids],
        }]}]}
        if metadata:
            data["_dependency_tree_path"] = TREE
        self.stdout, self.stderr = io.StringIO(), io.StringIO()
        with mock.patch("sys.stdout", self.stdout), mock.patch("sys.stderr", self.stderr):
            return checker.main(["--rules", str(legacy), "--exceptions", str(self.rules_file)], self.root,
                                lambda root: (1, data))

    def test_exact_compatibility_is_reported_and_allows_ci(self):
        self.assertEqual(self.check(), 0)
        self.assertIn(PURL, self.stdout.getvalue())
        self.assertIn(GHSA, self.stdout.getvalue())
        self.assertIn("expires", self.stdout.getvalue())

    def test_new_vulnerability_is_still_blocking_on_the_same_package(self):
        self.assertEqual(self.check((GHSA, OTHER_GHSA)), 1)
        self.assertIn(OTHER_GHSA, self.stderr.getvalue())
        self.assertNotIn(GHSA, self.stderr.getvalue())

    def test_other_version_package_or_source_remains_blocking(self):
        for arguments in ({"version": "2.10.2"}, {"name": "another-package"}, {"source": "release/bom.json"}):
            with self.subTest(arguments=arguments):
                self.assertEqual(self.check(**arguments), 1)

    def test_current_tree_metadata_and_every_project_are_required(self):
        self.assertEqual(self.check(metadata=False), 2)
        (self.root / "core/server" / TREE).unlink()
        self.assertEqual(self.check(), 2)

    def test_new_runtime_occurrence_blocks_without_changing_the_rule(self):
        self.write_tree("core/server/pom.xml", [node("runtime")])
        self.assertEqual(self.check(), 1)

    def test_expired_rule_is_configuration_failure_even_on_a_clean_scan(self):
        changed = rule()
        changed["expires"] = "2000-01-01"
        self.rules_file.write_text(json.dumps({"schema_version": 1, "exceptions": [changed]}), encoding="utf-8")
        self.assertEqual(self.check(), 2)


if __name__ == "__main__":
    unittest.main()
