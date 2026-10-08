#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Exact, expiring compatibility exceptions backed by resolved Maven provenance.

Rules match one source, Maven purl, version and GHSA identifier. Every resolved
occurrence must belong to an explicitly listed project and provided/test scope.
The aggregate BOM identifies packages; per-project dependency trees prove scope.
Neither package names alone nor an aggregate BOM's component scope is sufficient.
"""

from __future__ import annotations

import datetime
import json
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Dict, FrozenSet, List, Optional, Set, Tuple
from urllib.parse import parse_qsl, unquote, urlsplit

SOURCE = "target/bom.json"
TREE_PATTERN = re.compile(r"target/security-dependency-tree-[0-9a-f]{32}\.json\Z")
_SCOPES = frozenset({"provided", "test"})
_GHSA = re.compile(r"GHSA-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}\Z")
_VERSION = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]*\Z")
_MAVEN_NAME = re.compile(r"[A-Za-z0-9_.-]+\Z")
Coordinates = Tuple[str, str, str, str, str]


@dataclass(frozen=True)
class ProjectScope:
    path: str
    scopes: FrozenSet[str]


@dataclass(frozen=True)
class DependencyException:
    source: str
    purl: str
    version: str
    vulnerability: str
    projects: Tuple[ProjectScope, ...]
    reason: str
    expires: str
    references: Tuple[str, ...]


@dataclass(frozen=True)
class Occurrence:
    project: str
    scope: str


@dataclass
class Provenance:
    packages: Dict[Tuple[str, str], Set[str]]
    occurrences: Dict[Coordinates, Set[Occurrence]]
    projects: Set[str]

    def purl(self, name: str, version: str, supplied: str = "") -> str:
        """Resolve only an unambiguous BOM package, checking a supplied purl too."""
        candidates = self.packages.get((name, version), set())
        if len(candidates) != 1:
            return ""
        actual = next(iter(candidates))
        return actual if not supplied or supplied == actual else ""

    def permits(self, rule: DependencyException) -> bool:
        approved = {project.path: project.scopes for project in rule.projects}
        if not set(approved).issubset(self.projects):
            return False
        occurrences = self.occurrences.get(maven_coordinates(rule.purl), set())
        return bool(occurrences) and all(
            item.project in approved and item.scope in approved[item.project]
            for item in occurrences
        )


def _object(value, keys: Set[str], label: str) -> Dict:
    if not isinstance(value, dict) or set(value) != keys:
        raise ValueError(f"{label} must contain exactly {', '.join(sorted(keys))}")
    return value


def _text(value, label: str) -> str:
    if not isinstance(value, str) or not value.strip() or value != value.strip():
        raise ValueError(f"{label} must be a nonempty string without surrounding whitespace")
    return value


def maven_coordinates(purl: str) -> Coordinates:
    """Read exact Maven coordinates; reject wildcards, ranges and unknown qualifiers."""
    path, _, query = purl.partition("?")
    if not path.startswith("pkg:maven/") or "@" not in path:
        raise ValueError(f"not an exact Maven purl: {purl}")
    package, version = path[len("pkg:maven/"):].rsplit("@", 1)
    parts = package.split("/")
    if len(parts) != 2:
        raise ValueError(f"not an exact Maven purl: {purl}")
    group, name = (unquote(part) for part in parts)
    version = unquote(version)
    if not _MAVEN_NAME.fullmatch(group) or not _MAVEN_NAME.fullmatch(name) or not _VERSION.fullmatch(version):
        raise ValueError(f"not an exact Maven purl: {purl}")
    pairs = parse_qsl(query, keep_blank_values=True, strict_parsing=True) if query else []
    qualifiers = dict(pairs)
    if len(qualifiers) != len(pairs) or not set(qualifiers).issubset({"type", "classifier"}):
        raise ValueError(f"unsupported Maven purl qualifiers: {purl}")
    artifact_type = qualifiers.get("type", "jar")
    classifier = qualifiers.get("classifier", "")
    if not _MAVEN_NAME.fullmatch(artifact_type) or (classifier and not _MAVEN_NAME.fullmatch(classifier)):
        raise ValueError(f"not an exact Maven purl: {purl}")
    return group, name, version, artifact_type, classifier


def load_exceptions(path: Path, today: Optional[datetime.date] = None) -> List[DependencyException]:
    """Validate all rules before scanning; malformed or expired rules fail closed."""
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise ValueError(f"cannot read dependency exceptions {path}: {error}") from error
    _object(document, {"schema_version", "exceptions"}, "exception document")
    if type(document["schema_version"]) is not int or document["schema_version"] != 1:
        raise ValueError("dependency exceptions require schema_version 1")
    if not isinstance(document["exceptions"], list):
        raise ValueError("exceptions must be an array")
    result: List[DependencyException] = []
    seen = set()
    for index, value in enumerate(document["exceptions"]):
        label = f"exception {index + 1}"
        _object(value, {"source", "purl", "version", "vulnerability", "projects", "reason", "expires", "references"},
                label)
        strings = {key: _text(value[key], f"{label}.{key}") for key in
                   ("source", "purl", "version", "vulnerability", "reason", "expires")}
        if strings["source"] != SOURCE:
            raise ValueError(f"{label}.source must be exactly {SOURCE}")
        if maven_coordinates(strings["purl"])[2] != strings["version"]:
            raise ValueError(f"{label}.version must equal the purl version")
        if not _GHSA.fullmatch(strings["vulnerability"]):
            raise ValueError(f"{label}.vulnerability must be one exact GHSA identifier")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", strings["expires"]):
            raise ValueError(f"{label}.expires must be YYYY-MM-DD")
        expiry = datetime.date.fromisoformat(strings["expires"])
        if expiry < (today or datetime.date.today()):
            raise ValueError(f"{label} expired on {expiry}")
        if not isinstance(value["projects"], list) or not value["projects"]:
            raise ValueError(f"{label}.projects must be a nonempty array")
        projects = []
        project_names = set()
        for project in value["projects"]:
            _object(project, {"path", "scopes"}, f"{label}.project")
            name = _text(project["path"], f"{label}.project.path")
            relative = PurePosixPath(name)
            if (relative.is_absolute() or relative.as_posix() != name or ".." in relative.parts
                    or relative.name != "pom.xml" or any(char in name for char in "*?[]\\")):
                raise ValueError(f"{label}.project.path must be an exact repository-relative pom.xml")
            scopes = project["scopes"]
            if (not isinstance(scopes, list) or not scopes or any(not isinstance(scope, str) for scope in scopes)
                    or len(set(scopes)) != len(scopes) or not set(scopes).issubset(_SCOPES)):
                raise ValueError(f"{label}.project.scopes may contain only provided and test")
            if name in project_names:
                raise ValueError(f"duplicate project in {label}: {name}")
            project_names.add(name)
            projects.append(ProjectScope(name, frozenset(scopes)))
        references = value["references"]
        if not isinstance(references, list) or not references:
            raise ValueError(f"{label}.references must be a nonempty array")
        for reference in references:
            url = urlsplit(_text(reference, f"{label}.reference"))
            if url.scheme != "https" or not url.netloc:
                raise ValueError(f"{label}.references must be HTTPS URLs")
        key = (strings["source"], strings["purl"], strings["version"], strings["vulnerability"])
        if key in seen:
            raise ValueError(f"duplicate dependency exception: {key}")
        seen.add(key)
        result.append(DependencyException(**strings, projects=tuple(projects), references=tuple(references)))
    return result


def reactor_projects(root: Path) -> Dict[str, Path]:
    """Enumerate the default reactor used by the scanner, including aggregators."""
    projects: Dict[str, Path] = {}
    pending = [root / "pom.xml"]
    while pending:
        pom = pending.pop().resolve()
        try:
            source = pom.relative_to(root.resolve()).as_posix()
            project = ET.parse(pom).getroot()
        except (OSError, ValueError, ET.ParseError) as error:
            raise ValueError(f"cannot identify Maven reactor project {pom}: {error}") from error
        if source in projects:
            raise ValueError(f"duplicate or cyclic Maven reactor project: {source}")
        if project.find("{*}profiles/{*}profile/{*}modules") is not None:
            raise ValueError(f"profile-defined reactor modules need effective Maven provenance: {source}")
        projects[source] = pom
        for module in project.findall("{*}modules/{*}module"):
            name = (module.text or "").strip()
            if not name or "${" in name:
                raise ValueError(f"unresolved Maven reactor module in {source}")
            pending.append(pom.parent / name / "pom.xml")
    return projects


def _json(path: Path) -> Dict:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise ValueError(f"cannot read dependency provenance {path}: {error}") from error
    if not isinstance(data, dict):
        raise ValueError(f"dependency provenance must be an object: {path}")
    return data


def load_provenance(root: Path, tree_name: str) -> Provenance:
    """Require BOM identity and complete, freshly generated per-project JSON trees."""
    if not isinstance(tree_name, str) or not TREE_PATTERN.fullmatch(tree_name):
        raise ValueError("dependency provenance must use the current scan's unique tree filename")
    packages: Dict[Tuple[str, str], Set[str]] = {}
    bom = _json(root / SOURCE)
    components = bom.get("components")
    if not isinstance(components, list):
        raise ValueError("aggregate BOM must contain components")
    for component in components:
        if not isinstance(component, dict):
            raise ValueError("aggregate BOM components must be objects")
        purl = component.get("purl", "")
        if not isinstance(purl, str) or not purl.startswith("pkg:maven/"):
            continue
        group, name, version, _, _ = maven_coordinates(purl)
        if (component.get("name") != name or component.get("version") != version
                or component.get("group", group) != group):
            raise ValueError(f"aggregate BOM identity disagrees with purl: {purl}")
        for identifier in (name, f"{group}:{name}"):
            packages.setdefault((identifier, version), set()).add(purl)
    projects = reactor_projects(root)
    occurrences: Dict[Coordinates, Set[Occurrence]] = {}
    for source, pom in projects.items():
        tree_path = pom.parent / tree_name
        tree = _json(tree_path)
        project = ET.parse(pom).getroot()
        if tree.get("artifactId") != project.findtext("{*}artifactId"):
            raise ValueError(f"dependency tree belongs to another Maven project: {source}")

        def visit(node: Dict, inherited: str, dependency: bool, source: str) -> None:
            if not isinstance(node, dict):
                raise ValueError(f"dependency tree nodes must be objects: {source}")
            scope = node.get("scope", "unknown")
            if not isinstance(scope, str) or scope not in {"compile", "runtime", "provided", "test"}:
                scope = "unknown"
            if dependency:
                fields = [node.get(field) for field in ("groupId", "artifactId", "version")]
                if not all(isinstance(field, str) and field for field in fields):
                    raise ValueError(f"dependency tree is missing Maven coordinates: {source}")
                artifact_type = node.get("type", "jar")
                classifier = node.get("classifier", "")
                if artifact_type == "test-jar":
                    artifact_type, classifier = "jar", classifier or "tests"
                if not isinstance(artifact_type, str) or not isinstance(classifier, str):
                    raise ValueError(f"dependency tree has invalid Maven qualifiers: {source}")
                if inherited == "unknown":
                    scope = "unknown"
                elif scope != "unknown" and inherited in _SCOPES:
                    scope = inherited
                elif inherited == "runtime" and scope == "compile":
                    scope = "runtime"
                coordinates = (*fields, artifact_type, classifier)
                occurrences.setdefault(coordinates, set()).add(Occurrence(source, scope))
            children = node.get("children", [])
            if not isinstance(children, list):
                raise ValueError(f"dependency tree children must be an array: {source}")
            for child in children:
                visit(child, scope if dependency else "", True, source)

        visit(tree, "", False, source)
    return Provenance(packages, occurrences, set(projects))
