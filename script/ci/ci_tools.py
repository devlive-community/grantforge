#!/usr/bin/env python3
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Install pinned, checksum-verified lint tools for the CI scripts.

Tools are installed once into ``target/ci-tools/`` (ignored by git) so local runs
and CI use exactly the same versions. Binary downloads are verified against a
pinned SHA-256 before extraction; Python tools are installed with pip into a
private virtual environment.

Usage::

    python3 script/ci/ci_tools.py shellcheck    # prints the path of the binary
"""

from __future__ import annotations

import hashlib
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional, Sequence, Tuple

DEFAULT_ROOT = Path(__file__).resolve().parents[2]

# (system, machine) as normalised by platform_key().
PlatformKey = Tuple[str, str]


@dataclass(frozen=True)
class Asset:
    """A release archive for one platform and its pinned SHA-256."""

    url: str
    sha256: str


@dataclass(frozen=True)
class BinaryTool:
    """A tool shipped as a tar archive containing a single executable."""

    name: str
    version: str
    member: str  # path of the executable inside the archive
    assets: Dict[PlatformKey, Asset]


_SHELLCHECK_URL = "https://github.com/koalaman/shellcheck/releases/download/v0.10.0/shellcheck-v0.10.0.{}.tar.xz"
_ACTIONLINT_URL = "https://github.com/rhysd/actionlint/releases/download/v1.7.7/actionlint_1.7.7_{}.tar.gz"

BINARY_TOOLS: Dict[str, BinaryTool] = {
    "shellcheck": BinaryTool(
        name="shellcheck",
        version="0.10.0",
        member="shellcheck-v0.10.0/shellcheck",
        assets={
            ("linux", "x86_64"): Asset(_SHELLCHECK_URL.format("linux.x86_64"),
                                       "6c881ab0698e4e6ea235245f22832860544f17ba386442fe7e9d629f8cbedf87"),
            ("darwin", "arm64"): Asset(_SHELLCHECK_URL.format("darwin.aarch64"),
                                       "bbd2f14826328eee7679da7221f2bc3afb011f6a928b848c80c321f6046ddf81"),
            ("darwin", "x86_64"): Asset(_SHELLCHECK_URL.format("darwin.x86_64"),
                                        "ef27684f23279d112d8ad84e0823642e43f838993bbb8c0963db9b58a90464c2"),
        },
    ),
    "actionlint": BinaryTool(
        name="actionlint",
        version="1.7.7",
        member="actionlint",
        assets={
            ("linux", "x86_64"): Asset(_ACTIONLINT_URL.format("linux_amd64"),
                                       "023070a287cd8cccd71515fedc843f1985bf96c436b7effaecce67290e7e0757"),
            ("darwin", "arm64"): Asset(_ACTIONLINT_URL.format("darwin_arm64"),
                                       "2693315b9093aeacb4ebd91a993fea54fc215057bf0da2659056b4bc033873db"),
            ("darwin", "x86_64"): Asset(_ACTIONLINT_URL.format("darwin_amd64"),
                                        "28e5de5a05fc558474f638323d736d822fff183d2d492f0aecb2b73cc44584f5"),
        },
    ),
}

# Python tools installed with pip: name -> pinned version.
PIP_TOOLS: Dict[str, str] = {"ruff": "0.16.9"}


def platform_key(system: Optional[str] = None, machine: Optional[str] = None) -> PlatformKey:
    """Normalise ``platform.system()``/``platform.machine()`` to the keys used in ``assets``."""
    system = (system or platform.system()).lower()
    machine = (machine or platform.machine()).lower()
    aliases = {"amd64": "x86_64", "aarch64": "arm64"}
    return system, aliases.get(machine, machine)


def tools_dir(root: Path) -> Path:
    """Return the install directory, overridable with GRANTFORGE_CI_TOOLS."""
    override = os.environ.get("GRANTFORGE_CI_TOOLS")
    return Path(override) if override else root / "target" / "ci-tools"


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def extract_member(archive: Path, member: str, destination: Path) -> None:
    """Extract one regular file from a tar archive to ``destination``.

    Only the named member is read, so archive entries with absolute or ``..``
    paths can never be written outside the install directory.
    """
    with tarfile.open(archive) as tar:
        info = tar.getmember(member)
        if not info.isfile():
            raise ValueError(f"{archive.name}: {member} is not a regular file")
        source = tar.extractfile(info)
        if source is None:
            raise ValueError(f"{archive.name}: cannot read {member}")
        with source, destination.open("wb") as target:
            shutil.copyfileobj(source, target)
    destination.chmod(0o755)


def ensure_binary(tool: BinaryTool, root: Path, key: Optional[PlatformKey] = None,
                  download=urllib.request.urlretrieve) -> Path:
    """Return the path of ``tool``, downloading and verifying it on first use."""
    key = key or platform_key()
    asset = tool.assets.get(key)
    if asset is None:
        raise RuntimeError(f"{tool.name} {tool.version} has no pinned download for {key[0]}/{key[1]}")
    install = tools_dir(root) / f"{tool.name}-{tool.version}"
    binary = install / tool.name
    if binary.is_file():
        return binary

    install.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as scratch:
        archive = Path(scratch) / asset.url.rsplit("/", 1)[-1]
        download(asset.url, str(archive))
        actual = sha256_of(archive)
        if actual != asset.sha256:
            raise RuntimeError(
                f"{tool.name}: checksum mismatch for {asset.url} (expected {asset.sha256}, got {actual})"
            )
        # Write under a temporary name first so an interrupted run never leaves a half-written binary.
        partial = install / f".{tool.name}.partial"
        extract_member(archive, tool.member, partial)
        partial.replace(binary)
    return binary


def ensure_pip_tool(name: str, root: Path) -> Path:
    """Return the path of a pinned pip-installed tool inside its own virtual environment."""
    version = PIP_TOOLS.get(name)
    if version is None:
        raise KeyError(name)
    venv = tools_dir(root) / f"{name}-{version}"
    binary = venv / ("Scripts" if os.name == "nt" else "bin") / name
    if binary.is_file():
        return binary
    subprocess.run([sys.executable, "-m", "venv", str(venv)], check=True)
    python = venv / ("Scripts" if os.name == "nt" else "bin") / "python"
    subprocess.run([str(python), "-m", "pip", "install", "--quiet", "--disable-pip-version-check",
                    f"{name}=={version}"], check=True)
    return binary


def ensure(name: str, root: Path = DEFAULT_ROOT) -> Path:
    """Return the executable for a known tool name, installing it if needed."""
    if name in BINARY_TOOLS:
        return ensure_binary(BINARY_TOOLS[name], root)
    if name in PIP_TOOLS:
        return ensure_pip_tool(name, root)
    raise KeyError(f"unknown tool '{name}'; known: {', '.join(sorted([*BINARY_TOOLS, *PIP_TOOLS]))}")


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 1:
        print(f"usage: {Path(__file__).name} <{'|'.join(sorted([*BINARY_TOOLS, *PIP_TOOLS]))}>", file=sys.stderr)
        return 2
    try:
        print(ensure(args[0]))
    except (KeyError, RuntimeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
