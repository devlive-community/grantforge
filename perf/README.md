<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

# Performance benchmarks

Benchmarks of GrantForge at the scale of the performance audit: a million accounts, ten thousand roles and a hundred
thousand resources. They run every night on PostgreSQL (`.github/workflows/nightly.yml`) and fail when a result
exceeds a limit in [`thresholds.properties`](thresholds.properties).

```bash
bash script/ci/perf_benchmark.sh full            # target scale on postgres:17 (needs Docker), thresholds enforced
bash script/ci/perf_benchmark.sh smoke           # small scale on H2, only checks the benchmarks still run
bash script/ci/perf_benchmark.sh full mysql:8.4  # another database, as TestDatabase names them
```

The report is written to `perf/target/perf-report.json`, and a table to the log.

## What is measured

The system benchmark starts the server on the database, completes the first-run setup and seeds the platform tenant
through the application's own entities:

- 100 departments, a group per thousand accounts and 100 positions;
- the accounts, each in a department and a group; every tenth holds a position and every twentieth has a role of its
  own;
- the roles, every tenth of them inheriting from one of the first hundred; each group has three roles, each department
  two and each position one; every role is granted five console pages;
- an application of modules with a hundred pages of nine actions each; a tenth of the roles are granted twenty of its
  pages and actions.

Each metric is measured one call at a time after a warmup:

| Metric | What | Limit |
| --- | --- | --- |
| `authz.snapshot.hit` | an account's console snapshot when it is cached, which every API call asks for | p99 1 ms |
| `authz.snapshot.build` | the console snapshot of an account asked about for the first time | p95 50 ms |
| `authz.snapshot.app` | the snapshot in the application with the large resource tree | p95 50 ms |
| `api.users.page` | a page of the user list as the administrator, within the first 500 pages | p95 200 ms |
| `api.users.search` | a user search by name | p95 200 ms |
| `api.groups.page` | a page of the group list | p95 200 ms |
| `api.roles.list` | the role list, which is not paginated | p95 200 ms |
| `api.me.authorization` | the signed-in account's own permissions | p95 200 ms |
| `api.member.groups` | a guarded list as an account with one page granted | p95 200 ms |
| `jmh.derivation.*` | JMH: preparing a grant derivation for the tree, and deriving twenty grants | mean 50 / 5 ms |

## Settings

`perf_benchmark.sh` passes extra `-D` options from `PERF_OPTS`, for example `PERF_OPTS="-Dperf.samples=200"`:

| Property | Default | Meaning |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2`, or `<engine>:<version>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | seeded scale |
| `perf.samples` / `perf.warmup` | 1000 / 200 | measured and unmeasured runs per metric |
| `perf.jmh` | `true` | run the JMH benchmark too |
| `perf.enforce` | `true` | exit with 1 when a limit is exceeded |

Limits may only be tightened; loosening one needs a recorded decision.
