#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Keep translations complete and in use.

Web console (core/grantforge-web/src):

1. No CJK text outside the message dictionaries (``src/i18n/locales``) and tests; comments are ignored.
2. Every key of the reference dictionary (``zh-CN.ts``) is referenced somewhere as a quoted literal,
   so dead messages do not accumulate. (Key parity between locales is enforced by the TypeScript type
   of the dictionaries and their unit tests.)

Server (``**/src/main/resources/i18n``; each module ships its own bundle such as ``identity.properties``):

3. Every ``<bundle>_<locale>.properties`` defines exactly the keys of ``<bundle>.properties``, no message
   is empty, and no key is defined by two bundles.

Usage::

    python3 script/ci/check_i18n_keys.py [--root DIR]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cilib import list_repository_files  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
WEB_SOURCE = "core/grantforge-web/src/"
LOCALES = WEB_SOURCE + "i18n/locales/"
REFERENCE_DICTIONARY = LOCALES + "zh-CN.ts"

_CJK = re.compile(r"[぀-ヿ㐀-䶿一-鿿가-힯！-～]")
_COMMENTS = re.compile(r"<!--.*?-->|/\*.*?\*/|(?<![:'\"`])//[^\n]*", re.DOTALL)
_TOKEN = re.compile(r"\s*(?:(?P<name>[A-Za-z_$][\w$]*)\s*:|(?P<open>\{)|(?P<close>\})|(?P<string>'(?:[^'\\]|\\.)*')"
                    r"|(?P<comma>,))")
_BUNDLE = re.compile(r"(?:^|/)src/main/resources/i18n/([a-z][a-z0-9-]*)\.properties$")
_PROPERTY = re.compile(r"^\s*([^#!\s][^=:\s]*)\s*[=:]\s*(.*)$")


def dictionary_keys(source: str) -> List[str]:
    """Return the dotted keys of the first object literal assigned in a dictionary module.

    Supports the layout the dictionaries use: nested objects (also inline) with single-quoted string
    values. Anything else raises ``ValueError`` so an unsupported edit is noticed instead of ignored.
    """
    start = source.find("= {")
    if start < 0:
        raise ValueError("no object literal found")
    position = start + 2
    path: List[str] = []
    pending: Optional[str] = None
    keys: List[str] = []
    depth = 0
    while True:
        match = _TOKEN.match(source, position)
        if match is None:
            raise ValueError(f"unexpected content at offset {position}: {source[position:position + 30]!r}")
        position = match.end()
        if match.group("name"):
            pending = match.group("name")
        elif match.group("open"):
            depth += 1
            if depth > 1:
                if pending is None:
                    raise ValueError("object without a key")
                path.append(pending)
                pending = None
        elif match.group("close"):
            depth -= 1
            if depth == 0:
                return keys
            path.pop()
        elif match.group("string"):
            if pending is None:
                raise ValueError("string value without a key")
            keys.append(".".join([*path, pending]))
            pending = None


def strip_comments(text: str) -> str:
    return _COMMENTS.sub("", text)


def check_web(root: Path, paths: Sequence[str]) -> List[str]:
    """Rules 1 and 2 for the web console."""
    errors: List[str] = []
    sources = [p for p in paths if p.startswith(WEB_SOURCE) and p.endswith((".ts", ".vue"))
               and not p.startswith(LOCALES) and ".test." not in p and not p.endswith(".d.ts")]
    code: Dict[str, str] = {p: (root / p).read_text(encoding="utf-8") for p in sources}
    for path, text in code.items():
        for number, line in enumerate(strip_comments(text).splitlines(), start=1):
            if _CJK.search(line):
                errors.append(f"{path}:{number}: hard-coded text; move it to the i18n dictionaries")
                break
    if REFERENCE_DICTIONARY not in paths:
        return errors
    try:
        keys = dictionary_keys((root / REFERENCE_DICTIONARY).read_text(encoding="utf-8"))
    except ValueError as error:
        return [*errors, f"{REFERENCE_DICTIONARY}: cannot parse ({error})"]
    everything = "\n".join(code.values())
    for key in keys:
        if f"'{key}'" not in everything and f'"{key}"' not in everything:
            errors.append(f"{REFERENCE_DICTIONARY}: message '{key}' is never used")
    return errors


def read_properties(text: str) -> Dict[str, str]:
    """Parse simple key=value properties (no line continuations), ignoring comments and blank lines."""
    values: Dict[str, str] = {}
    for line in text.splitlines():
        match = _PROPERTY.match(line)
        if match is not None:
            values[match.group(1)] = match.group(2).strip()
    return values


def check_server(root: Path, paths: Sequence[str]) -> List[str]:
    """Rule 3 for every server message bundle."""
    errors: List[str] = []
    owners: Dict[str, str] = {}
    for base in sorted(p for p in paths if _BUNDLE.search(p)):
        directory, file_name = base.rsplit("/", 1)
        name = file_name[:-len(".properties")]
        reference = read_properties((root / base).read_text(encoding="utf-8"))
        for key, value in reference.items():
            if not value:
                errors.append(f"{base}: message '{key}' is empty")
            owner = owners.setdefault(key, base)
            if owner != base:
                errors.append(f"{base}: '{key}' is also defined in {owner}")
        prefix = f"{directory}/{name}_"
        for bundle in sorted(p for p in paths if p.startswith(prefix) and p.endswith(".properties")):
            messages = read_properties((root / bundle).read_text(encoding="utf-8"))
            for key in sorted(set(reference) - set(messages)):
                errors.append(f"{bundle}: missing '{key}'")
            for key in sorted(set(messages) - set(reference)):
                errors.append(f"{bundle}: '{key}' is not in {file_name}")
            for key, value in messages.items():
                if not value:
                    errors.append(f"{bundle}: message '{key}' is empty")
    return errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    paths = list_repository_files(root)

    errors = check_web(root, paths) + check_server(root, paths)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("i18n ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
