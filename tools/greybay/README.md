# Greybay

Greybay searches Java-defined solver configurations on fixed instance/seed panels, records every attempt in a durable study, and qualifies one frozen training selection on separate held-out data. It provides conditional TPE and random search, exact score comparison, restart accounting, and ordinary solver XML export. This module establishes a configuration-search workflow; no solver effectiveness measurements or TPE superiority claim accompany it.

Add `io.github.cameleogrey:greycos-solver-greybay` using the GreyCOS BOM. Its JPMS module is `greycos.solver.greybay`. The production module depends on core, not benchmark. The runnable examples and optional benchmark launcher below are test sources, so their domain models and benchmark dependency do not enter the runtime artifact.

## Run the complete examples

Use the repository-supported JDK and run from the repository root. Build/install the reactor dependencies, then execute with the **test** classpath:

```shell
./mvnw -pl tools/greybay -am install -DskipTests
./mvnw -f tools/greybay/pom.xml test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=greycos.solver.greybay.examples.GreybayWorkflowExample \
  -Dexec.args="scheduling target/scheduling-study"
./mvnw -f tools/greybay/pom.xml test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=greycos.solver.greybay.examples.GreybayWorkflowExample \
  -Dexec.args="routing target/routing-study"
```

Use a new destination for each invocation: creation refuses an existing study. Relative argument paths resolve from the execution working directory. Each example creates a study, runs a segment, requests stop, closes it, explicitly resumes with larger limits, completes training, qualifies, exports, reloads XML, solves a fresh graph, and independently replays the result. Warmup is explicitly zero and the solve horizon is one second to keep the walkthrough small. The two candidate trials are TPE startup proposals, not a Bayesian effectiveness experiment. The final XML-reload solve is an additional demonstration cost outside the study accounting.

The complete models include public no-argument constructors, accessors, score calculators, adapters, and verifiers:

* [SchedulingExample.java](src/test/java/greycos/solver/greybay/examples/SchedulingExample.java): assign immutable work demand to workers at fixed slots using required basic variables. Hard score penalizes capacity excess; soft score penalizes preference violations and squared total load.
* [RoutingExample.java](src/test/java/greycos/solver/greybay/examples/RoutingExample.java): order visits on vehicle list variables, with inverse owner and index shadows. Hard score penalizes vehicle capacity excess; soft score penalizes Manhattan travel including depot legs.
* [GreybayWorkflowExample.java](src/test/java/greycos/solver/greybay/examples/GreybayWorkflowExample.java): a complete definition with bounded Late Acceptance controls and explicit lifecycle calls.

Both families keep the business input in immutable, unannotated records. Each adapter reconstructs fresh mutable planning objects with stable IDs. SHA-256 descriptions cover the generated input content. Replay checks the original demand/fleet, ID coverage, graph membership, assignments, list order, shadows, and native score without calling the solver score calculator. Native recalculation alone cannot detect removed input demand. The generated `train-small`, `train-medium`, `train-large`, `heldout-small`, `heldout-medium`, and `heldout-large` problems are illustrative fixtures, not a representative production corpus. Training and held-out records differ, and seed panels are disjoint.

Run the workflow and replay regression tests with:

```shell
./mvnw -pl tools/greybay -am test \
  -Dtest=GreybayWorkflowTest,ExampleBusinessReplayTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Define and run an application study

`GreybayStudyDefinition.builder()` requires the base `SolverConfig`, a `ProblemAdapter`, training and held-out `ProblemPanel`s, templates, an objective, a sampler, an explicit `optimizerSeed`, a solve duration, and admission limits. Pass `new BalancedQualityObjective<>()` and `.sampler(new TpeSamplerConfig())` or `.sampler(new RandomSamplerConfig())`; the sampler overload also accepts a custom `Sampler`. The required optimizer seed controls proposal generation separately from the panels' solver seeds. Specify warmup explicitly; the default is 30 seconds. Preflight, warmup, training, and held-out solver seeds must be disjoint. Problem IDs must also be disjoint across training and held-out panels.

The optional `baselineCandidateAssignment` identifies a baseline already represented in the declared search space. Supply the complete assignment including its `template` choice. Greybay checks that it materializes exactly the effective baseline before admitting it to sampler history. Omit this field for a baseline outside the space; do not invent parameter values to make it fit.

The base configuration is defensively captured. Every candidate receives an independent nested configuration. Application code supplies meaningful parameter bounds; there is no universally beneficial range. `ReferenceTemplates` exposes ordinary local-search variants, GLS, ALNS with registered portfolios, and ALNS/GLS-to-local-search pipelines. `TemplateParameters` declares the schema and deterministic default assignment. `MovePortfolio` and `AlnsPortfolio` register fixed selector/operator definitions and model requirements. Conditional dimensions reject inactive values; incompatible capabilities and portfolios fail validation. GLS does not combine with ordinary acceptors. Temperature/score scales and portfolio identities belong to the declared template, not a universal XML path mutation interface.

Candidate-specific materialization and pipeline-cap calculation failures are recorded as failed trials and retain their consumed trial slots on resume. An invalid preflight default or a violation of the frozen study protocol stops the study. Phase-specific worker overrides, including construction heuristics, must inherit or match the declared resource policy.

```java
try (var study = GreybayStudies.create(studyDirectory, definition)) {
    var training = study.run();
    if (training.hasEligibleIncumbent()) {
        var qualification = study.qualify();
        var export = study.exportSelected(studyDirectory.resolve("selected"));
        System.out.println(qualification.status());
        System.out.println(export.solverConfigPath());
    }
}
```

`run`, `qualify`, and `exportSelected` are synchronous and mutually exclusive. `requestStop` and immutable `snapshot` are thread-safe. Stop prevents new admissions and requests cooperative termination; return and `close` wait for owned active work and cleanup. A blocked custom callback may therefore prevent shutdown from completing. The directory lock remains held while resources are live. Snapshots expose committed observations, not partially written results.

Resume with the same immutable definition and explicit operational limits:

```java
try (var study = GreybayStudies.resume(studyDirectory, definition,
        StudyRunLimits.of(140, Duration.ofHours(36)))) {
    study.run();
}
```

Limits may increase; consumed candidate slots and active cost never reset. The baseline does not consume a candidate trial slot. Restarted interrupted evaluations use fresh graphs and fresh solvers, and uncertain interrupted cost remains labeled. Changing problem content, scoring, templates, objective, sampler, protocol, seeds, or declared environment requires a new study. A saved solution or XML file does not resume solver RNGs, adaptive acceptors, or algorithm state. Training cannot resume after selection is frozen or held-out evaluation begins.

## What the observations establish

Each evaluation uses the full declared solve horizon. Greybay retains the best complete solution observed on time and assesses a detached graph; a late returned improvement does not replace the selected on-time result. Loading, verification, sampling, warmup, failed attempts, restart uncertainty, and held-out work are part of cost reporting. Cooperative overshoot is recorded. Fixed resources and instrumentation must remain comparable. Pipelines retain the declared wall-clock phase policy; a shorter simulated-annealing run is not assumed to be a prefix of a longer run.

`BalancedQualityObjective` first requires eligible complete observations. It compares feasibility fraction, then normalized native score levels lexicographically with exact rational arithmetic. Seeds are averaged within each instance, and instances have equal weight. Baseline calibration fixes scales before candidate ranking; historical values do not change when a new candidate appears. An exact tie retains the earlier incumbent, including the baseline. Constraint matches are diagnostic attribution, not the objective. A required-assignment check is separate from optional independent business replay; configure a verifier when business completeness matters.

`qualify()` freezes the training selection before evaluating the baseline and that selection on the held-out panel. It never chooses another winner after inspecting held-out scores. Held-out scales come from the held-out baseline. Results report comparison (`BETTER`, `EQUAL`, `WORSE`, `UNAVAILABLE`) separately from status: failed measurement, inconclusive evidence, lack of full feasibility, held-out improvement, or no held-out improvement. A best-effort improvement is not labeled a fully feasible recommendation. These are statements about the declared panel, not statistical significance or universal quality.

## Identities, persistence, and export

Custom adapters, verifiers, templates, objectives, samplers, operator callbacks, and runtime reconstruction providers need stable `ComponentIdentity(id, version, artifactIdentity)` values. Change the artifact identity when implementation, dependencies, score weights, or fixed component behavior changes. A class name or lambda identity alone is not a reproducible artifact. Declare external input paths from file-backed adapters and content fingerprints from the bytes/data actually loaded. A custom objective must supply deterministic codecs and a transitive comparator. Runtime-only components require explicit reconstruction and its identity; XML cannot serialize live callbacks, class loaders, native resources, generated accessors, or clocks.

The study owns a locked directory containing its immutable manifest, append-only journal, referenced artifacts, and checkpoint. Corrupt committed history is an error; a torn final record is recovered under the journal contract. The coordinator reconstructs state by replaying the full authoritative journal. New checkpoints store a compact digest marker in that journal and atomically replace one full recovery cache. They are written at geometrically spaced terminal-trial counts and at operation completion, with checkpoint work included in active cost. A missing, corrupt, or stale cache is rebuilt from the journal; valid caches may precede later journal transitions. Legacy full checkpoint records remain readable without rewriting committed history. Existing artifact and environment identity checks still apply to resume. Do not edit study files or copy only the selected XML and describe that as a resumable study.

Export writes `selected-solver.xml`, versioned selection/provenance JSON, per-evaluation CSV, and a Markdown report, with the manifest committed last. It requires a frozen selection and retains failed/incomplete qualification labels. The runnable XML defaults to the first training seed, explicitly an example seed rather than an optimized seed. An existing same-selection destination returns its verified immutable snapshot at the journal position recorded in the manifest. It does not refresh when qualification later changes; export to a new destination to include that later evidence. Unrelated files, inputs, and other studies are protected from overwrite. `SolverConfig.writeXml(Writer)` keeps caller ownership of the writer. Always reload the XML with the required application classes on the classpath and verify its effective configuration.

## Optional benchmark rerun

After an export, the ordinary benchmark launcher can run the same declared family with explicit candidate/seed entries:

```shell
./mvnw -f tools/greybay/pom.xml test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=greycos.solver.greybay.examples.ExportedBenchmarkExample \
  -Dexec.args="routing target/routing-study/selected/selected-solver.xml target/routing-benchmark 101,103,107"
```

This launcher reconstructs the example baseline and copies the exported horizon/resource settings. It runs all three declared held-out fixture sizes, explicitly sets warmup to zero and parallelism to one, and creates one named entry per candidate/seed with `subSingleCount = 1`. Candidate aggregation remains Greybay's responsibility. Benchmark uses ordinary returned final scores; its HTML/CSV reruns are supplemental and never overwrite Greybay cutoff observations or select a replacement recommendation. Their compute and instrumentation are separate costs. The benchmark command is optional and is not run by the workflow example.

## Effectiveness protocol

The [qualification harness](QUALIFICATION.md) freezes an equal-compute TPE/random protocol and supports reporting without asserting a winning method. Freeze representative data, all sizes, panels, baseline, spaces, horizons, optimizer seeds, resources, qualification allocations, and reporting criteria before running it. Keep separate studies for different business score families. Ten optimizer seeds and paired instance-level uncertainty are the reference protocol; fewer seeds must be reported as limited evidence. No effectiveness campaign is run as part of feature delivery or ordinary unit tests.
