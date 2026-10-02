#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Verify that every compiled class targets the project's minimum Java version.

A newer JDK compiling with the wrong ``--release`` silently produces class files
that fail to load on the minimum supported runtime. This check reads the class
file header of every ``.class`` under each Maven module's ``target/classes`` and
``target/test-classes`` and fails when a major version exceeds ``--max-major``
(52 = Java 8, 61 = Java 17) or when a module with sources has no build output.

``--module`` limits the check to some modules (repeatable) and ``--main-only`` to their main classes, for
modules held to an older release than the rest, such as the policy engine that agents embed (Java 8) while its
tests use Java 17.

Usage::

    python3 script/ci/check_java_bytecode.py --max-major 61
    python3 script/ci/check_java_bytecode.py --max-major 52 --module core/grantforge-policy-engine --main-only
"""

from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path
from typing import List, Optional, Sequence, Tuple
from xml.etree import ElementTree

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
_POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
_MAGIC = b"\xca\xfe\xba\xbe"
# Class file major version -> Java release, for readable messages.
_JAVA_BY_MAJOR = {52: "8", 55: "11", 61: "17", 65: "21", 69: "25"}


def java_name(major: int) -> str:
    return f"Java {_JAVA_BY_MAJOR.get(major, f'{major - 44}')}"


def class_major(header: bytes) -> Optional[int]:
    """Return the major version from the first 8 bytes of a class file, or None if invalid."""
    if len(header) < 8 or header[:4] != _MAGIC:
        return None
    return struct.unpack(">H", header[6:8])[0]


def maven_modules(root: Path) -> List[Path]:
    """Return module directories declared in the root pom (empty list if none)."""
    pom = ElementTree.parse(root / "pom.xml").getroot()
    return [root / (m.text or "").strip() for m in pom.findall("m:modules/m:module", _POM_NS) if (m.text or "").strip()]


def check(root: Path, max_major: int, only: Optional[Sequence[str]] = None,
          main_only: bool = False) -> Tuple[int, List[str]]:
    """Return (number of class files checked, error messages)."""
    errors: List[str] = []
    count = 0
    modules = maven_modules(root)
    if only:
        declared = {module.relative_to(root).as_posix(): module for module in modules}
        wanted = [name.strip("/") for name in only]
        errors.extend(f"not a module of pom.xml: {name}" for name in sorted(set(wanted) - set(declared)))
        modules = [declared[name] for name in wanted if name in declared]
    if not modules and not errors:
        errors.append("no Java modules declared in pom.xml")
    outputs = (("src/main/java", "target/classes"),) if main_only else (
        ("src/main/java", "target/classes"), ("src/test/java", "target/test-classes"))
    for module in modules:
        for source, output in outputs:
            if not any((module / source).rglob("*.java")):
                continue
            classes = sorted((module / output).rglob("*.class"))
            if not classes:
                errors.append(f"missing compiled classes: {(module / output).relative_to(root)}")
            for path in classes:
                with path.open("rb") as stream:
                    major = class_major(stream.read(8))
                count += 1
                if major is None:
                    errors.append(f"invalid class file header: {path.relative_to(root)}")
                elif major > max_major:
                    errors.append(f"{path.relative_to(root)}: compiled for {java_name(major)} (major {major}), "
                                  f"maximum is {java_name(max_major)} (major {max_major})")
    if count == 0 and not errors:
        errors.append("no compiled Java classes found; build the project first")
    return count, errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    parser.add_argument("--max-major", type=int, required=True, help="highest allowed class file major version")
    parser.add_argument("--module", action="append", default=[], help="only check this module (repeatable)")
    parser.add_argument("--main-only", action="store_true", help="only check main classes, not test classes")
    args = parser.parse_args(argv)

    count, errors = check(args.root.resolve(), args.max_major, args.module, args.main_only)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"checked {count} class files: all compatible with {java_name(args.max_major)} (major <= {args.max_major})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
