# Move-threading and multiple-list measurements

`MoveThreadingBenchmark` runs deterministic `basic`, `list`, `mixed-list`, and `multiple-list` workloads. It independently checks the final assignment, list shadows, and score. `size` means the number of values in the single-list workload and the number of values **in each** of the two lists in the multiple-list workload. The mixed-list workload uses the unchanged existing fixture with one list, two owner basic capacity assignments, a pinned prefix, built-in list shadows, a cascading shadow, declarative shadows, and the basic inverse collection. Its score couples list lengths with the owner capacities. The multiple-list workload also optimizes an owner setting and each second-list element's quantity; its score couples owner list sizes and settings.

Compile both checkouts' core tests, including the new benchmark classes in the current checkout, before running the benchmark. Each checkout needs a Surefire XML report containing its test classpath. The standalone Python runner uses only the Python standard library and requires Unix `os.wait4` for per-process resources. It does not invoke Maven or mutate build outputs; it writes only to the requested output directory:

```bash
python3 core/src/test/scripts/multiple-list-benchmark.py \
  --baseline /path/to/pre-change-checkout \
  --current "$PWD" \
  --output /tmp/multiple-list-protocol \
  --dry-run
```

Inspect `manifest.json`, then remove `--dry-run` and choose a different output directory to execute the same protocol. Every invocation, including a dry run, requires a new output directory outside both checkouts; existing directories are rejected without modification. A dry run copies the harness and records the complete schedule but starts no JVM. Run on an otherwise idle host, with no concurrent build or test jobs. Every sample uses a fresh JVM, `-Xms512m -Xmx512m -XX:+UseG1GC`, and sequential paired baseline/current scheduling; the pair order alternates by seed and repetition. The script records commands, checkout heads and full dirty state/diffs, source and compiled-class hashes, host details and load, raw stdout/stderr, process resource reports, combined CSV, exact parity comparisons, and a Markdown report. The manifest references full before/after inventories in `provenance/`; deterministic comparisons and separate wall-budget outcomes are in `comparisons.json`. Unset `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, and `_JAVA_OPTIONS`; the runner rejects implicit JVM options so the recorded flags describe the actual protocol.

The default protocol uses seeds 0, 1, and 2; `NONE` and two workers; two repetitions; size 200; and an accepted-count limit of 32. Its three modes answer separate questions:

| Mode | Work | Warmup | Evidence |
| --- | --- | --- | --- |
| `trace` | 200 completed steps | None | Exact useful-move count, score, final-state hash, and completed-step trace hash |
| `fixed_work` | 1,000 completed steps | 2 seconds | Solve time and work rate with trace hashing disabled; compare work/score/state before interpreting timing |
| `fixed_wall` | External 2-second phase deadline | 2 seconds | Best score and actual work completed under a requested wall budget; actual termination can overshoot |

The single-list and mixed-list workloads run on both versions using exactly the same benchmark bytecode. The runner copies only the benchmark classes and the new multiple-list fixture into a shared classpath overlay, checks that the pre-existing mixed fixture source is unchanged between checkouts, and verifies each result's `core_location` points to the intended engine. Class hashes are retained in the manifest. The multiple-list workload runs only on the current implementation, since the baseline rejects this model. Scores and work rates across different workloads are not comparable. The default matrix has 180 sequential JVM invocations. The runner preserves all samples and applies no performance thresholds. Options can narrow the matrix or change sizes and budgets; every effective command is recorded.

The same campaign may be split into two fresh output directories using `--modes trace` (60 runs), followed by `--modes fixed_work,fixed_wall` (120 runs). The CLI options and defaults are:

| Option | Default |
| --- | --- |
| `--baseline` | Required pre-change checkout |
| `--current` | Current working directory |
| `--output` | Required new output directory |
| `--java` | `java` |
| `--seeds` | `0,1,2` |
| `--workers` | `NONE,2` |
| `--repetitions` | `2` |
| `--size` | `200` (at least 4) |
| `--accepted-count` | `32` |
| `--fixed-steps` | `1000` |
| `--trace-steps` | `200` |
| `--warmup-seconds` | `2` |
| `--wall-seconds` | `2` |
| `--timeout-seconds` | `180` per JVM |
| `--workloads` | `list,mixed-list,multiple-list` (also supports `basic`) |
| `--modes` | `trace,fixed_work,fixed_wall` |
| `--dry-run` | Disabled |

`manifest.json` and `report.md` explicitly distinguish `complete`, `incomplete`, and `dry_run`, with expected, attempted, completed, failed, timed-out, interrupted, and unattempted counts. `runs.json` includes every scheduled invocation, including pending ones; `results.csv` includes every attempted run with its status and error. A missing member of a pair is an incomplete comparison, never a parity pass. Deterministic parity and repetition checks cover trace and fixed-work modes; wall-budget outcomes are reported separately. Source or compiled-core changes detected at the end make the campaign incomplete. The runner continues after ordinary process failures/timeouts, and preserves partial stdout/stderr and per-child resources. SIGINT/SIGTERM stop the active process group and retain the incomplete campaign. An uncatchable kill may leave the last checkpoint with pending runs; it cannot make an unfinished campaign complete. Exit status is nonzero for incomplete campaigns or deterministic differences; timing regressions alone never change it.

Run the runner's tests without Maven or Java:

```bash
python3 -B -m unittest discover -s core/src/test/scripts -p 'test_*.py' -v
```

`setup_ms` excludes process startup and includes problem/model/solver creation after any warmup. `solve_ms` includes solver initialization and shutdown. Process CPU and peak RSS include JVM startup, warmup, the measured solve, and final independent checking. `used_heap_bytes` is an uncollected endpoint reading, not retained or peak heap. GC counters cover only the measured solve. `allocated_bytes` is the delta from `com.sun.management.ThreadMXBean.getTotalThreadAllocatedBytes()` immediately around the measured solve. Allocation tracking is enabled during setup when supported; unsupported or unavailable counters report `-1`. The counter estimates VM-wide Java allocation across all threads, including background Java threads. It excludes warmup, setup, external-deadline cleanup, and independent final replay. No hot-loop instrumentation or allocation profiler is used. Allocated object counts and per-class attribution are not measured by this harness. Pipeline counters may be unavailable for serial or older implementations; `-1` means unavailable. Completed/useful move accounting is distinct from physical score calculations and speculative worker evaluations.

These are bounded synthetic-workload measurements on the recorded host/JVM. Report score outcomes separately from fixed-work time, include regressions, and retain exact hash mismatches. Other worktrees may run unrelated Maven jobs on the shared host; record any such overlap as measurement noise. Small paired-seed samples do not establish confidence intervals, workload scalability, or universal speedups.
