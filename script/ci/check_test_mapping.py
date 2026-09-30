#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Require a unit test file for every production source file.

Mapping rules (paths are repository-relative):

* Java  ``<module>/src/main/java/<pkg>/Foo.java`` needs
  ``<module>/src/test/java/<pkg>/FooTest.java`` or ``FooIT.java``.
  ``package-info.java`` and ``module-info.java`` contain no behaviour and are skipped.
* Web   ``core/grantforge-web/src/**/Foo.ts`` or ``Foo.vue`` needs a sibling
  ``Foo.test.ts``. Declaration files (``*.d.ts``) and test files themselves are skipped.
* CI    ``script/ci/foo.py`` needs ``script/ci/tests/test_foo.py``.

Anything else must be listed in ``test_mapping_exclusions.txt`` with a reason.
``--report-only PREFIX`` lists missing tests below PREFIX without failing, which is
used while legacy code awaiting replacement still lives in the tree.

Usage::

    python3 script/ci/check_test_mapping.py [--report-only core/] [--root DIR]
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable, List, Optional, Sequence, Set, Tuple

# Allow "import cilib" when this file is loaded by path (tests) rather than executed directly.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from cilib import is_excluded, list_repository_files, load_exclusions  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_EXCLUSIONS = Path(__file__).resolve().parent / "test_mapping_exclusions.txt"

WEB_SOURCE_ROOT = "core/grantforge-web/src/"
CI_SCRIPT_ROOT = "script/ci/"

_JAVA_MAIN = re.compile(r"^(?P<module>.+)/src/main/java/(?P<path>.+)\.java$")
_JAVA_SKIPPED = frozenset({"package-info", "module-info"})
_WEB_SOURCE = re.compile(r"^(?P<stem>.+)\.(?:ts|vue)$")
_WEB_NOT_SOURCE = re.compile(r"\.(?:test|spec)\.ts$|\.d\.ts$")
_CI_SCRIPT = re.compile(r"^script/ci/(?P<name>[^/]+)\.py$")


@dataclass(frozen=True)
class Missing:
    """A production file whose expected test file(s) do not exist."""

    source: str
    expected: Tuple[str, ...]


def expected_tests(path: str) -> Optional[Tuple[str, ...]]:
    """Return the acceptable test paths for a source file, or None if it is not a mapped source.

    Any one of the returned paths satisfies the requirement.
    """
    java = _JAVA_MAIN.match(path)
    if java is not None:
        if java.group("path").rsplit("/", 1)[-1] in _JAVA_SKIPPED:
            return None
        base = f"{java.group('module')}/src/test/java/{java.group('path')}"
        return (f"{base}Test.java", f"{base}IT.java")
    if path.startswith(WEB_SOURCE_ROOT) and not _WEB_NOT_SOURCE.search(path):
        web = _WEB_SOURCE.match(path)
        if web is not None:
            return (f"{web.group('stem')}.test.ts",)
    ci = _CI_SCRIPT.match(path)
    if ci is not None:
        return (f"{CI_SCRIPT_ROOT}tests/test_{ci.group('name')}.py",)
    return None


def find_missing(paths: Iterable[str], excluded: Callable[[str], bool] = lambda _: False) -> List[Missing]:
    """Return every mapped, non-excluded source file that has none of its expected tests."""
    all_paths: Set[str] = set(paths)
    missing: List[Missing] = []
    for path in sorted(all_paths):
        expected = expected_tests(path)
        if expected is None or excluded(path):
            continue
        if not any(candidate in all_paths for candidate in expected):
            missing.append(Missing(path, expected))
    return missing


def split_report_only(missing: Sequence[Missing], prefixes: Sequence[str]) -> Tuple[List[Missing], List[Missing]]:
    """Split findings into (blocking, report-only) by path prefix."""
    blocking: List[Missing] = []
    reported: List[Missing] = []
    for item in missing:
        (reported if any(item.source.startswith(p) for p in prefixes) else blocking).append(item)
    return blocking, reported


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    parser.add_argument("--exclusions", type=Path, default=DEFAULT_EXCLUSIONS, help="exclusion rule file")
    parser.add_argument("--report-only", action="append", default=[], metavar="PREFIX",
                        help="list missing tests below PREFIX without failing (repeatable)")
    args = parser.parse_args(argv)

    try:
        rules = load_exclusions(args.exclusions)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    missing = find_missing(list_repository_files(args.root.resolve()), lambda p: is_excluded(p, rules))
    blocking, reported = split_report_only(missing, args.report_only)

    if reported:
        print(f"report only: {len(reported)} source file(s) below {', '.join(args.report_only)} lack tests")
        for item in reported:
            print(f"  {item.source}")
    for item in blocking:
        print(f"{item.source}: missing test, expected {' or '.join(item.expected)}", file=sys.stderr)
    if blocking:
        print(f"\n{len(blocking)} source file(s) without a unit test. Add the test or an exclusion with a reason "
              f"in {args.exclusions.name}.", file=sys.stderr)
        return 1
    print("test mapping ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
