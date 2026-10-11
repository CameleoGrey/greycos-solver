package greycos.solver.greybay.impl.study;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.EvaluationRecord;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.ExportResult;
import greycos.solver.greybay.api.QualificationResult;
import greycos.solver.greybay.api.StudyResult;
import greycos.solver.greybay.api.TrialResult;
import greycos.solver.greybay.api.TrialStatus;
import greycos.solver.greybay.api.objective.BalancedObjectiveValue;
import greycos.solver.greybay.api.objective.ExactRational;
import greycos.solver.greybay.api.objective.ObjectiveValue;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.impl.config.FrozenSolverConfig;
import greycos.solver.greybay.impl.evaluation.TrialEvaluator;
import greycos.solver.greybay.impl.persistence.StoreJson;
import greycos.solver.greybay.impl.persistence.StudyStore;

/**
 * Materializes a frozen selection and its reconstructable evidence without running another solve.
 */
final class StudyExporter {
  private StudyExporter() {}

  static <Solution_, Score_ extends Score<Score_>> ExportResult export(
      Path destination,
      StudyStore store,
      GreybayStudyDefinition<Solution_, Score_> definition,
      StudyResult<Score_> study,
      QualificationResult<Score_> qualification,
      List<EvaluationRecord<Score_>> observations,
      List<Map<String, Object>> progression,
      byte[] selectedXml) {
    if (study.frozenSelectionTrialId() == null)
      throw new IllegalStateException("Export requires a frozen selection.");
    var selected =
        study.trials().stream()
            .filter(trial -> trial.trialId() == study.frozenSelectionTrialId())
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Frozen selection is missing from study trials."));
    if (!selected.eligible())
      throw new IllegalStateException("Frozen selection must be an eligible completed trial.");
    if (qualification != null && qualification.selectedTrialId() != selected.trialId()) {
      throw new IllegalArgumentException("Qualification refers to a different frozen selection.");
    }
    var baseline =
        study.trials().stream()
            .filter(trial -> trial.trialId() == 0)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Export requires the calibration baseline."));
    long seed = definition.trainingPanel().seeds().getFirst();
    String selectionIdentity =
        StoreJson.sha256(
            StoreJson.canonicalBytes(
                Map.of(
                    "studyId",
                    study.studyId(),
                    "trialId",
                    Long.toString(selected.trialId()),
                    "candidateIdentity",
                    selected.candidateIdentity(),
                    "exampleSeed",
                    Long.toString(seed))));
    try {
      Path target =
          ExportFiles.destination(destination, store, definition.problemAdapter().inputPaths());
      var recovered = ExportFiles.recover(target, store, selectionIdentity);
      if (recovered != null) {
        return ExportFiles.install(
            target,
            store,
            definition.problemAdapter().inputPaths(),
            selectionIdentity,
            recovered,
            true);
      }
      byte[] xml = verifiedXml(definition, selected, selectedXml, seed);
      var evidence =
          evidence(
              store,
              definition,
              study,
              qualification,
              observations,
              progression,
              baseline,
              selected);
      var artifacts = new LinkedHashMap<String, byte[]>();
      artifacts.put(ExportFiles.XML, xml);
      artifacts.put(
          ExportFiles.CSV,
          csv(store, definition, study, observations).getBytes(StandardCharsets.UTF_8));
      artifacts.put(
          ExportFiles.REPORT,
          report(
                  definition,
                  study,
                  qualification,
                  baseline,
                  selected,
                  evidence,
                  observations,
                  progression)
              .getBytes(StandardCharsets.UTF_8));
      var manifest = new LinkedHashMap<String, Object>(evidence);
      manifest.put("schemaVersion", "1");
      manifest.put("selectionIdentity", selectionIdentity);
      manifest.put("studyId", study.studyId());
      manifest.put("selectedTrialId", Long.toString(selected.trialId()));
      manifest.put("selectedCandidateIdentity", selected.candidateIdentity());
      manifest.put("exampleSeed", Long.toString(seed));
      manifest.put(
          "seedRole", "first training seed; reproducible example seed, not an optimized seed");
      var digests = new LinkedHashMap<String, Object>();
      artifacts.forEach((name, bytes) -> digests.put(name, StoreJson.sha256(bytes)));
      manifest.put("artifactDigests", digests);
      artifacts.put(ExportFiles.MANIFEST, StoreJson.canonicalBytes(manifest));
      return ExportFiles.install(
          target,
          store,
          definition.problemAdapter().inputPaths(),
          selectionIdentity,
          artifacts,
          false);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to export frozen selection to (" + destination + ").", e);
    }
  }

  private static byte[] verifiedXml(
      GreybayStudyDefinition<?, ?> definition, TrialResult<?> selected, byte[] source, long seed) {
    var provider = definition.runtimeReconstructionProvider();
    var providerIdentity = provider == null ? null : provider.identity();
    var config =
        SolverConfig.createFromXmlReader(
            new StringReader(new String(source, StandardCharsets.UTF_8)));
    if (!selected
        .candidateIdentity()
        .equals(FrozenSolverConfig.canonicalIdentity(config, providerIdentity))) {
      throw new IllegalArgumentException(
          "Selected XML differs from the frozen candidate identity.");
    }
    config.setRandomSeed(seed);
    if (provider != null) provider.reconstruct(config);
    if (!selected
        .candidateIdentity()
        .equals(FrozenSolverConfig.canonicalIdentity(config, providerIdentity))) {
      throw new IllegalArgumentException(
          "Runtime reconstruction changed selected XML-visible behavior.");
    }
    var frozen = FrozenSolverConfig.capture(config, provider);
    var reloaded = frozen.freshConfig();
    if (!Objects.equals(reloaded.getRandomSeed(), seed)
        || reloaded.getTerminationConfig() == null
        || !definition.solveDuration().equals(reloaded.getTerminationConfig().getSpentLimit())) {
      throw new IllegalArgumentException(
          "Reloaded selected XML changed the example seed or scored horizon.");
    }
    var solver = SolverFactory.create(reloaded).buildSolver();
    try {
      return frozen.xml().getBytes(StandardCharsets.UTF_8);
    } finally {
      TrialEvaluator.disposeUninvoked(solver);
    }
  }

  private static <Score_ extends Score<Score_>> Map<String, Object> evidence(
      StudyStore store,
      GreybayStudyDefinition<?, Score_> definition,
      StudyResult<Score_> study,
      QualificationResult<Score_> qualification,
      List<EvaluationRecord<Score_>> observations,
      List<Map<String, Object>> progression,
      TrialResult<Score_> baseline,
      TrialResult<Score_> selected) {
    var result = new LinkedHashMap<String, Object>();
    result.put("definitionManifest", store.manifest());
    var records = store.records();
    result.put(
        "journalSequence", records.isEmpty() ? "0" : Long.toString(records.getLast().sequence()));
    result.put("journalHash", records.isEmpty() ? null : records.getLast().hash());
    result.put("state", study.state().name());
    result.put("trainingStatus", study.trainingStatus().name());
    result.put("baselineCandidateIdentity", baseline.candidateIdentity());
    result.put("trainingIncumbent", trial(definition, selected));
    result.put("trials", study.trials().stream().map(value -> trial(definition, value)).toList());
    result.put("baselineObjective", objective(definition, baseline.objective()));
    result.put("selectedObjective", objective(definition, selected.objective()));
    result.put("trainingFeasibility", feasible(selected.evaluations(), definition.trainingPanel()));
    var counts = new LinkedHashMap<String, Object>();
    for (var status : TrialStatus.values())
      counts.put(
          status.name(),
          Long.toString(study.trials().stream().filter(trial -> trial.status() == status).count()));
    result.put("trialCounts", counts);
    var qualificationFields = new LinkedHashMap<String, Object>();
    qualificationFields.put(
        "status", qualification == null ? "INCONCLUSIVE" : qualification.status().name());
    qualificationFields.put(
        "comparison", qualification == null ? "UNAVAILABLE" : qualification.comparison().name());
    qualificationFields.put(
        "detail",
        qualification == null
            ? "Selection frozen; qualification has not completed."
            : qualification.detail());
    qualificationFields.put(
        "feasibilityRate",
        qualification == null || qualification.feasibilityRate() == null
            ? null
            : fraction(qualification.feasibilityRate()));
    qualificationFields.put(
        "baselineObjective",
        qualification == null ? null : objective(definition, qualification.baselineObjective()));
    qualificationFields.put(
        "selectedObjective",
        qualification == null ? null : objective(definition, qualification.selectedObjective()));
    result.put("qualification", qualificationFields);
    result.put(
        "exportSnapshotSemantics",
        "immutable journal snapshot; reusing this destination returns the same artifacts; "
            + "export later qualification evidence to a new destination");
    result.put("progression", progression);
    result.put("pairedInstanceDifferences", paired(definition, observations, selected.trialId()));
    result.put("seedVariability", variability(definition, observations));
    result.put(
        "costAccounting",
        Map.of(
            "measuredActiveNanos",
            Long.toString(study.measuredActiveNanos()),
            "admissionChargedNanos",
            Long.toString(study.admissionChargedNanos()),
            "interruptedCostUnknown",
            study.interruptedCostUnknown(),
            "snapshotScope",
            "committed accounting snapshot at the named journal position; excludes the current export operation"));
    result.put(
        "restartAndWarmupMarkers",
        records.stream()
            .filter(
                record ->
                    record.kind().equals("PROCESS_STARTED")
                        || record.kind().equals("WARMUP_COMPLETE")
                        || record.kind().equals("ACTIVE_INTERRUPTED")
                        || record.kind().equals("EVALUATION_RECOVERED"))
            .map(
                record ->
                    Map.of(
                        "sequence",
                        Long.toString(record.sequence()),
                        "kind",
                        record.kind(),
                        "payload",
                        record.payload()))
            .toList());
    result.put(
        "activeStageAccounting",
        records.stream()
            .filter(
                record ->
                    record.kind().equals("ACTIVE_COMPLETED")
                        || record.kind().equals("ACTIVE_INTERRUPTED"))
            .map(record -> record.payload())
            .toList());
    result.put(
        "calibrations",
        records.stream()
            .filter(
                record ->
                    record.kind().equals("CALIBRATION")
                        || record.kind().equals("HELD_OUT_CALIBRATION"))
            .map(
                record ->
                    Map.of(
                        "stage",
                        record.kind(),
                        "encodedCalibration",
                        record.payload().get("value")))
            .toList());
    result.put(
        "evaluationArtifacts",
        records.stream()
            .filter(record -> record.kind().equals("EVALUATION_COMPLETED"))
            .map(record -> record.payload())
            .toList());
    var base = definition.baseSolverConfig();
    result.put(
        "instrumentation",
        Map.of(
            "logging",
            "application logging configuration; phase logging retained in selected XML",
            "monitoring",
            base.getMonitoringConfig() == null
                    || base.getMonitoringConfig().getSolverMetricList() == null
                ? List.of("default")
                : base.getMonitoringConfig().getSolverMetricList().stream()
                    .map(Enum::name)
                    .toList(),
            "cutoff",
            "on-time synchronous best-solution snapshot; selectedObserverNanos <= solve horizon",
            "nativeScoreAssessment",
            "fresh score assessment after solving",
            "businessVerifier",
            definition
                .problemAdapter()
                .verifier()
                .map(value -> value.identity().id())
                .orElse("NOT_CONFIGURED")));
    return result;
  }

  private static Map<String, Object> trial(
      GreybayStudyDefinition<?, ?> definition, TrialResult<?> trial) {
    var result = new LinkedHashMap<String, Object>();
    result.put("trialId", Long.toString(trial.trialId()));
    result.put("candidateIdentity", trial.candidateIdentity());
    result.put("status", trial.status().name());
    result.put("activeParameters", trial.assignment() == null ? null : trial.assignment().toMap());
    result.put("objective", objective(definition, trial.objective()));
    result.put("failure", trial.failure());
    return result;
  }

  private static Object objective(GreybayStudyDefinition<?, ?> definition, ObjectiveValue value) {
    if (value == null) return null;
    var result = new LinkedHashMap<String, Object>();
    String encoded = definition.objective().codec().encodeValue(value);
    result.put("encodedValue", encoded);
    if (value instanceof BalancedObjectiveValue balanced) {
      result.put("feasibilityRate", fraction(balanced.feasibilityRate()));
      result.put(
          "normalizedGains",
          balanced.normalizedGains().stream().map(StudyExporter::fraction).toList());
    }
    return result;
  }

  private static Map<String, Object> fraction(ExactRational value) {
    return Map.of(
        "numerator",
        value.numerator().toString(),
        "denominator",
        value.denominator().toString(),
        "roundedDisplay",
        value.toBigDecimal(MathContext.DECIMAL64).toPlainString());
  }

  private static Map<String, Object> feasible(
      List<? extends EvaluationRecord<?>> records, ProblemPanel panel) {
    long feasible =
        records.stream()
            .filter(
                record ->
                    record.eligible() && record.score() != null && record.score().isFeasible())
            .map(EvaluationRecord::evaluationId)
            .distinct()
            .count();
    return Map.of(
        "feasibleCells",
        Long.toString(feasible),
        "expectedCells",
        Long.toString(panel.size()),
        "fraction",
        fraction(ExactRational.of(feasible, panel.size())));
  }

  private static <Score_ extends Score<Score_>> String csv(
      StudyStore store,
      GreybayStudyDefinition<?, Score_> definition,
      StudyResult<Score_> study,
      List<EvaluationRecord<Score_>> observations) {
    var metadata = new LinkedHashMap<List<String>, Map<String, Object>>();
    var journal = store.records();
    var elapsedLowerBounds = new LinkedHashMap<List<String>, Long>();
    for (var journalRecord : journal) {
      if (journalRecord.kind().equals("EVALUATION_STARTED")) {
        metadata.put(
            List.of(
                (String) journalRecord.payload().get("evaluationId"),
                (String) journalRecord.payload().get("attempt")),
            journalRecord.payload());
      }
      if (journalRecord.kind().equals("ACTIVE_HEARTBEAT")
          && journalRecord.payload().get("evaluationId") instanceof String evaluationId) {
        elapsedLowerBounds.merge(
            List.of((String) journalRecord.payload().get("operationId"), evaluationId),
            Long.parseLong((String) journalRecord.payload().get("evaluationElapsedNanos")),
            Math::max);
      }
    }
    // Recovery markers are exported as explicitly unmeasured attempts, never added to scored
    // observations.
    var exportedAttempts = new ArrayList<>(observations);
    var unknownCosts = new java.util.HashSet<List<String>>();
    for (var journalRecord : journal) {
      boolean recovered = journalRecord.kind().equals("EVALUATION_RECOVERED");
      if (!recovered && !journalRecord.kind().equals("EVALUATION_MEASUREMENT_FAILED")) continue;
      var payload = journalRecord.payload();
      String evaluationId = (String) payload.get("evaluationId");
      long attempt = Long.parseLong((String) payload.get("attempt"));
      var key = List.of(evaluationId, Long.toString(attempt));
      if (exportedAttempts.stream()
          .anyMatch(
              record -> record.evaluationId().equals(evaluationId) && record.attempt() == attempt))
        continue;
      var started = metadata.get(key);
      if (started == null)
        throw new IllegalStateException("Unmeasured attempt is missing its start record: " + key);
      exportedAttempts.add(
          new EvaluationRecord<>(
              evaluationId,
              (String) started.get("stage"),
              Long.parseLong((String) started.get("trialId")),
              (String) started.get("problemId"),
              Long.parseLong((String) started.get("solverSeed")),
              attempt,
              recovered ? EvaluationStatus.INTERRUPTED : EvaluationStatus.FAILED,
              null,
              -1,
              -1,
              -1,
              -1,
              -1,
              -1,
              null,
              recovered ? "PROCESS_INTERRUPTION" : (String) payload.get("type"),
              (String) payload.get(recovered ? "reason" : "message")));
      unknownCosts.add(key);
    }
    var output = new StringBuilder();
    row(
        output,
        List.of(
            "evaluationId",
            "stage",
            "trialId",
            "candidateIdentity",
            "problemId",
            "solverSeed",
            "attempt",
            "status",
            "scoreType",
            "nativeScore",
            "nativeLevels",
            "structural",
            "initialized",
            "nativeFeasible",
            "missingRequiredAssignmentCount",
            "businessVerification",
            "verificationMessage",
            "verificationMetrics",
            "budgetNanos",
            "graceNanos",
            "selectedObserverNanos",
            "selectedSolverMillis",
            "solveElapsedNanos",
            "overrunNanos",
            "preparationElapsedNanos",
            "assessmentElapsedNanos",
            "failureType",
            "failureMessage",
            "problemFingerprint",
            "startedAt",
            "costUnknown",
            "attemptElapsedLowerBoundNanos"));
    for (var record : exportedAttempts) {
      var key = List.of(record.evaluationId(), Long.toString(record.attempt()));
      var context = metadata.getOrDefault(key, Map.of());
      long budget =
          context.get("horizonNanos") instanceof String configuredBudget
              ? Long.parseLong(configuredBudget)
              : horizon(definition, record.stage());
      boolean costUnknown = unknownCosts.contains(key);
      Long elapsedLowerBound =
          costUnknown
              ? elapsedLowerBounds.getOrDefault(
                  List.of((String) context.get("operationId"), record.evaluationId()), 0L)
              : null;
      var values = new ArrayList<Object>();
      values.addAll(
          Arrays.asList(
              record.evaluationId(),
              record.stage(),
              record.trialId(),
              context.getOrDefault(
                  "candidateIdentity",
                  study.trials().stream()
                      .filter(trial -> trial.trialId() == record.trialId())
                      .map(TrialResult::candidateIdentity)
                      .findFirst()
                      .orElse(null)),
              record.problemId(),
              record.solverSeed(),
              record.attempt(),
              record.status(),
              record.score() == null ? null : record.score().getClass().getName(),
              record.score(),
              nativeLevels(record.score()),
              record.score() == null ? null : record.score().structuralScore(),
              record.missingRequiredAssignmentCount() < 0
                  ? null
                  : record.missingRequiredAssignmentCount() == 0,
              record.score() == null ? null : record.score().isFeasible(),
              nullable(record.missingRequiredAssignmentCount()),
              record.verification() == null ? null : record.verification().status(),
              record.verification() == null ? null : record.verification().message(),
              record.verification() == null ? null : json(record.verification().metrics()),
              budget,
              grace(budget),
              nullable(record.selectedObserverNanos()),
              nullable(record.selectedSolverMillis()),
              nullable(record.solveElapsedNanos()),
              record.solveElapsedNanos() < 0
                  ? null
                  : Math.max(0, record.solveElapsedNanos() - budget),
              nullable(record.preparationElapsedNanos()),
              nullable(record.assessmentElapsedNanos()),
              record.failureType(),
              record.failureMessage(),
              context.get("problemFingerprint"),
              context.get("startedAt"),
              costUnknown,
              elapsedLowerBound));
      row(output, values);
    }
    return output.toString();
  }

  private static long grace(long horizonNanos) {
    long millis = horizonNanos / 1_000_000;
    return Math.max(1_000L, millis / 20 + (millis % 20 == 0 ? 0 : 1)) * 1_000_000L;
  }

  private static Object nullable(long number) {
    return number < 0 ? null : Long.toString(number);
  }

  private static Object nativeLevels(Score<?> score) {
    if (score == null) return null;
    var levels = new ArrayList<Map<String, Object>>();
    for (Number number : score.toLevelNumbers()) {
      String representation =
          switch (number) {
            case Float value -> "0x" + Integer.toHexString(Float.floatToRawIntBits(value));
            case Double value -> "0x" + Long.toHexString(Double.doubleToRawLongBits(value));
            default -> number.toString();
          };
      levels.add(Map.of("type", number.getClass().getName(), "value", representation));
    }
    return json(Map.of("levels", levels));
  }

  private static void row(StringBuilder output, List<?> values) {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) output.append(',');
      Object value = values.get(i);
      if (value != null)
        output.append('"').append(value.toString().replace("\"", "\"\"")).append('"');
    }
    output.append("\r\n");
  }

  private static long horizon(GreybayStudyDefinition<?, ?> definition, String stage) {
    return stage.startsWith("WARMUP:")
        ? definition.warmupDuration().toNanos()
        : definition.solveDuration().toNanos();
  }

  private static <Score_ extends Score<Score_>> List<Map<String, Object>> paired(
      GreybayStudyDefinition<?, Score_> definition,
      List<EvaluationRecord<Score_>> observations,
      long selectedId) {
    var result = new ArrayList<Map<String, Object>>();
    for (boolean heldOut : List.of(false, true)) {
      var panel = heldOut ? definition.heldOutPanel() : definition.trainingPanel();
      String baselineStage = heldOut ? "HELD_OUT_BASELINE" : "TRAINING";
      String selectedStage = heldOut && selectedId != 0 ? "HELD_OUT_SELECTED" : baselineStage;
      for (String problem : panel.problemIds()) {
        var differences = new ArrayList<List<ExactRational>>();
        for (long seed : panel.seeds()) {
          var baseline = cell(observations, baselineStage, 0, problem, seed);
          var selected = cell(observations, selectedStage, selectedId, problem, seed);
          if (baseline == null || selected == null) continue;
          var left = exactLevels(definition, baseline.score());
          var right = exactLevels(definition, selected.score());
          if (left.size() != right.size()) {
            throw new IllegalArgumentException(
                "Paired scores have different exact level counts for problem (" + problem + ").");
          }
          var delta = new ArrayList<ExactRational>();
          for (int i = 0; i < left.size(); i++) delta.add(right.get(i).subtract(left.get(i)));
          differences.add(delta);
        }
        var entry = new LinkedHashMap<String, Object>();
        entry.put("panel", heldOut ? "HELD_OUT" : "TRAINING");
        entry.put("problemId", problem);
        entry.put("pairedSeeds", Integer.toString(differences.size()));
        entry.put("expectedSeeds", Integer.toString(panel.seeds().size()));
        entry.put(
            "meanSelectedMinusBaselineNativeLevels",
            differences.isEmpty()
                ? null
                : mean(differences).stream().map(StudyExporter::fraction).toList());
        entry.put("complete", differences.size() == panel.seeds().size());
        result.add(entry);
      }
    }
    return result;
  }

  private static <Score_ extends Score<Score_>> EvaluationRecord<Score_> cell(
      List<EvaluationRecord<Score_>> records, String stage, long trial, String problem, long seed) {
    return records.stream()
        .filter(
            record ->
                record.eligible()
                    && record.stage().equals(stage)
                    && record.trialId() == trial
                    && record.problemId().equals(problem)
                    && record.solverSeed() == seed)
        .findFirst()
        .orElse(null);
  }

  private static <Score_ extends Score<Score_>> List<ExactRational> exactLevels(
      GreybayStudyDefinition<?, Score_> definition, Score_ score) {
    Objects.requireNonNull(score, "Eligible measured observations require a score.");
    var adapter = definition.objective().scoreAdapter();
    var levels = List.copyOf(adapter.levels(score));
    int expectedLevels = adapter.layout(score).levelCount();
    if (levels.size() != expectedLevels) {
      throw new IllegalArgumentException(
          "Exact score adapter ("
              + adapter.identity()
              + ") returned "
              + levels.size()
              + " levels; expected "
              + expectedLevels
              + ".");
    }
    return levels;
  }

  private static List<ExactRational> mean(List<List<ExactRational>> samples) {
    var result = new ArrayList<ExactRational>();
    for (int level = 0; level < samples.getFirst().size(); level++) {
      var sum = ExactRational.ZERO;
      for (var sample : samples) sum = sum.add(sample.get(level));
      result.add(sum.divide(samples.size()));
    }
    return result;
  }

  private static <Score_ extends Score<Score_>> List<Map<String, Object>> variability(
      GreybayStudyDefinition<?, Score_> definition, List<EvaluationRecord<Score_>> observations) {
    var groups = new LinkedHashMap<List<String>, List<List<ExactRational>>>();
    for (var observation : observations) {
      if (!observation.eligible()) continue;
      var levels = exactLevels(definition, observation.score());
      groups
          .computeIfAbsent(
              List.of(
                  observation.stage(),
                  Long.toString(observation.trialId()),
                  observation.problemId()),
              ignored -> new ArrayList<>())
          .add(levels);
    }
    var result = new ArrayList<Map<String, Object>>();
    groups.forEach(
        (key, samples) -> {
          var average = mean(samples);
          var variances = new ArrayList<Map<String, Object>>();
          var minima = new ArrayList<Map<String, Object>>();
          var maxima = new ArrayList<Map<String, Object>>();
          for (int level = 0; level < average.size(); level++) {
            var variance = ExactRational.ZERO;
            var minimum = samples.getFirst().get(level);
            var maximum = minimum;
            for (var sample : samples) {
              var delta = sample.get(level).subtract(average.get(level));
              variance = variance.add(delta.multiply(delta));
              if (sample.get(level).compareTo(minimum) < 0) minimum = sample.get(level);
              if (sample.get(level).compareTo(maximum) > 0) maximum = sample.get(level);
            }
            variances.add(fraction(variance.divide(samples.size())));
            minima.add(fraction(minimum));
            maxima.add(fraction(maximum));
          }
          result.add(
              Map.of(
                  "stage",
                  key.get(0),
                  "trialId",
                  key.get(1),
                  "problemId",
                  key.get(2),
                  "observedSeeds",
                  Integer.toString(samples.size()),
                  "meanNativeLevels",
                  average.stream().map(StudyExporter::fraction).toList(),
                  "populationVarianceNativeLevels",
                  variances,
                  "minimumNativeLevels",
                  minima,
                  "maximumNativeLevels",
                  maxima));
        });
    return result;
  }

  private static <Score_ extends Score<Score_>> String report(
      GreybayStudyDefinition<?, Score_> definition,
      StudyResult<Score_> study,
      QualificationResult<Score_> qualification,
      TrialResult<Score_> baseline,
      TrialResult<Score_> selected,
      Map<String, Object> evidence,
      List<EvaluationRecord<Score_>> observations,
      List<Map<String, Object>> progression) {
    var output = new StringBuilder("# Greybay study report\n\n");
    output
        .append("Study: ")
        .append(code(study.studyId()))
        .append(". Frozen training selection: trial ")
        .append(selected.trialId())
        .append(".\n\nQualification: **")
        .append(qualification == null ? "INCONCLUSIVE" : qualification.status())
        .append("**; comparison: ")
        .append(qualification == null ? "UNAVAILABLE" : qualification.comparison())
        .append(".\n\n")
        .append("The selected XML uses the first training seed, ")
        .append(definition.trainingPanel().seeds().getFirst())
        .append(", as a reproducible example seed, not an optimized seed. Its scored horizon is ")
        .append(definition.solveDuration())
        .append("; the scored pipeline caps are preserved.\n\n")
        .append("Baseline configuration: ")
        .append(code(baseline.candidateIdentity()))
        .append(".\n\n")
        .append("Selected configuration: ")
        .append(code(selected.candidateIdentity()))
        .append(".\n\n");
    section(
        output,
        "Search space and active parameters",
        Map.of(
            "templates",
            definition.templates().stream()
                .map(
                    template ->
                        Map.of(
                            "identity",
                            StudyManifest.identity(template.identity()),
                            "searchSpace",
                            template.searchSpace().toMap()))
                .toList(),
            "selected",
            selected.assignment() == null
                ? Map.of("baseline", true)
                : selected.assignment().toMap()));
    section(
        output,
        "Trial outcomes and feasible fraction",
        Map.of(
            "counts",
            evidence.get("trialCounts"),
            "trainingFeasibility",
            evidence.get("trainingFeasibility")));
    section(
        output,
        "Exact aggregate values and rounded display",
        Map.of(
            "baseline",
            evidence.get("baselineObjective"),
            "selected",
            evidence.get("selectedObjective")));
    section(output, "Independent held-out qualification", evidence.get("qualification"));
    section(
        output,
        "Per-instance paired native differences",
        evidence.get("pairedInstanceDifferences"));
    output
        .append(
            "Differences are selected minus baseline, paired by instance and seed. Partial pairs are labeled; ")
        .append("exact level conversion uses the registered objective score adapter.\n\n");
    section(output, "Seed variability", evidence.get("seedVariability"));
    output.append(
        "Variances describe the observed seed panel (population denominator); they are not confidence intervals.\n\n");
    output
        .append("## Progression against consumed compute\n\n")
        .append(
            "| Trial | Status | Measured/lower-bound active ns | Admission charged ns | Exact objective |\n")
        .append("| --- | --- | --- | --- | --- |\n");
    for (var entry : progression)
      output
          .append("| ")
          .append(cellText(entry.get("trialId")))
          .append(" | ")
          .append(cellText(entry.get("status")))
          .append(" | ")
          .append(cellText(entry.get("measuredActiveNanos")))
          .append(" | ")
          .append(cellText(entry.get("admissionChargedNanos")))
          .append(" | ")
          .append(cellText(entry.get("objective")))
          .append(" |\n");
    output.append('\n');
    section(output, "Timing and cost accounting", evidence.get("costAccounting"));
    long overrunCount =
        observations.stream()
            .filter(record -> record.solveElapsedNanos() > horizon(definition, record.stage()))
            .count();
    output
        .append("Committed measured attempts: ")
        .append(observations.size())
        .append("; attempts exceeding the solve horizon: ")
        .append(overrunCount)
        .append("; budget-exceeded outcomes: ")
        .append(
            observations.stream()
                .filter(record -> record.status() == EvaluationStatus.BUDGET_EXCEEDED)
                .count())
        .append(
            ". Per-attempt preparation, solve, assessment, cutoff timestamps and overruns are in evaluations.csv.\n\n")
        .append(
            "Crash-interrupted attempts and measurement failures without an observation are included in the CSV with ")
        .append(
            "unknown-cost markers and durable attempt elapsed lower bounds. Missing measurements are never zero scores.\n\n")
        .append(
            "Interrupted cost is a lower bound when its final duration is unknown. Admission charges are an ")
        .append(
            "operational policy, not a proven upper bound on consumed compute. The committed accounting snapshot excludes the ")
        .append("current export operation; the journal records that operation on completion.\n\n");
    section(output, "Active stage accounting", evidence.get("activeStageAccounting"));
    section(output, "Instrumentation", evidence.get("instrumentation"));
    section(output, "Process restart and warmup markers", evidence.get("restartAndWarmupMarkers"));
    output
        .append("## Interpretation and durability\n\n")
        .append(
            "Qualification is evidence on the declared held-out panel. It establishes neither statistical ")
        .append(
            "significance, global optimality, parameter causality nor performance on future instances. ")
        .append("No sampler-derived feature importance is reported.\n\n")
        .append(
            "This export is an immutable snapshot at the journal position in selection.json. Reusing its destination ")
        .append(
            "returns the same snapshot; export later qualification evidence to a new destination.\n\n")
        .append(
            "XML reconstructs configuration, not solver continuation state. Runtime reconstruction provider ")
        .append(
            "identity and application artifacts are recorded in selection.json. Timed reruns can produce ")
        .append(
            "different scores and later proposals. Process-crash recovery requires supported local filesystem ")
        .append(
            "locking and atomic moves; power-loss guarantees depend on filesystem synchronization semantics.\n");
    return output.toString();
  }

  private static String json(Map<String, ?> value) {
    var copy = new LinkedHashMap<String, Object>();
    value.forEach(copy::put);
    return new String(StoreJson.canonicalBytes(copy), StandardCharsets.UTF_8);
  }

  private static void section(StringBuilder output, String title, Object value) {
    String content = json(Map.of("value", value));
    // Indented code keeps arbitrary labels, backticks and line breaks from altering Markdown
    // structure.
    output
        .append("## ")
        .append(title)
        .append("\n\n    ")
        .append(content.replace("\n", "\n    "))
        .append("\n\n");
  }

  private static String code(String value) {
    return "`" + value.replace("`", "&#96;").replace("\n", " ") + "`";
  }

  private static String cellText(Object value) {
    return Objects.toString(value, "unavailable")
        .replace("|", "&#124;")
        .replace("\n", " ")
        .replace("\r", " ");
  }
}
