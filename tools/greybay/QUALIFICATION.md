# Greybay effectiveness qualification harness

This delivery contains reusable campaign tooling and controlled correctness fixtures. **No solver-effectiveness campaign was run, and no TPE effectiveness evidence is delivered.** The fixtures establish accounting, freeze, reporting, and paired-statistics behavior only. Stage C workflow examples and random startup trials do not complete Stage D.

The tools live in `src/test/java/greycos/solver/greybay/qualification`, so their interface has no public runtime compatibility commitment. Ordinary tests use a controlled runner; `GreybayCampaignRunner.live()` is the explicit bridge to `GreybayStudies.create`, search, resume, qualification, and export. `GreybayCampaignMain` is an opt-in launcher, not a test.

## Freeze a campaign before measuring

Each invocation covers one problem family and one declared deployment horizon. `CampaignHarness.freeze(protocol, definitionFactory)` captures every TPE/random optimizer invocation before any runner is called. Its canonical JSON records:

- Family, basic/list/mixed model declaration, size labels, problem content fingerprints, and ordered training/held-out instance and solver-seed panels.
- Baseline XML, adapter/verifier/objective/reconstruction identities, template code identities, complete conditional spaces and bounds, and default assignments.
- Solve horizon, preflight/warmup seeds and duration, resource/environment policy, sampler identities, optimizer seeds, and predeclared alternating execution order.
- Separate search and qualification active-time allocations, candidate safety ceiling, reporting criteria, and bootstrap estimator/seed/resample count.

The definition factory may vary only the sampler and optimizer seed. Different panels, horizons, spaces, baselines, objectives, resources, or allocations are rejected before execution. A new output directory is required. Its `campaign-manifest.json` is written before the first runner invocation, and its SHA-256 identity links the results. The definition is checked again before measurement. Application callback identities remain the application's responsibility; a stable string cannot prove unchanged callback semantics or identical host load.

The reference optimizer panel is ten explicit seeds: `101,211,307,401,503,601,701,809,907,1009`. A caller can declare fewer, but the report labels that evidence limited. There is no universal solve horizon or tuning-time default. Choose the deployment horizon and allocations before inspecting outcomes. Give TPE sufficient room to leave startup; the candidate ceiling is a safety limit, not an equal-compute claim.

For the reference matrix, predeclare at least a basic-variable scheduling family and a list-variable routing family with small, medium, and large instances in each. Add mixed models only for qualified templates. Freeze all intended family/horizon invocations before running the matrix. Do not pool unrelated families' business scores into one study or choose a new horizon after viewing its held-out results. The bundled generated scheduling/routing inputs are runnable protocol examples; size labels alone do not make them representative deployment data. Replace the problem adapter, baseline, instances, space, and resource policy for application evidence.

## Equal allocations and complete paired panels

Both samplers receive the same search active-time allowance and the same qualification allowance, identical complete Cartesian panels, identical solve horizons, and the same resource policy. Search active time includes setup, preflight, warmup, baseline calibration, failed attempts, verification, sampler overhead, and recovery. Qualification starts only after search ends and freezes its training selection before reading held-out scores. Restart warmup, held-out baseline/selected evaluations, verification, and export are charged to qualification. Reused baseline selection is evaluated once per held-out key. A fixed baseline is a deployment comparator, not a search algorithm credited with an equal tuning spend.

The live runner reopens the study with a cumulative limit of consumed search admission charge plus the fixed qualification allowance, or the existing cap if that is larger because resume limits cannot decrease. An additional elapsed-active watchdog enforces the qualification allowance even after early search exhaustion: it counts resume setup, requests stop at the remaining allowance, and retains lifecycle ownership until the study returns. Unused search allowance is not transferred to held-out evaluation. An allocation exhausted during setup admits no held-out evaluation. A watchdog requests cancellation; it cannot kill a blocked application callback or bound cleanup. Do not increase a completed campaign's allocation after seeing its result.

Equal allocated time does not imply equal measured consumption: finite-space exhaustion, early stopping, failures, or slow cancellation can differ. Reports retain actual measured/lower-bound active cost, admission charge, unknown interrupted cost, allocation overruns, nominal solver budgets, and all outcomes. Nominal work is not measured CPU time. Outer campaign freeze/report orchestration is separate from per-study active accounting. The study cap is an admission/cancellation policy, not a hard process-kill guarantee. Any unmeasured interrupted work prevents an exact total-cost claim.

## Paired uncertainty and reports

`InstancePairedBootstrap` is independent of the study objective and sampler. It computes the equally weighted mean of per-instance paired differences, averaging every declared optimizer/solver seed within each instance. For each of **10,000** bootstrap replicates it samples instances with replacement and carries all their paired seed observations together. The resampling seed is mandatory and frozen. Observations are put in canonical instance order before using Java's specified `Random` generator.

The report uses 95% percentile intervals with the inverse empirical CDF: sorted replicate indices 249 and 9749, counted from zero. Arithmetic and point estimates are exact rationals. Feasibility has its own interval; every native score level has a separate interval in its original units. These intervals do not replace the lexicographic study comparison or select a new incumbent. They describe variation across this instance panel conditional on the declared seed panel, not independent resampling of optimizer runs or universal superiority. A single instance gives a degenerate instance-bootstrap interval and cannot support broad generalization.

Comparisons are TPE selected minus random selected, TPE selected minus its fixed baseline, and random selected minus its fixed baseline. Baselines are rerun under each declared study's paired protocol; timed observations can legitimately differ. All predeclared paired cells must be eligible before an interval is produced. Missing/failed cells are never imputed as zero, and the report never substitutes a completed-case subset. Per-instance mean differences and paired seed ranges expose variability. Exact held-out objectives retain the study's normalization and feasibility-first semantics separately from these raw-level intervals.

`campaign-results.json` retains accounting, exact encoded objectives, qualification status/comparison, and per-attempt held-out observations. `campaign-report.md` retains negative and inconclusive outcomes, completeness evidence, resource costs, paired intervals, and training progression against cumulative measured evaluation work. That progression axis is explicitly a lower bound; total study active accounting additionally includes setup, warmup, sampler and export overhead. Individual study journals and exports contain the detailed stage evidence. No candidate is replaced after looking at held-out results, and no report claims statistically established parameter causality or global optimality.

## Run later

First build the module and its test sources using the repository's normal Maven workflow. The focused controlled checks can be selected with:

```bash
./mvnw -pl tools/greybay -am test \
  -Dtest=CampaignHarnessTest,InstancePairedBootstrapTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Run `greycos.solver.greybay.qualification.GreybayCampaignMain` from an IDE with the module's **test** runtime classpath, or use Maven's exec plugin after reactor dependencies have been installed:

```bash
./mvnw -pl tools/greybay test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=greycos.solver.greybay.qualification.GreybayCampaignMain \
  -Dexec.args="freeze scheduling /absolute/new-campaign SOLVE_MS SEARCH_ACTIVE_MS QUALIFICATION_ACTIVE_MS WARMUP_MS MAX_TRIALS BOOTSTRAP_SEED RESOURCE_LABEL 'REPORTING_CRITERIA'"
```

Replace every uppercase placeholder with an explicitly chosen value. `freeze` prints the deterministic manifest without solving or creating the output directory. Review and archive that manifest, then use the same arguments with `run` when intentionally starting measurements. Use `routing` for the list-variable family. An optional final comma-separated optimizer-seed list replaces the reference ten. The launcher declares three solver seeds for every training instance (`11,17,23`) and disjoint held-out seeds (`101,107,113`); applications needing other seed panels should supply their own `DefinitionFactory`.

The launcher records Java/OS/architecture and the supplied resource label with fixed single-threaded resources. Record the actual host, application build/artifact, CPU allocation, load policy and instrumentation in that label or a custom `ResourcePolicy`. Neither the launcher nor the manifest guarantees equivalent machine load. A multi-family effectiveness publication still requires running the predeclared matrix, checking all protocol failures and negative results, and bounding every claim to that evidence.
