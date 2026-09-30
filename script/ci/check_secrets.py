#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Scan the git history (or the working tree) for committed secrets with gitleaks.

gitleaks is pinned in ci_tools.py. Findings are printed redacted so the CI log
never repeats a leaked value. CI needs the full history (``fetch-depth: 0``).

Usage::

    python3 script/ci/check_secrets.py            # every commit in the history
    python3 script/ci/check_secrets.py --worktree # current files only (fast local check)
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path
from typing import Callable, List, Optional, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ci_tools  # noqa: E402

DEFAULT_ROOT = Path(__file__).resolve().parents[2]


def build_command(gitleaks: Path, worktree: bool) -> List[str]:
    """Return the gitleaks invocation; exit code 1 means leaks were found."""
    mode = ["dir", "."] if worktree else ["git", "."]
    return [str(gitleaks), *mode, "--redact", "--no-banner", "--exit-code", "1"]


def main(argv: Optional[Sequence[str]] = None, root: Path = DEFAULT_ROOT,
         run: Optional[Callable[[List[str], Path], int]] = None,
         find: Optional[Callable[[str], Path]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--worktree", action="store_true", help="scan current files instead of the git history")
    args = parser.parse_args(argv)

    finder = find or (lambda name: ci_tools.ensure(name, root))
    runner = run or (lambda command, cwd: subprocess.run(command, cwd=cwd, check=False).returncode)
    code = runner(build_command(finder("gitleaks"), args.worktree), root)
    if code == 0:
        print("secrets ok")
        return 0
    if code == 1:
        print("gitleaks found possible secrets (see redacted findings above). Rotate any real secret, then "
              "remove it from history or add a reviewed allowlist entry.", file=sys.stderr)
        return 1
    print(f"error: gitleaks failed with exit code {code}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
