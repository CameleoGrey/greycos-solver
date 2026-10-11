# Resumable-search qualification and bounded runs

This harness checks execution and produces reviewable observations. Short runs do not establish
steady-state throughput, scalability, solution-quality superiority, or defaults. Keep the larger
adoption campaign separate.

`ResumableSearchBenchmark` reuses the assignment/global-load and list/cascading-shadow fixtures
from `MoveThreadingWorkload` and adds dense timetable resource conflicts. All returned solutions
undergo independent score and assignment/shadow replay. No external example repository is needed.

Build once, with Maven validation serialized with other repository builds:

```bash
./mvnw -B -pl core -am -DskipTests test-compile
./mvnw -B -pl core dependency:build-classpath -Dmdep.includeScope=test \
  -Dmdep.outputFile=target/resumable-benchmark-classpath.txt
benchmark_classpath="core/target/test-classes:core/target/classes:$(cat core/target/resumable-benchmark-classpath.txt)"
benchmark_revision="$(git rev-parse HEAD)"
```

Invocation:

```text
<basic|list|timetable> <mode> <la|dla|gls> <seed> <size>
<workers> <logicalWidth> <quantum> <steps|moves|scores|seconds> <budget>
[warmupSeconds=1] [trace=false]
```

Modes are `SERIAL`, `MOVE_THREADS`, `ISLANDS`, `INDEPENDENT_ISLANDS`, `SESSIONS`, and `COOPERATIVE`.
Use a fresh JVM for each invocation. The warmup uses a separate problem and solver in that JVM.
Assertions and full traces belong in correctness runs; timed runs disable assertions and read
the final accounting snapshot without per-step solution clones.

| Setting | Fixed value in the bounded panel |
| --- | --- |
| Input families and sizes | `basic=100`, `list=80`, `timetable=48` |
| Solver seeds | `0`, `37` |
| Controllers | `la`: LA400/accepted 1; `dla`: DLA64/accepted 16, explicitly enabled preview |
| Selectors | Basic change/swap or list change/swap; maximum 512 selected candidates per step |
| Physical workers | 4 for move threading and session modes |
| Logical width | 4 for islands and session modes |
| Quantum | 8 completed primitive steps |
| Cooperation | Protect session 0; stagnation 4 rounds; at most one weakest-session restart |
| Existing islands | Migration frequency `Integer.MAX_VALUE`; global comparison enabled every 400 steps |
| Existing independent islands | Same, with global comparison disabled |
| Time | 1-second warmup, 3-second external deadline, including search-worker/session startup |
| Heap/logging | Identical fixed heap and logging configuration for every invocation |

The complete panel has 72 measured runs and 216 nominal measured seconds. Interleave the six
modes in a predeclared order; do not rerun only unfavorable results. Keep host configuration
unchanged. Record process failures, timeouts and incomplete rows rather than dropping them.
Legacy islands use one worker per island; their `workers` argument does not independently change
that width. For matched resource smoke comparisons, set both arguments to 4.

The companion runner executes the fixed-work panel first (72 runs), then the timed panel (72
runs), each in a fresh JVM. It writes its complete manifest and classpath artifact hashes before
the first solver invocation, preserves stdout/stderr and a status for every planned case, enforces
a 30-second per-process watchdog and 30-minute campaign deadline, and never retries a result.
It removes ambient JVM option environment variables and passes its explicit heap settings.
The output directory must not already exist, so previous evidence cannot be overwritten:

```bash
python3 core/src/test/java/greycos/solver/core/impl/islandmodel/resumable/run_resumable_qualification.py \
  --repo "$PWD" --output core/target/resumable-qualification
```

`manifest.json` freezes the exact cases, commands and environment; `results.jsonl` contains every
status, `raw/` preserves process output, and `summary.json` checks fixed logical trace groups across
worker counts. `report.md` links those artifacts. A timeout or missing row fails qualification and
remains visible in the report. No speed or final-score adoption threshold is evaluated.

Example timed cell:

```bash
java -Xms512m -Xmx512m -Dgreycos.benchmark.revision="$benchmark_revision" \
  -cp "$benchmark_classpath" \
  greycos.solver.core.impl.islandmodel.resumable.ResumableSearchBenchmark \
  list SESSIONS la 37 80 4 4 8 seconds 3 1 false
```

For deterministic execution qualification, hold the entire logical configuration fixed and vary
only workers. Run both `SESSIONS` and `COOPERATIVE`, seeds 0/37, workers 1/2/4, and a 259-step
aggregate budget to exercise the last partial round:

```bash
java -Xms512m -Xmx512m -Dgreycos.benchmark.revision="$benchmark_revision" \
  -cp "$benchmark_classpath" \
  greycos.solver.core.impl.islandmodel.resumable.ResumableSearchBenchmark \
  basic SESSIONS dla 37 100 1 4 8 steps 259 0 true
```

Require identical trace hashes, final state, native score and logical work across worker counts.
Different logical widths, controllers, quanta or cooperation policies are different algorithms.
Legacy island traces are not exposed by this observer, so `trace=true` is rejected for those modes.
Legacy fixed-step comparisons require a budget divisible by width and give each island that
budget divided by width. Legacy move/score-budget comparisons are rejected because their quota
semantics differ from the new aggregate budget.

CSV separates completed steps, consumed moves and reported score calculations. Physical score
categories come from final runtime accounting, including initialization and finalization. Budget
overshoot uses the enclosing phase's committed logical counts. An unavailable measurement is `-1`,
not zero. A score calculation is not necessarily a consumed candidate: initialization, assertions,
restoration and nested probes can perform additional calculations. The configured work budget may
overshoot through already granted completed steps; preserve the full counts.

The output records config/state fingerprints, loaded core location, supplied revision, JDK/OS,
processor count, elapsed time, process CPU time, GC and used heap. Save the effective command,
full working-tree diff/artifact hash, hardware topology and logging configuration alongside the
CSV. Used heap is an end-of-run observation, not peak RSS or retained-memory proof. Internal
spent-limit measurements are a separate experiment from this external-deadline panel.

`ResumableSearchRuntimeTest`, `ResumableSearchLifecycleTest`, and `ResumableMixedSearchTest`
exercise candidate/step traces, private incremental notifications, reconstruction, deterministic
budgets, hierarchy ownership, cancellation, repeated solves and problem revisions. Those tests
are the correctness gate. This harness supplies bounded execution evidence only.
