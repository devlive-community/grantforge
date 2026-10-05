#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Check or fix the MIT license header of every file in the repository.

Every tracked (and untracked but not ignored) file must either carry the
project license header, written with the comment syntax of its file type, or
match an entry of ``license_header_exclusions.txt`` that states a reason.
Files whose type has no known comment syntax and that are not excluded are
reported as errors, so a new file type always forces an explicit decision.

Usage::

    python3 script/ci/check_license_headers.py            # check, exit 1 on failure
    python3 script/ci/check_license_headers.py --fix      # insert/replace headers
    python3 script/ci/check_license_headers.py a.java b.ts  # limit to given paths
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Sequence, Tuple

# Allow "import cilib" when this file is loaded by path (tests) rather than executed directly.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from cilib import glob_to_regex, is_excluded, list_repository_files, load_exclusions  # noqa: E402

HEADER_LINES: Tuple[str, ...] = (
    "Copyright (c) 2026 devlive-community/grantforge",
    "",
    "Licensed under the MIT License. See the LICENSE file in the",
    "project root for full license text.",
)

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_EXCLUSIONS = Path(__file__).resolve().parent / "license_header_exclusions.txt"

# Words that identify a leading comment as a (legacy) license header that --fix may replace.
# Anything else at the top of a file is treated as real content and is never removed.
_LICENSE_MARKERS = re.compile(r"copyright|licen[cs]e|apache|spdx", re.IGNORECASE)


@dataclass(frozen=True)
class CommentStyle:
    """Comment syntax used to render the header for one family of file types.

    ``line_prefix`` is set for line-comment languages; ``block_open``/``block_close``
    (with ``block_prefix`` for inner lines) are set for block-comment languages.
    Exactly one of the two families is populated.
    """

    name: str
    line_prefix: Optional[str] = None
    block_open: Optional[str] = None
    block_prefix: Optional[str] = None
    block_close: Optional[str] = None

    def render(self) -> List[str]:
        """Return the header as a list of lines (without line terminators)."""
        if self.line_prefix is not None:
            return [f"{self.line_prefix} {line}" if line else self.line_prefix for line in HEADER_LINES]
        # Block style: the opening and closing markers sit on their own lines.
        assert self.block_open is not None and self.block_close is not None
        prefix = self.block_prefix or ""
        body = [f"{prefix}{line}".rstrip() if line else prefix.rstrip() for line in HEADER_LINES]
        return [self.block_open, *body, self.block_close]


SLASH = CommentStyle("slash", line_prefix="//")
HASH = CommentStyle("hash", line_prefix="#")
DASH = CommentStyle("dash", line_prefix="--")
CSS = CommentStyle("css", block_open="/*", block_prefix=" * ", block_close=" */")
MARKUP = CommentStyle("markup", block_open="<!--", block_prefix="  ", block_close="-->")

# Extension (lower case, with dot) -> comment style.
STYLE_BY_EXTENSION: Dict[str, CommentStyle] = {
    ".java": SLASH,
    ".ts": SLASH,
    ".tsx": SLASH,
    ".js": SLASH,
    ".mjs": SLASH,
    ".cjs": SLASH,
    ".jsx": SLASH,
    ".css": CSS,
    ".scss": CSS,
    ".sh": HASH,
    ".bash": HASH,
    ".py": HASH,
    ".yml": HASH,
    ".yaml": HASH,
    ".properties": HASH,
    ".toml": HASH,
    ".conf": HASH,
    ".imports": HASH,  # Spring Boot auto-configuration lists; ImportCandidates skips # lines
    ".cnf": HASH,
    ".sql": DASH,
    ".xml": MARKUP,
    ".html": MARKUP,
    ".vue": MARKUP,
    ".md": MARKUP,
}

# Exact file names (no extension or special names) -> comment style.
STYLE_BY_FILENAME: Dict[str, CommentStyle] = {
    "Dockerfile": HASH,
    ".gitignore": HASH,
    ".gitattributes": HASH,
    ".editorconfig": HASH,
    ".browserslistrc": HASH,
    ".npmrc": HASH,
    ".helmignore": HASH,
    ".gitleaksignore": HASH,
}

# Glob patterns -> comment style, for files whose extension alone is ambiguous (e.g. .txt).
STYLE_BY_GLOB: Tuple[Tuple[str, CommentStyle], ...] = (
    ("**/requirements*.txt", HASH),
    ("script/ci/*.txt", HASH),  # rule files read by the CI scripts
    ("**/META-INF/services/*", HASH),  # java.util.ServiceLoader provider files allow '#' comments
)


def style_for(path: str) -> Optional[CommentStyle]:
    """Return the comment style for a repository-relative POSIX path, or None if unknown."""
    for pattern, style in STYLE_BY_GLOB:
        if glob_to_regex(pattern).fullmatch(path):
            return style
    name = path.rsplit("/", 1)[-1]
    if name in STYLE_BY_FILENAME:
        return STYLE_BY_FILENAME[name]
    dot = name.rfind(".")
    if dot <= 0:
        return None
    return STYLE_BY_EXTENSION.get(name[dot:].lower())


# ---------------------------------------------------------------------------
# Header detection and rewriting
# ---------------------------------------------------------------------------

_XML_DECLARATION = re.compile(r"^<\?xml\b.*\?>\s*$")
_DOCTYPE = re.compile(r"^<!doctype\b", re.IGNORECASE)
_PYTHON_ENCODING = re.compile(r"^#.*coding[:=]")
_DOCKER_DIRECTIVE = re.compile(r"^#\s*(syntax|escape|check)\s*=", re.IGNORECASE)


def preamble_length(lines: Sequence[str], path: str, style: CommentStyle) -> int:
    """Count leading lines that must stay above the header.

    These are lines whose position is significant to a tool: shebangs, Python
    encoding cookies, Dockerfile parser directives, XML declarations, HTML
    doctypes and Markdown front matter (which must start on line 1).
    """
    index = 0
    if index < len(lines) and lines[index].startswith("#!"):
        index += 1
    if path.endswith(".py") and index < len(lines) and _PYTHON_ENCODING.match(lines[index]):
        index += 1
    if path.rsplit("/", 1)[-1] == "Dockerfile":
        while index < len(lines) and _DOCKER_DIRECTIVE.match(lines[index]):
            index += 1
    if style is MARKUP and index < len(lines):
        if _XML_DECLARATION.match(lines[index]) or _DOCTYPE.match(lines[index]):
            index += 1
        elif path.endswith(".md") and lines[index].strip() == "---":
            # Front matter: keep everything up to and including the closing '---'.
            for end in range(index + 1, len(lines)):
                if lines[end].strip() == "---":
                    return end + 1
    return index


def leading_comment_span(lines: Sequence[str], start: int, style: CommentStyle) -> int:
    """Return the number of lines of the comment that begins at ``start`` (0 if none).

    For line-comment styles this is the run of consecutive comment lines; for
    block styles it runs to the line containing the closing marker. Java/TS
    ``/* ... */`` blocks are recognised for every C-like style because legacy
    files used them even where the new header uses ``//``.
    """
    if start >= len(lines):
        return 0
    first = lines[start].lstrip()
    block_pairs = []
    if style.block_open is not None and style.block_close is not None:
        block_pairs.append((style.block_open, style.block_close.strip()))
    if style is SLASH or style is CSS:
        block_pairs.append(("/*", "*/"))
    for opener, closer in block_pairs:
        if first.startswith(opener):
            for end in range(start, len(lines)):
                probe = lines[end] if end > start else lines[end].lstrip()[len(opener):]
                if closer in probe:
                    return end - start + 1
            return 0  # Unterminated block: do not guess.
    if style.line_prefix is not None and first.startswith(style.line_prefix):
        # Never swallow a shebang-like line or a following non-comment line.
        end = start
        while (end < len(lines) and lines[end].lstrip().startswith(style.line_prefix)
               and not lines[end].startswith("#!")):
            end += 1
        return end - start
    return 0


def check_text(text: str, path: str, style: CommentStyle) -> Optional[str]:
    """Return None when the header is correct, otherwise a human-readable reason."""
    lines = _strip_bom(text).splitlines()
    start = preamble_length(lines, path, style)
    expected = style.render()
    actual = lines[start:start + len(expected)]
    if not actual or not _mentions_license(actual):
        return "missing license header"
    for offset, (want, got) in enumerate(zip(expected, actual)):
        if want != got:
            return f"header mismatch at line {start + offset + 1}: expected {want!r}, found {got!r}"
    if len(actual) < len(expected):
        return "license header is truncated"
    after = start + len(expected)
    if after < len(lines) and lines[after].strip() != "":
        return f"line {after + 1} must be blank after the license header"
    return None


def fix_text(text: str, path: str, style: CommentStyle) -> str:
    """Return ``text`` with exactly one correct header, replacing a legacy license comment.

    Line endings, a UTF-8 BOM, the preamble and all other content are preserved.
    Only a leading comment that looks like a license (see ``_LICENSE_MARKERS``)
    is removed, so ordinary documentation comments are never deleted.
    """
    bom = "﻿" if text.startswith("﻿") else ""
    body = _strip_bom(text)
    newline = "\r\n" if "\r\n" in body else "\n"
    had_trailing_newline = body.endswith(("\n", "\r"))
    lines = body.splitlines()

    start = preamble_length(lines, path, style)
    span = leading_comment_span(lines, start, style)
    if span and _mentions_license(lines[start:start + span]):
        rest = lines[start + span:]
    else:
        rest = lines[start:]
    while rest and rest[0].strip() == "":
        rest = rest[1:]

    new_lines = [*lines[:start], *style.render()]
    if rest:
        new_lines += ["", *rest]
    result = newline.join(new_lines)
    if had_trailing_newline or not rest:
        result += newline
    return bom + result


def _strip_bom(text: str) -> str:
    return text[1:] if text.startswith("﻿") else text


def _mentions_license(lines: Iterable[str]) -> bool:
    return any(_LICENSE_MARKERS.search(line) for line in lines)


# ---------------------------------------------------------------------------
# Command line
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class Finding:
    path: str
    reason: str


def run(root: Path, exclusions_path: Path, paths: Sequence[str], fix: bool,
        reader: Callable[[Path], str] = lambda p: p.read_text(encoding="utf-8")) -> Tuple[List[Finding], List[str]]:
    """Check (and optionally fix) files; return (remaining findings, fixed paths)."""
    rules = load_exclusions(exclusions_path)
    candidates = list(paths) if paths else list_repository_files(root)
    findings: List[Finding] = []
    fixed: List[str] = []
    for relative in candidates:
        relative = relative.replace("\\", "/")
        if is_excluded(relative, rules):
            continue
        style = style_for(relative)
        if style is None:
            findings.append(Finding(relative, "no comment syntax known for this file type; "
                                              "add a style or an exclusion with a reason"))
            continue
        file_path = root / relative
        try:
            text = reader(file_path)
        except UnicodeDecodeError:
            findings.append(Finding(relative, "not UTF-8 text; exclude binary files explicitly"))
            continue
        reason = check_text(text, relative, style)
        if reason is None:
            continue
        if fix:
            file_path.write_text(fix_text(text, relative, style), encoding="utf-8", newline="")
            fixed.append(relative)
        else:
            findings.append(Finding(relative, reason))
    return findings, fixed


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", nargs="*", help="repository-relative files to process (default: all)")
    parser.add_argument("--fix", action="store_true", help="insert or replace headers in place")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="repository root")
    parser.add_argument("--exclusions", type=Path, default=DEFAULT_EXCLUSIONS, help="exclusion rule file")
    args = parser.parse_args(argv)

    try:
        findings, fixed = run(args.root.resolve(), args.exclusions, args.paths, args.fix)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    for path in fixed:
        print(f"fixed: {path}")
    for finding in findings:
        print(f"{finding.path}: {finding.reason}", file=sys.stderr)
    if findings:
        print(f"\n{len(findings)} file(s) without a valid license header. "
              f"Run: python3 script/ci/check_license_headers.py --fix", file=sys.stderr)
        return 1
    print(f"license headers ok ({len(fixed)} fixed)" if args.fix else "license headers ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
