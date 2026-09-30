#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Lint the repository's shell scripts, Python CI scripts and GitHub workflows.

* shell     - ShellCheck on every tracked ``*.sh`` file
* python    - ruff (config: script/ci/ruff.toml) on every tracked ``*.py`` file under script/
* workflows - actionlint on .github/workflows (with ShellCheck for ``run:`` blocks)

Tool versions are pinned in ci_tools.py and installed on first use.

Usage::

    python3 script/ci/lint_scripts.py [shell|python|workflows ...]   # default: all
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence

# Allow "import cilib" when this file is loaded by path (tests) rather than executed directly.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import ci_tools  # noqa: E402
from cilib import list_repository_files  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
RUFF_CONFIG = Path(__file__).resolve().parent / "ruff.toml"

Runner = Callable[[Sequence[str], Path], int]
ToolFinder = Callable[[str], Path]


def _run(command: Sequence[str], cwd: Path) -> int:
    return subprocess.run(list(command), cwd=cwd, check=False).returncode


def shell_files(paths: Sequence[str]) -> List[str]:
    return [p for p in paths if p.endswith(".sh")]


def python_files(paths: Sequence[str]) -> List[str]:
    return [p for p in paths if p.startswith("script/") and p.endswith(".py")]


def workflow_files(paths: Sequence[str]) -> List[str]:
    return [p for p in paths if p.startswith(".github/workflows/") and p.endswith((".yml", ".yaml"))]


def build_commands(paths: Sequence[str], find: ToolFinder) -> Dict[str, Optional[List[str]]]:
    """Return the command for each linter, or None when it has no files to check."""
    shell = shell_files(paths)
    python = python_files(paths)
    workflows = workflow_files(paths)
    return {
        # -x follows sourced files; external sources are resolved relative to each script.
        "shell": [str(find("shellcheck")), "-x", *shell] if shell else None,
        "python": [str(find("ruff")), "check", "--config", str(RUFF_CONFIG), *python] if python else None,
        "workflows": [str(find("actionlint")), f"-shellcheck={find('shellcheck')}", *workflows]
        if workflows else None,
    }


def main(argv: Optional[Sequence[str]] = None, root: Path = DEFAULT_ROOT,
         run: Runner = _run, find: Optional[ToolFinder] = None) -> int:
    requested = list(sys.argv[1:] if argv is None else argv) or ["shell", "python", "workflows"]
    finder: ToolFinder = find or (lambda name: ci_tools.ensure(name, root))
    unknown = [name for name in requested if name not in ("shell", "python", "workflows")]
    if unknown:
        print(f"unknown linter(s): {', '.join(unknown)}", file=sys.stderr)
        return 2

    commands = build_commands(list_repository_files(root), finder)
    failed: List[str] = []
    for name in requested:
        command = commands[name]
        if command is None:
            print(f"{name}: no files")
            continue
        print(f"{name}: {len(command) - 2} file(s)" if name != "python" else f"{name}: checking")
        if run(command, root) != 0:
            failed.append(name)
    if failed:
        print(f"lint failed: {', '.join(failed)}", file=sys.stderr)
        return 1
    print("script lint ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
