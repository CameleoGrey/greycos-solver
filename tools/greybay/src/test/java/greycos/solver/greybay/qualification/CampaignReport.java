package greycos.solver.greybay.qualification;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.EvaluationRecord;
import greycos.solver.greybay.api.QualificationResult;
import greycos.solver.greybay.api.TrialStatus;
import greycos.solver.greybay.impl.persistence.StoreJson;

/** Reports every predeclared run, including negative, incomplete and failed outcomes. */
final class CampaignReport {
  private CampaignReport() {}

  static <Solution_, Score_ extends Score<Score_>> void write(
      CampaignHarness.Frozen<Solution_, Score_> frozen,
      List<CampaignHarness.Run<Score_>> runs,
      Path directory)
      throws IOException {
    var protocol = frozen.protocol();
    var definition = frozen.requests().getFirst().definition();
    var text = new StringBuilder("# Greybay campaign report\n\n");
    text.append("Manifest: `")
        .append(frozen.fingerprint())
        .append("`. Family: ")
        .append(protocol.family())
        .append(". Completed runner invocations: ")
        .append(runs.size())
        .append(" / ")
        .append(frozen.requests().size())
        .append(".\n\n");
    text.append(
            "This report describes only this frozen panel and horizon. A negative or inconclusive result is retained. ")
        .append(
            "The baseline is a deployment comparator; it is not credited with an equal tuning spend.\n\n");
    if (protocol.optimizerSeeds().size() < 10) {
      text.append("LIMITED EVIDENCE: ")
          .append(protocol.optimizerSeeds().size())
          .append(" optimizer seeds; the reference protocol calls for ten.\n\n");
    }
    text.append("Allocation per method/optimizer seed: search ")
        .append(protocol.searchAllocation())
        .append(", qualification ")
        .append(protocol.qualificationAllocation())
        .append(". ")
        .append(
            "Equal allocated active time does not mean equal actual consumption. Unused search time is not ")
        .append(
            "transferred to qualification. Caps request cancellation and are not process-kill deadlines. ")
        .append("Measured cost is a lower bound when an interrupted cost is unknown.\n\n");
    text.append("Scored horizon: ")
        .append(definition.solveDuration())
        .append(". Maximum nominal training solver nanos ")
        .append("(baseline plus candidate ceiling, excluding setup/warmup/overrun): ")
        .append(
            BigInteger.valueOf(protocol.maximumTrials())
                .add(BigInteger.ONE)
                .multiply(BigInteger.valueOf(definition.trainingPanel().size()))
                .multiply(BigInteger.valueOf(definition.solveDuration().toNanos())))
        .append(". Actual invocation counts can differ under equal time allocations.\n\n");
    text.append(
            "| Method | Optimizer seed | Complete/failed/ineligible trials | Held-out status | Comparison | Feasibility | ")
        .append(
            "Search measured ns | Qualification measured ns | Total admission ns | Unknown cost |\n")
        .append("|---|---:|---|---|---|---|---:|---:|---:|---|\n");
    var encodedRuns = new ArrayList<Map<String, Object>>();
    for (var run : runs) {
      var evidence = run.evidence();
      var search = evidence.search();
      var finished = evidence.finished();
      var qualification = evidence.qualification();
      long complete =
          search.trials().stream().filter(t -> t.status() == TrialStatus.COMPLETE).count();
      long failed = search.trials().stream().filter(t -> t.status() == TrialStatus.FAILED).count();
      long ineligible = search.trials().size() - complete - failed;
      text.append("| ")
          .append(run.method())
          .append(" | ")
          .append(run.optimizerSeed())
          .append(" | ")
          .append(complete)
          .append('/')
          .append(failed)
          .append('/')
          .append(ineligible)
          .append(" | ")
          .append(qualification == null ? "UNAVAILABLE" : qualification.status())
          .append(" | ")
          .append(qualification == null ? "UNAVAILABLE" : qualification.comparison())
          .append(" | ")
          .append(qualification == null ? "UNAVAILABLE" : qualification.feasibilityRate())
          .append(" | ")
          .append(search.measuredActiveNanos())
          .append(" | ")
          .append(finished.measuredActiveNanos() - search.measuredActiveNanos())
          .append(" | ")
          .append(finished.admissionChargedNanos())
          .append(" | ")
          .append(finished.interruptedCostUnknown())
          .append(" |\n");
      var encoded = new LinkedHashMap<String, Object>();
      encoded.put("method", run.method().name());
      encoded.put("optimizerSeed", Long.toString(run.optimizerSeed()));
      encoded.put("searchMeasuredNanos", Long.toString(search.measuredActiveNanos()));
      encoded.put("searchAdmissionNanos", Long.toString(search.admissionChargedNanos()));
      encoded.put("totalMeasuredNanos", Long.toString(finished.measuredActiveNanos()));
      encoded.put("totalAdmissionNanos", Long.toString(finished.admissionChargedNanos()));
      encoded.put("interruptedCostUnknown", finished.interruptedCostUnknown());
      encoded.put("detail", evidence.detail());
      encoded.put(
          "qualificationStatus",
          qualification == null ? "UNAVAILABLE" : qualification.status().name());
      encoded.put(
          "comparison", qualification == null ? "UNAVAILABLE" : qualification.comparison().name());
      encoded.put(
          "feasibility",
          qualification == null || qualification.feasibilityRate() == null
              ? null
              : qualification.feasibilityRate().toString());
      encoded.put(
          "baselineObjective",
          qualification == null || qualification.baselineObjective() == null
              ? null
              : definition.objective().codec().encodeValue(qualification.baselineObjective()));
      encoded.put(
          "selectedObjective",
          qualification == null || qualification.selectedObjective() == null
              ? null
              : definition.objective().codec().encodeValue(qualification.selectedObjective()));
      encoded.put(
          "heldOutEvaluations",
          qualification == null
              ? List.of()
              : qualification.evaluations().stream().map(CampaignReport::evaluation).toList());
      encodedRuns.add(StoreJson.immutableObject(encoded));
    }
    text.append(
            "\nAll exact final objective values and evaluation statuses are in `campaign-results.json`; ")
        .append(
            "individual study journals/reports retain active accounting, failures, restart markers and export evidence.\n");
    for (var run : runs) {
      text.append("\n## ")
          .append(run.method())
          .append(" / ")
          .append(run.optimizerSeed())
          .append("\n\n")
          .append(run.evidence().detail())
          .append("\n\n");
      var evidence = run.evidence();
      boolean searchOver =
          evidence.search().admissionChargedNanos() > protocol.searchAllocation().toNanos();
      long qualificationCharge =
          evidence.finished().admissionChargedNanos() - evidence.search().admissionChargedNanos();
      if (searchOver || qualificationCharge > protocol.qualificationAllocation().toNanos()) {
        text.append(
            "ALLOCATION OVERRUN: preserve this result and its observed excess; do not assert exact cap compliance.\n\n");
      }
      text.append(
              "Training progression uses cumulative measured evaluation work as a LOWER BOUND on consumed compute; ")
          .append(
              "setup, warmup and sampler overhead are additionally included in total active accounting above.\n\n")
          .append(
              "| Trial | Cumulative evaluation-work ns (lower bound) | Status | Best complete objective so far |\n")
          .append("|---:|---:|---|---|\n");
      long work = 0;
      greycos.solver.greybay.api.objective.ObjectiveValue best = null;
      for (var trial : evidence.search().trials()) {
        for (var evaluation : trial.evaluations()) {
          work =
              Math.addExact(
                  work,
                  Math.addExact(
                      Math.max(0, evaluation.solveElapsedNanos()),
                      Math.addExact(
                          Math.max(0, evaluation.preparationElapsedNanos()),
                          Math.max(0, evaluation.assessmentElapsedNanos()))));
        }
        if (trial.eligible()
            && (best == null || definition.objective().compare(trial.objective(), best) > 0)) {
          best = trial.objective();
        }
        text.append("| ")
            .append(trial.trialId())
            .append(" | ")
            .append(work)
            .append(" | ")
            .append(trial.status())
            .append(" | ")
            .append(best == null ? "UNAVAILABLE" : definition.objective().codec().encodeValue(best))
            .append(" |\n");
      }
      var qualification = evidence.qualification();
      if (qualification != null) {
        long expected =
            definition.heldOutPanel().size() * (qualification.selectedTrialId() == 0 ? 1 : 2);
        long completeUnique = 0;
        long completeSelected = 0;
        long feasibleSelected = 0;
        for (var instance : definition.heldOutPanel().problemIds()) {
          for (long seed : definition.heldOutPanel().seeds()) {
            var selected = cell(qualification, false, instance, seed);
            if (selected != null && selected.eligible()) {
              completeUnique++;
              completeSelected++;
              if (selected.score() != null && selected.score().isFeasible()) {
                feasibleSelected++;
              }
            }
            if (qualification.selectedTrialId() != 0) {
              var baseline = cell(qualification, true, instance, seed);
              if (baseline != null && baseline.eligible()) {
                completeUnique++;
              }
            }
          }
        }
        text.append("\nHeld-out completeness rate: ")
            .append(completeUnique)
            .append('/')
            .append(expected)
            .append(" unique predeclared cells. Selected feasibility among complete cells: ")
            .append(feasibleSelected)
            .append('/')
            .append(completeSelected)
            .append(
                "; missing cells have no feasibility value. Nominal held-out attempt solver nanos: ")
            .append(
                BigInteger.valueOf(qualification.evaluations().size())
                    .multiply(BigInteger.valueOf(definition.solveDuration().toNanos())))
            .append(". Qualification detail: ")
            .append(qualification.detail())
            .append("\n");
      }
    }
    text.append("\n## Independent paired uncertainty\n\n")
        .append(
            "Positive differences favor the left comparator. Native score-level units are unchanged; these ")
        .append(
            "intervals are separate from feasibility and from the lexicographic study decision. ")
        .append(
            "Each bootstrap draw resamples an INSTANCE with all paired optimizer/solver seeds together. ")
        .append(
            "Seeds are repeated measurements, not additional independent problems. Resamples: 10000; seed: ")
        .append(protocol.bootstrapSeed())
        .append(".\n");
    appendComparison(
        text,
        frozen,
        runs,
        "TPE - RANDOM",
        CampaignHarness.Method.TPE,
        CampaignHarness.Method.RANDOM);
    appendComparison(
        text, frozen, runs, "TPE - its fixed baseline", CampaignHarness.Method.TPE, null);
    appendComparison(
        text, frozen, runs, "RANDOM - its fixed baseline", CampaignHarness.Method.RANDOM, null);
    atomicWrite(
        directory.resolve("campaign-results.json"),
        StoreJson.canonicalBytes(
            Map.of("manifestFingerprint", frozen.fingerprint(), "runs", encodedRuns)));
    atomicWrite(
        directory.resolve("campaign-report.md"), text.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static <Solution_, Score_ extends Score<Score_>> void appendComparison(
      StringBuilder text,
      CampaignHarness.Frozen<Solution_, Score_> frozen,
      List<CampaignHarness.Run<Score_>> runs,
      String label,
      CampaignHarness.Method leftMethod,
      CampaignHarness.Method rightMethod) {
    text.append("\n### ").append(label).append("\n\n");
    var pairs = new ArrayList<InstancePairedBootstrap.Pair>();
    var panel = frozen.requests().getFirst().definition().heldOutPanel();
    var adapter = frozen.requests().getFirst().definition().objective().scoreAdapter();
    for (long optimizerSeed : frozen.protocol().optimizerSeeds()) {
      var left = find(runs, leftMethod, optimizerSeed);
      var right = rightMethod == null ? left : find(runs, rightMethod, optimizerSeed);
      for (var instance : panel.problemIds()) {
        for (long solverSeed : panel.seeds()) {
          var leftCell = cell(left, false, instance, solverSeed);
          var rightCell = cell(right, rightMethod == null, instance, solverSeed);
          if (leftCell == null
              || rightCell == null
              || !leftCell.eligible()
              || !rightCell.eligible()
              || leftCell.score() == null
              || rightCell.score() == null) {
            text.append("UNAVAILABLE: the predeclared paired panel is incomplete or ineligible. ")
                .append(
                    "No failed/missing cell is imputed and no completed-case subset is substituted.\n");
            return;
          }
          pairs.add(
              new InstancePairedBootstrap.Pair(
                  instance,
                  optimizerSeed,
                  solverSeed,
                  leftCell.score().isFeasible(),
                  rightCell.score().isFeasible(),
                  adapter.levels(leftCell.score()),
                  adapter.levels(rightCell.score())));
        }
      }
    }
    var statistics = InstancePairedBootstrap.estimate(pairs, frozen.protocol().bootstrapSeed());
    text.append(statistics.estimator())
        .append(".\n\nFeasibility difference: ")
        .append(statistics.feasibility())
        .append(".\n\n");
    for (int level = 0; level < statistics.levels().size(); level++) {
      text.append("Native level ")
          .append(level)
          .append(": ")
          .append(statistics.levels().get(level))
          .append(".\n\n");
    }
    text.append(
            "| Instance | Paired seed count | Mean native-level differences | Seed minimum | Seed maximum | Feasibility difference |\n")
        .append("|---|---:|---|---|---|---|\n");
    for (var summary : statistics.instances()) {
      text.append("| ")
          .append(summary.instance())
          .append(" | ")
          .append(summary.pairedSeedCount())
          .append(" | ")
          .append(summary.meanDifferences())
          .append(" | ")
          .append(summary.minimumDifferences())
          .append(" | ")
          .append(summary.maximumDifferences())
          .append(" | ")
          .append(summary.feasibilityDifference())
          .append(" |\n");
    }
  }

  private static <Score_ extends Score<Score_>> QualificationResult<Score_> find(
      List<CampaignHarness.Run<Score_>> runs, CampaignHarness.Method method, long optimizerSeed) {
    for (var run : runs) {
      if (run.method() == method && run.optimizerSeed() == optimizerSeed) {
        return run.evidence().qualification();
      }
    }
    return null;
  }

  private static <Score_ extends Score<Score_>> EvaluationRecord<Score_> cell(
      QualificationResult<Score_> result, boolean baseline, String problemId, long seed) {
    if (result == null) {
      return null;
    }
    String stage =
        baseline || result.selectedTrialId() == 0 ? "HELD_OUT_BASELINE" : "HELD_OUT_SELECTED";
    return result.evaluations().stream()
        .filter(
            cell ->
                cell.stage().equals(stage)
                    && cell.problemId().equals(problemId)
                    && cell.solverSeed() == seed)
        .max(java.util.Comparator.comparingLong(EvaluationRecord::attempt))
        .orElse(null);
  }

  private static Map<String, Object> evaluation(EvaluationRecord<?> value) {
    var encoded = new LinkedHashMap<String, Object>();
    encoded.put("id", value.evaluationId());
    encoded.put("stage", value.stage());
    encoded.put("instance", value.problemId());
    encoded.put("solverSeed", Long.toString(value.solverSeed()));
    encoded.put("attempt", Long.toString(value.attempt()));
    encoded.put("status", value.status().name());
    encoded.put("score", value.score() == null ? null : value.score().toString());
    encoded.put("solveElapsedNanos", Long.toString(value.solveElapsedNanos()));
    encoded.put("preparationElapsedNanos", Long.toString(value.preparationElapsedNanos()));
    encoded.put("assessmentElapsedNanos", Long.toString(value.assessmentElapsedNanos()));
    encoded.put("failureType", value.failureType());
    encoded.put("failureMessage", value.failureMessage());
    return encoded;
  }

  private static void atomicWrite(Path target, byte[] bytes) throws IOException {
    var temporary = Files.createTempFile(target.getParent(), ".campaign-", ".tmp");
    try {
      Files.write(temporary, bytes);
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
