#!/usr/bin/env python3
"""Reject missing build outputs and class files newer than Java 8."""

import argparse
import struct
import sys
from pathlib import Path
from xml.etree import ElementTree


def check(root):
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ElementTree.parse(root / "pom.xml").getroot()
    modules = pom.findall("m:modules/m:module", namespace)
    errors = []
    count = 0
    if not modules:
        errors.append("No Java modules declared in pom.xml")
    for module in modules:
        directory = root / module.text
        for source, output in (
            ("src/main/java", "target/classes"),
            ("src/test/java", "target/test-classes"),
        ):
            if not any((directory / source).rglob("*.java")):
                continue
            classes = list((directory / output).rglob("*.class"))
            if not classes:
                errors.append("Missing compiled classes: " + str(directory / output))
            for file in classes:
                with file.open("rb") as stream:
                    header = stream.read(8)
                if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                    errors.append("Invalid class header: " + str(file))
                    continue
                major = struct.unpack(">H", header[6:8])[0]
                if major > 52:
                    errors.append("Java 8 requires class version <= 52: {} ({})".format(file, major))
                count += 1
    if count == 0 and not errors:
        errors.append("No compiled Java classes found")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Checked {} class files: all compatible with Java 8 (major <= 52).".format(count))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    arguments = parser.parse_args()
    return check(arguments.root.resolve())


if __name__ == "__main__":
    sys.exit(main())
