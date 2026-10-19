#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Tell the workflows which areas of the repository a change touches, so they run only the jobs it needs.

Each area is an ordered list of globs over repository paths; the last matching glob decides, and a glob
starting with ``!`` takes a path out again (``core/**`` then ``!core/grantforge-web/**``). An area is touched when
one changed path is in it. A change to this script or to cilib.py touches every area, as does a run without a
usable range (a new branch, a force push without a fallback, ``--all``).

The range is ``--base...--head`` (the changes on head since the two diverged), or the commits of head made
within ``--since`` for scheduled runs. The answer is written as ``<area>=true|false`` lines to ``--output``
(``$GITHUB_OUTPUT`` in a workflow) and printed.

Usage::

    python3 script/ci/changed_areas.py --base <sha> --head <sha> [--fallback origin/dev] [--output FILE]
    python3 script/ci/changed_areas.py --since "25 hours ago" [--output FILE]
    python3 script/ci/changed_areas.py --all [--output FILE]
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cilib import glob_to_regex, resolve_base  # noqa: E402

# What the Maven build reads besides the modules: the reactor, the wrapper and the checkstyle, PMD and assembly setup.
_MAVEN = ["pom.xml", ".mvn/**", "mvnw", "mvnw.cmd", "configure/**"]
# What every plugin and agent builds on.
_PLATFORM = ["core/grantforge-plugin-api/**", "core/grantforge-plugin-host/**", "core/grantforge-policy-engine/**",
             "core/grantforge-agent-core/**", "core/grantforge-common/**"]
# The server's tests read the console's OpenAPI contract and permission manifest; nothing else of the console.
_SERVER = ["core/**", "!core/grantforge-web/**", "core/grantforge-web/src/api/openapi.json",
           "core/grantforge-web/src/permissions/**"]
# Prose never changes what a build or a test does; docs/ has its own area.
_PROSE = ["!**/*.md"]
_CI = [".github/workflows/ci.yml"]
_NIGHTLY = [".github/workflows/nightly.yml"]
_SECURITY = [".github/workflows/security.yml"]


def _integration(name: str, script: str) -> List[str]:
    modules = [f"plugins/grantforge-plugin-{name}/**", f"agents/grantforge-agent-{name}*/**"]
    return _MAVEN + _PLATFORM + modules + _PROSE + [f"script/ci/{script}"] + _NIGHTLY


AREAS: Dict[str, List[str]] = {
    # CI
    "java": _MAVEN + _SERVER + ["plugins/**", "agents/**", "sdk/grantforge-spring-boot-starter/**"] + _PROSE
    + ["script/ci/java.sh", "script/ci/check_java_bytecode.py", "script/ci/check_coverage.py",
       "script/ci/coverage_thresholds.txt"] + _CI,
    "web": ["core/grantforge-web/**"] + _PROSE + ["script/ci/web.sh"] + _CI,
    "sdk_js": ["sdk/grantforge-js/**"] + _PROSE + ["script/ci/sdk_js.sh"] + _CI,
    "docs": ["docs/**", "core/grantforge-web/src/api/openapi.json", "core/**/src/main/java/**/*ErrorCode.java",
             "core/**/src/main/resources/i18n/*.properties", "script/ci/docs.sh"] + _CI,
    "database": _MAVEN + _SERVER + _PROSE + ["script/ci/db_integration.sh", "script/ci/check_migrations.py"] + _CI,
    "plugin_api": ["pom.xml", "core/grantforge-plugin-api/**"] + _PROSE + ["script/ci/check_plugin_api_compat.py"]
    + _CI,
    "fullstack": _MAVEN + ["core/**", "plugins/**"] + _PROSE + ["script/ci/e2e_fullstack.sh"] + _CI,
    "ci_scripts": ["script/**", ".github/workflows/**"],
    # Security
    "dependencies": ["**/pom.xml", "**/package.json", "**/pnpm-lock.yaml", "script/ci/check_dependencies.py",
                     "script/ci/dependency_*", "script/ci/ci_tools.py", ".github/dependency-review-config.yml"]
    + _SECURITY,
    "code": ["**", "!docs/content/**"] + _PROSE,
    # Nightly
    "hdfs": _integration("hdfs", "hdfs_integration.sh"),
    "zookeeper": _integration("zookeeper", "zookeeper_integration.sh"),
    "mysql": _integration("mysql", "mysql_integration.sh"),
    "hive": _integration("hive", "hive_integration.sh"),
    "kafka": _integration("kafka", "kafka_integration.sh"),
    "hbase": _integration("hbase", "hbase_integration.sh"),
    "trino": _integration("trino", "trino_integration.sh"),
    "performance": _MAVEN + _SERVER + ["perf/**"] + _PROSE + ["script/ci/perf_benchmark.sh"] + _NIGHTLY,
    "docker": _MAVEN + ["core/**", "plugins/**", "agents/**", "deploy/**"] + _PROSE + ["script/ci/docker_smoke.sh"]
    + _NIGHTLY,
}

# Changing how areas are decided may change any decision.
_EVERYTHING = [glob_to_regex(glob) for glob in ("script/ci/changed_areas.py", "script/ci/cilib.py")]

Rule = Tuple[bool, "re.Pattern[str]"]


def compile_rules(globs: Sequence[str]) -> List[Rule]:
    """Compile an area's globs into (include, regex) rules, in order."""
    return [(not glob.startswith("!"), glob_to_regex(glob.lstrip("!"))) for glob in globs]


def in_area(path: str, rules: Sequence[Rule]) -> bool:
    """Return True when the last rule matching ``path`` includes it."""
    selected = False
    for include, regex in rules:
        if regex.fullmatch(path):
            selected = include
    return selected


def areas_of(paths: Optional[Sequence[str]], areas: Optional[Dict[str, List[str]]] = None) -> Dict[str, bool]:
    """Return, for every area, whether one of ``paths`` is in it; ``None`` (an unknown change) touches all."""
    areas = AREAS if areas is None else areas
    if paths is None or any(regex.fullmatch(path) for path in paths for regex in _EVERYTHING):
        return {name: True for name in areas}
    compiled = {name: compile_rules(globs) for name, globs in areas.items()}
    return {name: any(in_area(path, rules) for path in paths) for name, rules in compiled.items()}


def _git(root: Path, *arguments: str) -> Optional[str]:
    result = subprocess.run(["git", *arguments], cwd=root, check=False, stdout=subprocess.PIPE,
                            stderr=subprocess.DEVNULL)
    return result.stdout.decode("utf-8") if result.returncode == 0 else None


def changed_paths(root: Path, base: str, head: str) -> Optional[List[str]]:
    """Return the paths changed on ``head`` since it diverged from ``base``, both sides of a rename included."""
    output = _git(root, "diff", "--name-only", "--no-renames", "-z", f"{base}...{head}")
    if output is None:
        return None
    return sorted({path for path in output.split("\0") if path})


def base_before(root: Path, since: str, head: str) -> Optional[str]:
    """Return the last commit of ``head`` made before ``since``, or None when head has no older commit."""
    output = _git(root, "rev-list", "-1", f"--before={since}", head)
    return output.strip() if output and output.strip() else None


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="", help="range start: the PR base or the push's 'before'")
    parser.add_argument("--head", default="HEAD", help="range end")
    parser.add_argument("--fallback", default="", help="branch to compare with when --base is gone, such as origin/dev")
    parser.add_argument("--since", default="", help="take the commits of --head since then, such as '25 hours ago'")
    parser.add_argument("--all", action="store_true", help="touch every area")
    parser.add_argument("--output", type=Path, help="file to append <area>=true|false lines to, such as $GITHUB_OUTPUT")
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root")
    args = parser.parse_args(argv)

    paths: Optional[List[str]] = None
    if args.all:
        print("every area: asked for all")
    elif args.since:
        base = base_before(args.root, args.since, args.head)
        if base is None:
            print(f"every area: {args.head} has no commit before {args.since}")
        else:
            paths = changed_paths(args.root, base, args.head)
    else:
        base = resolve_base(args.root, args.base, args.head, args.fallback or None)
        if base is None and args.fallback:
            # A new branch: take what it changed since it left the default branch.
            base = args.fallback if _git(args.root, "rev-parse", "--verify", "--quiet", args.fallback) else None
        if base is None:
            print("every area: no range to compare")
        else:
            paths = changed_paths(args.root, base, args.head)
    if paths is not None:
        print(f"{len(paths)} changed path(s)")

    touched = areas_of(paths)
    lines = [f"{name}={'true' if value else 'false'}" for name, value in touched.items()]
    for line in lines:
        print(line)
    if args.output is not None:
        with args.output.open("a", encoding="utf-8") as output:
            output.write("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
