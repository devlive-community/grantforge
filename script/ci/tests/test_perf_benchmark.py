# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

"""Exercise the performance entry point without starting Maven, a JVM or containers."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


class PerfBenchmarkTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        scripts = self.root / "script/ci"
        scripts.mkdir(parents=True)
        source = Path(__file__).resolve().parents[1]
        for name in ("perf_benchmark.sh", "maven.sh"):
            shutil.copyfile(source / name, scripts / name)
        binaries = self.root / "bin"
        binaries.mkdir()
        self.log = self.root / "commands.jsonl"
        executable = """#!/usr/bin/env python3
import json
import os
import sys
from pathlib import Path

kind = "maven" if Path(sys.argv[0]).name == "mvnw" else "java"
with Path(os.environ["PERF_SCRIPT_LOG"]).open("a", encoding="utf-8") as log:
    log.write(json.dumps([kind, *sys.argv[1:]]) + "\\n")
if kind == "maven" and "dependency:build-classpath" in sys.argv:
    target = Path("perf/target")
    target.mkdir(parents=True, exist_ok=True)
    (target / "classpath.txt").write_text("dependencies", encoding="utf-8")
"""
        for path in (self.root / "mvnw", binaries / "java"):
            path.write_text(executable, encoding="utf-8")
            path.chmod(0o755)
        self.environment = dict(os.environ, PATH=str(binaries) + os.pathsep + os.environ["PATH"],
                                PERF_SCRIPT_LOG=str(self.log), GRANTFORGE_CHECKED="1", GRANTFORGE_PERF_SKIP_BUILD="0",
                                PERF_OPTS="")

    def run_script(self, mode: str) -> list[list[str]]:
        subprocess.run(["bash", "script/ci/perf_benchmark.sh", mode], cwd=self.root,
                       env=self.environment, check=True, capture_output=True, text=True)
        return [json.loads(line) for line in self.log.read_text(encoding="utf-8").splitlines()]

    def test_prebuild_skips_integration_tests_but_harness_tests_and_full_gate_still_run(self) -> None:
        build, harness, benchmark = self.run_script("full")

        self.assertEqual(build[0], "maven")
        self.assertEqual(build[-4:], ["-DskipFrontend", "-DskipTests", "-DskipITs", "install"])
        self.assertEqual(harness[0], "maven")
        self.assertIn("test", harness)
        self.assertNotIn("-DskipTests", harness)
        self.assertNotIn("-DskipITs", harness)
        self.assertNotIn("-Dtest=none", harness)
        self.assertEqual(benchmark[0], "java")
        self.assertIn("-Dperf.users=1000000", benchmark)
        self.assertIn("-Dperf.enforce=true", benchmark)
        self.assertIn("-Dperf.database=postgres:17", benchmark)

    def test_reusing_installed_modules_still_tests_the_harness_and_runs_smoke(self) -> None:
        self.environment["GRANTFORGE_PERF_SKIP_BUILD"] = "1"

        harness, benchmark = self.run_script("smoke")

        self.assertEqual(harness[0], "maven")
        self.assertIn("test", harness)
        self.assertNotIn("install", harness)
        self.assertIn("-Dperf.users=5000", benchmark)
        self.assertIn("-Dperf.database=h2", benchmark)
        self.assertIn("-Dperf.enforce=false", benchmark)


if __name__ == "__main__":
    unittest.main()
