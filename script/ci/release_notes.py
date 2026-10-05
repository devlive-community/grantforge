#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Write the release notes of a version from its page in the documentation site's changelog.

The notes are the page of ``docs/content/changelog/`` whose title starts with the
version (``2026.0.0`` or ``2026.0.0（重构版）``), without its front matter and
license comment, and with the site's own links made absolute so they work on the
GitHub release page. A release without such a page fails: the changelog is
written before the tag.

Usage::

    python3 script/ci/release_notes.py 2026.0.0 [--output FILE] [--root DIR]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Optional, Sequence

DEFAULT_ROOT = Path(__file__).resolve().parents[2]

_FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.S)
_TITLE = re.compile(r"^title:\s*(.+?)\s*$", re.M)
_LICENSE = re.compile(r"\A\s*<!--.*?-->\s*", re.S)
# Markdown links to the site's own pages: ](/start/upgrade/) or ](/start/upgrade/#anchor).
_SITE_LINK = re.compile(r"\]\((/[^)\s]*)\)")


def page_title(text: str) -> Optional[str]:
    """Return the title in a page's front matter, without quotes."""
    front = _FRONT_MATTER.match(text)
    title = _TITLE.search(front.group(1)) if front else None
    return title.group(1).strip("\"'") if title else None


def names_version(title: str, version: str) -> bool:
    """Whether a changelog title is about the version: it is the version, or starts with it and no digit or dot."""
    return title == version or (title.startswith(version) and not re.match(r"[\d.]", title[len(version)]))


def find_page(root: Path, version: str) -> Optional[Path]:
    """Return the changelog page of the version."""
    for page in sorted((root / "docs" / "content" / "changelog").glob("*.md")):
        title = page_title(page.read_text(encoding="utf-8"))
        if title and names_version(title, version):
            return page
    return None


def site_url(root: Path) -> str:
    """Return the documentation site's address, from the domain it is published under."""
    domain = (root / "docs" / "public" / "CNAME").read_text(encoding="utf-8").strip()
    return "https://" + domain


def notes(text: str, site: str) -> str:
    """Return a page's body as release notes."""
    front = _FRONT_MATTER.match(text)
    body = text[front.end():] if front else text
    body = _LICENSE.sub("", body, count=1)
    body = _SITE_LINK.sub(lambda match: "](" + site + match.group(1) + ")", body)
    return body.strip() + "\n"


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("version", help="the version, such as 2026.0.0")
    parser.add_argument("--output", type=Path, help="file to write; standard output by default")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    args = parser.parse_args(argv)
    root: Path = args.root.resolve()
    page = find_page(root, args.version)
    if page is None:
        print(f"no page in docs/content/changelog/ is titled {args.version}; write the changelog before tagging",
              file=sys.stderr)
        return 1
    written = notes(page.read_text(encoding="utf-8"), site_url(root))
    if args.output:
        args.output.write_text(written, encoding="utf-8")
        print(f"release notes of {args.version} from {page.relative_to(root)} written to {args.output}")
    else:
        sys.stdout.write(written)
    return 0


if __name__ == "__main__":
    sys.exit(main())
