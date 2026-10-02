#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Keep the console's permission manifest consistent with the server and the console.

The manifest (``core/grantforge-web/src/permissions/manifest.json``) declares the console's pages and buttons
and the API permissions each needs. The server creates its entries as built-in resources and derives grants
from it, so a drift between it, the API contract and the console silently breaks permissions. Checked:

1. Structure: every entry has a code, a known type and a name; codes are unique and start with their parent's
   code; types nest as the server allows (modules at the top, pages under modules or menus, buttons under pages
   or tabs); only pages have a route, every page has one, and routes are unique.
2. Every ``apis`` entry is a permission of the API contract (``x-permission`` of ``src/api/openapi.json``).
3. Every ``requires`` entry names another manifest entry, and the ``requires`` links have no cycle.
4. Every permission of the contract is needed by some manifest entry, or is listed with a reason in
   ``script/ci/permission_direct_apis.txt`` (APIs granted directly, such as integration APIs). Listed
   permissions must exist and must not be needed by an entry.
5. Every page route is a route of the console's router (``src/router/index.ts``).
6. Every resource or permission code the console's sources quote (such as ``'system.user.btn.edit'`` in a
   ``v-permission``) exists in the manifest or the contract.

Usage::

    python3 script/ci/check_permission_manifest.py [--root DIR]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Set

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cilib import list_repository_files, load_exclusions  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
WEB = "core/grantforge-web/"
MANIFEST = WEB + "src/permissions/manifest.json"
CONTRACT = WEB + "src/api/openapi.json"
ROUTER = WEB + "src/router/index.ts"
DIRECT_APIS = "script/ci/permission_direct_apis.txt"
# Sources whose quoted codes must exist; generated files, the manifest itself and tests are skipped.
SOURCES = WEB + "src/"
SKIPPED_SOURCES = (WEB + "src/api/", WEB + "src/permissions/", WEB + "src/i18n/")

# The parents each type may have, as the server's ResourceType.allowsParent; None is the top level.
PARENTS: Dict[str, Set[Optional[str]]] = {
    "MODULE": {None, "MODULE"},
    "MENU": {None, "MODULE", "MENU"},
    "PAGE": {None, "MODULE", "MENU"},
    "TAB": {"PAGE", "TAB"},
    "ACTION": {"PAGE", "TAB"},
}
NOT_PERMISSIONS = {"public", "authenticated"}
_QUOTED = re.compile(r"""['"`]([a-z][a-z0-9-]*(?:\.[a-z0-9-]+)+)['"`]""")
_ROUTE = re.compile(r"""\bpath:\s*'([^']*)'""")


def flatten(entries: Sequence[Dict[str, Any]], parent: Optional[Dict[str, Any]] = None) -> List[tuple]:
    """Return ``(entry, parent)`` pairs, parents before their children."""
    pairs: List[tuple] = []
    for entry in entries:
        pairs.append((entry, parent))
        pairs.extend(flatten(entry.get("children") or [], entry))
    return pairs


def contract_permissions(contract: Dict[str, Any]) -> Set[str]:
    """Return the permission codes the contract's operations declare."""
    found: Set[str] = set()
    for item in (contract.get("paths") or {}).values():
        for operation in item.values():
            if isinstance(operation, dict) and isinstance(operation.get("x-permission"), str):
                found.add(operation["x-permission"])
    return found - NOT_PERMISSIONS


def check_structure(pairs: Sequence[tuple]) -> List[str]:
    """Rule 1."""
    errors: List[str] = []
    seen: Set[str] = set()
    routes: Dict[str, str] = {}
    for entry, parent in pairs:
        code, kind = entry.get("code"), entry.get("type")
        if not isinstance(code, str) or not code:
            errors.append(f"{MANIFEST}: an entry has no code")
            continue
        if code in seen:
            errors.append(f"{MANIFEST}: '{code}' is declared twice")
        seen.add(code)
        if not entry.get("name"):
            errors.append(f"{MANIFEST}: '{code}' has no name")
        parent_type = parent.get("type") if parent else None
        if kind not in PARENTS:
            errors.append(f"{MANIFEST}: '{code}' has unknown type {kind!r}")
        elif parent_type not in PARENTS[kind]:
            where = f"below a {parent_type}" if parent_type else "at the top level"
            errors.append(f"{MANIFEST}: '{code}' is a {kind} {where}")
        if parent and not code.startswith(parent["code"] + "."):
            errors.append(f"{MANIFEST}: '{code}' does not start with its parent's code '{parent['code']}.'")
        route = entry.get("route")
        if kind == "PAGE" and not route:
            errors.append(f"{MANIFEST}: page '{code}' has no route")
        if route and kind != "PAGE":
            errors.append(f"{MANIFEST}: '{code}' has a route but is not a page")
        if route:
            if route in routes:
                errors.append(f"{MANIFEST}: '{code}' and '{routes[route]}' share the route {route}")
            routes.setdefault(route, code)
    return errors


def check_links(pairs: Sequence[tuple], permissions: Set[str]) -> List[str]:
    """Rules 2 and 3."""
    errors: List[str] = []
    codes = {entry.get("code") for entry, _ in pairs}
    requires: Dict[str, List[str]] = {}
    for entry, _ in pairs:
        code = entry.get("code")
        for api in entry.get("apis") or []:
            if api not in permissions:
                errors.append(f"{MANIFEST}: '{code}' needs '{api}', which no API of {CONTRACT} declares")
        for other in entry.get("requires") or []:
            if other == code:
                errors.append(f"{MANIFEST}: '{code}' requires itself")
            elif other not in codes:
                errors.append(f"{MANIFEST}: '{code}' requires '{other}', which is not in the manifest")
            else:
                requires.setdefault(code, []).append(other)
    cycle = find_cycle(requires)
    if cycle:
        errors.append(f"{MANIFEST}: 'requires' links form a cycle: {' -> '.join(cycle)}")
    return errors


def find_cycle(edges: Dict[str, List[str]]) -> Optional[List[str]]:
    """Return one cycle of the directed graph as a path that ends where it starts, or None."""
    state: Dict[str, int] = {}
    stack: List[str] = []

    def visit(node: str) -> Optional[List[str]]:
        state[node] = 1
        stack.append(node)
        for target in edges.get(node, []):
            if state.get(target) == 1:
                return [*stack[stack.index(target):], target]
            if state.get(target) is None:
                found = visit(target)
                if found:
                    return found
        stack.pop()
        state[node] = 2
        return None

    for node in sorted(edges):
        if state.get(node) is None:
            found = visit(node)
            if found:
                return found
    return None


def check_coverage(root: Path, pairs: Sequence[tuple], permissions: Set[str]) -> List[str]:
    """Rule 4."""
    errors: List[str] = []
    needed = {api for entry, _ in pairs for api in entry.get("apis") or []}
    try:
        direct = {rule.pattern: rule.reason for rule in load_exclusions(root / DIRECT_APIS)}
    except ValueError as error:
        return [str(error)]
    for permission in sorted(permissions - needed - set(direct)):
        errors.append(f"{CONTRACT}: no manifest entry needs '{permission}'; add it to an entry's 'apis' or list it "
                      f"in {DIRECT_APIS} with a reason")
    for permission in sorted(direct):
        if permission not in permissions:
            errors.append(f"{DIRECT_APIS}: '{permission}' is not a permission of {CONTRACT}")
        elif permission in needed:
            errors.append(f"{DIRECT_APIS}: '{permission}' is needed by a manifest entry; remove it from the list")
    return errors


def check_routes(root: Path, pairs: Sequence[tuple]) -> List[str]:
    """Rule 5."""
    router = (root / ROUTER).read_text(encoding="utf-8")
    known = {path if path.startswith("/") else "/" + path for path in _ROUTE.findall(router)}
    return [f"{MANIFEST}: page '{entry['code']}' has the route {entry['route']}, which {ROUTER} does not define"
            for entry, _ in pairs if entry.get("route") and entry["route"] not in known]


def check_sources(root: Path, paths: Sequence[str], pairs: Sequence[tuple], permissions: Set[str]) -> List[str]:
    """Rule 6: quoted codes starting with a top-level module code must exist."""
    errors: List[str] = []
    modules = {entry["code"] for entry, parent in pairs if parent is None and entry.get("code")}
    known = {entry.get("code") for entry, _ in pairs} | permissions
    sources = [p for p in paths if p.startswith(SOURCES) and p.endswith((".ts", ".vue"))
               and not p.startswith(SKIPPED_SOURCES) and ".test." not in p and not p.endswith(".d.ts")]
    for path in sorted(sources):
        for number, line in enumerate((root / path).read_text(encoding="utf-8").splitlines(), start=1):
            for code in _QUOTED.findall(line):
                if code.split(".", 1)[0] in modules and code not in known:
                    errors.append(f"{path}:{number}: '{code}' is neither in the manifest nor a permission of the API")
    return errors


def check(root: Path, paths: Sequence[str]) -> List[str]:
    """Run every rule; a missing manifest or contract is an error of its own."""
    missing = [p for p in (MANIFEST, CONTRACT, ROUTER) if not (root / p).is_file()]
    if missing:
        return [f"{p}: missing" for p in missing]
    try:
        manifest = json.loads((root / MANIFEST).read_text(encoding="utf-8"))
        contract = json.loads((root / CONTRACT).read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        return [f"cannot parse JSON: {error}"]
    pairs = flatten(manifest.get("resources") or [])
    permissions = contract_permissions(contract)
    return (check_structure(pairs) + check_links(pairs, permissions) + check_coverage(root, pairs, permissions)
            + check_routes(root, pairs) + check_sources(root, paths, pairs, permissions))


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    errors = check(root, list_repository_files(root))
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("permission manifest ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
