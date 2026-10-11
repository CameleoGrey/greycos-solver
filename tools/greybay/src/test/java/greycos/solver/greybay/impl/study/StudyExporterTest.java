package greycos.solver.greybay.impl.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.EvaluationRecord;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.ExportResult;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.ProblemDescriptor;
import greycos.solver.greybay.api.QualificationComparison;
import greycos.solver.greybay.api.QualificationResult;
import greycos.solver.greybay.api.QualificationStatus;
import greycos.solver.greybay.api.RuntimeReconstructionProvider;
import greycos.solver.greybay.api.StudyResult;
import greycos.solver.greybay.api.StudyState;
import greycos.solver.greybay.api.TrainingStatus;
import greycos.solver.greybay.api.TrialResult;
import greycos.solver.greybay.api.TrialStatus;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.api.objective.BalancedObjectiveValue;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.api.objective.ExactRational;
import greycos.solver.greybay.api.objective.ExactScoreAdapter;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.RandomSamplerConfig;
import greycos.solver.greybay.examples.GreybayWorkflowExample;
import greycos.solver.greybay.examples.SchedulingExample;
import greycos.solver.greybay.impl.config.FrozenSolverConfig;
import greycos.solver.greybay.impl.persistence.StoreJson;
import greycos.solver.greybay.impl.persistence.StudyStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyExporterTest {
  @TempDir Path directory;

  @Test
  void exportsFrozenSelectionWithExactEvidenceAndFirstSeedAndStableDigests() throws Exception {
    var fixture = fixture(List.of());
    try (var store = fixture.store()) {
      store.append("PROCESS_STARTED", Map.of("session", "2", "resumed", true));
      store.append("WARMUP_COMPLETE", Map.of("session", "2", "disabled", true));
      var result = fixture.export(directory.resolve("selected"));
      var config = SolverConfig.createFromXmlFile(result.solverConfigPath().toFile());
      assertThat(config.getRandomSeed()).isEqualTo(11L);
      assertThat(config.getTerminationConfig().getSpentLimit()).isEqualTo(Duration.ofSeconds(1));
      assertThat(FrozenSolverConfig.canonicalIdentity(config, null))
          .isEqualTo(fixture.selected().candidateIdentity());
      var manifest = StoreJson.readObject(Files.readAllBytes(result.selectionManifestPath()));
      assertThat(manifest)
          .containsEntry("schemaVersion", "1")
          .containsEntry("selectedTrialId", "1")
          .containsEntry("exampleSeed", "11");
      assertThat(object(manifest.get("qualification")))
          .containsEntry("status", "NO_HELD_OUT_IMPROVEMENT")
          .containsEntry("comparison", "WORSE");
      assertThat(object(manifest.get("selectedObjective")).get("encodedValue"))
          .isEqualTo(
              fixture.definition().objective().codec().encodeValue(fixture.selected().objective()));
      assertThat(Files.readString(result.reportPath()))
          .contains(
              "reproducible example seed",
              "population denominator",
              "Progression against consumed compute",
              "10000000001",
              "Active stage accounting",
              "PROCESS_STARTED",
              "immutable snapshot",
              "NO_HELD_OUT_IMPROVEMENT");
      for (var entry : result.artifactDigests().entrySet()) {
        assertThat(
                StoreJson.sha256(
                    Files.readAllBytes(result.reportPath().resolveSibling(entry.getKey()))))
            .isEqualTo(entry.getValue());
      }
      assertThat(object(manifest.get("artifactDigests")))
          .containsKeys("selected-solver.xml", "evaluations.csv", "report.md");
      var again = fixture.export(directory.resolve("selected"));
      assertThat(again.artifactDigests()).isEqualTo(result.artifactDigests());
      assertThat(store.records().stream().filter(record -> record.kind().equals("EXPORT_PREPARED")))
          .hasSize(1);
    }
  }

  @Test
  void computesPairedDifferencesAndSeedVarianceWithoutRoundingLongScores() throws Exception {
    var fixture = fixture(List.of());
    try (var ignored = fixture.store()) {
      var exported = fixture.export(directory.resolve("selected"));
      var manifest = StoreJson.readObject(Files.readAllBytes(exported.selectionManifestPath()));
      var paired = list(manifest.get("pairedInstanceDifferences"));
      var training = object(paired.getFirst());
      assertThat(training).containsEntry("pairedSeeds", "2").containsEntry("complete", true);
      var levels = list(training.get("meanSelectedMinusBaselineNativeLevels"));
      assertThat(object(levels.get(1)))
          .containsEntry("numerator", "3")
          .containsEntry("denominator", "2");
      var variability =
          list(manifest.get("seedVariability")).stream()
              .map(StudyExporterTest::object)
              .filter(
                  value ->
                      value.get("trialId").equals("1") && value.get("stage").equals("TRAINING"))
              .findFirst()
              .orElseThrow();
      var variance = list(variability.get("populationVarianceNativeLevels"));
      assertThat(object(variance.get(1)))
          .containsEntry("numerator", "1")
          .containsEntry("denominator", "4");
      assertThat(Files.readString(exported.evaluationsCsvPath()))
          .contains(
              "-9007199254740993",
              "java.lang.Long",
              "java.lang.IllegalStateException",
              "line one,\"\"quoted\"\"\nline two");
    }
  }

  @Test
  void exportsMissingScoresAsEmptyFieldsAndExplicitInconclusiveQualification() throws Exception {
    var fixture = fixture(List.of());
    try (var ignored = fixture.store()) {
      var result =
          StudyExporter.export(
              directory.resolve("pending"),
              fixture.store(),
              fixture.definition(),
              fixture.study(),
              null,
              fixture.observations(),
              fixture.progression(),
              fixture.xml());
      var manifest = StoreJson.readObject(Files.readAllBytes(result.selectionManifestPath()));
      assertThat(object(manifest.get("qualification")))
          .containsEntry("status", "INCONCLUSIVE")
          .containsEntry("comparison", "UNAVAILABLE")
          .containsEntry("selectedObjective", null);
      String csv = Files.readString(result.evaluationsCsvPath());
      assertThat(csv).contains("\"FAILED\",,,,,,,");
      assertThat(csv).contains("\"BUDGET_EXCEEDED\"");
      assertThat(csv).contains("\"40000000000\",\"2000000000\"");
    }
  }

  @Test
  void incompleteExportRegeneratesFromDurableArtifactsIncludingTornTemporary() throws Exception {
    var fixture = fixture(List.of());
    try (var store = fixture.store()) {
      var destination = directory.resolve("selected");
      var first = fixture.export(destination);
      Files.delete(first.selectionManifestPath());
      Files.delete(first.reportPath());
      var temporary =
          first
              .reportPath()
              .resolveSibling(
                  ".report.md." + first.artifactDigests().get("report.md").substring(7) + ".tmp");
      Files.writeString(temporary, "partial");
      var regenerated = fixture.export(destination);
      assertThat(regenerated.artifactDigests()).isEqualTo(first.artifactDigests());
      assertThat(temporary).doesNotExist();
      assertThat(regenerated.reportPath()).isRegularFile();
      assertThat(store.records().stream().filter(record -> record.kind().equals("EXPORT_PREPARED")))
          .hasSize(1);
    }
  }

  @Test
  void refusesUnrelatedFilesAndModifiedCommittedArtifacts() throws Exception {
    var fixture = fixture(List.of());
    try (var ignored = fixture.store()) {
      var unrelated = Files.createDirectory(directory.resolve("unrelated"));
      var original = Files.writeString(unrelated.resolve("notes.txt"), "preserve me");
      assertThatThrownBy(() -> fixture.export(unrelated))
          .isInstanceOf(UncheckedIOException.class)
          .hasRootCauseMessage(
              "Refusing to overwrite pre-existing unrelated export files (" + unrelated + ").");
      assertThat(Files.readString(original)).isEqualTo("preserve me");
      var exported = fixture.export(directory.resolve("selected"));
      Files.writeString(exported.reportPath(), "modified");
      assertThatThrownBy(() -> fixture.export(exported.reportPath().getParent()))
          .isInstanceOf(UncheckedIOException.class)
          .hasRootCauseMessage(
              "Refusing to overwrite unrelated or modified export artifact ("
                  + exported.reportPath()
                  + ").");
      assertThat(Files.readString(exported.reportPath())).isEqualTo("modified");
    }
  }

  @Test
  void refusesMissingArtifactsBehindCommittedManifest() throws Exception {
    var fixture = fixture(List.of());
    try (var ignored = fixture.store()) {
      var exported = fixture.export(directory.resolve("selected"));
      Files.delete(exported.evaluationsCsvPath());
      assertThatThrownBy(() -> fixture.export(directory.resolve("selected")))
          .isInstanceOf(UncheckedIOException.class)
          .hasRootCauseMessage(
              "Committed export artifact is missing (" + exported.evaluationsCsvPath() + ").");
    }
  }

  @Test
  void rejectsStudyControlAliasesAndDatasetSymlinkAndHardLinkAliases() throws Exception {
    Path dataset = Files.writeString(directory.resolve("dataset.json"), "input");
    var fixture = fixture(List.of(dataset));
    try (var ignored = fixture.store()) {
      assertThatThrownBy(() -> fixture.export(fixture.store().directory()))
          .isInstanceOf(UncheckedIOException.class);
      var alias = directory.resolve("study-alias");
      Files.createSymbolicLink(alias, fixture.store().directory());
      assertThatThrownBy(() -> fixture.export(alias.resolve("artifacts")))
          .isInstanceOf(UncheckedIOException.class);
      assertThatThrownBy(
              () -> fixture.export(fixture.store().directory().resolve("selected/../artifacts")))
          .isInstanceOf(UncheckedIOException.class);
      var symlinkDestination = Files.createDirectory(directory.resolve("symlink-output"));
      Files.createSymbolicLink(symlinkDestination.resolve("selected-solver.xml"), dataset);
      assertThatThrownBy(() -> fixture.export(symlinkDestination))
          .isInstanceOf(UncheckedIOException.class);
      var hardlinkDestination = Files.createDirectory(directory.resolve("hardlink-output"));
      Files.createLink(hardlinkDestination.resolve("selected-solver.xml"), dataset);
      assertThatThrownBy(() -> fixture.export(hardlinkDestination))
          .isInstanceOf(UncheckedIOException.class);
      assertThat(Files.readString(dataset)).isEqualTo("input");
    }
  }

  @Test
  void rejectsAnotherFrozenSelectionEvenWithIdenticalExportBytes() throws Exception {
    var fixture = fixture(List.of());
    try (var ignored = fixture.store()) {
      var exported = fixture.export(directory.resolve("selected"));
      var baseline = fixture.study().trials().getFirst();
      var other =
          new StudyResult<>(
              fixture.study().studyId(),
              StudyState.FINISHED,
              TrainingStatus.NO_IMPROVEMENT_FOUND,
              fixture.study().trials(),
              baseline,
              0L,
              null,
              0L,
              0L,
              false,
              null);
      assertThatThrownBy(
              () ->
                  StudyExporter.export(
                      exported.reportPath().getParent(),
                      fixture.store(),
                      fixture.definition(),
                      other,
                      null,
                      fixture.observations(),
                      fixture.progression(),
                      fixture.xml()))
          .isInstanceOf(UncheckedIOException.class)
          .hasRootCauseMessage(
              "Export destination belongs to another frozen selection ("
                  + exported.reportPath().getParent()
                  + ").");
    }
  }

  @Test
  void rejectsWrongConfigurationIdentityBeforeWritingDestination() throws Exception {
    var fixture = fixture(List.of());
    try (var ignored = fixture.store()) {
      var changed =
          SolverConfig.createFromXmlReader(
              new java.io.StringReader(new String(fixture.xml(), StandardCharsets.UTF_8)));
      changed.getTerminationConfig().setSpentLimit(Duration.ofSeconds(2));
      var destination = directory.resolve("selected");
      assertThatThrownBy(
              () ->
                  StudyExporter.export(
                      destination,
                      fixture.store(),
                      fixture.definition(),
                      fixture.study(),
                      fixture.qualification(),
                      fixture.observations(),
                      fixture.progression(),
                      FrozenSolverConfig.write(changed).getBytes(StandardCharsets.UTF_8)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("frozen candidate identity");
      assertThat(destination).doesNotExist();
    }
  }

  @Test
  void runtimeProviderIsReconstructedAndIdentityIsRecorded() throws Exception {
    var calls = new AtomicInteger();
    var provider =
        new RuntimeReconstructionProvider() {
          @Override
          public ComponentIdentity identity() {
            return new ComponentIdentity("export-clock", "1");
          }

          @Override
          public void reconstruct(SolverConfig config) {
            calls.incrementAndGet();
            config.setClock(Clock.systemUTC());
          }
        };
    var fixture = fixture(List.of(), provider);
    try (var ignored = fixture.store()) {
      int previousCalls = calls.get();
      var exported = fixture.export(directory.resolve("selected"));
      assertThat(calls.get()).isGreaterThan(previousCalls);
      var config = SolverConfig.createFromXmlFile(exported.solverConfigPath().toFile());
      provider.reconstruct(config);
      assertThat(FrozenSolverConfig.canonicalIdentity(config, provider.identity()))
          .isEqualTo(fixture.selected().candidateIdentity());
    }
  }

  @Test
  void rejectsRuntimeProviderChangingScoredConfigurationBeforeWriting() throws Exception {
    var corrupt = new AtomicBoolean();
    var provider =
        new RuntimeReconstructionProvider() {
          @Override
          public ComponentIdentity identity() {
            return new ComponentIdentity("mutable-export-provider", "1");
          }

          @Override
          public void reconstruct(SolverConfig config) {
            if (corrupt.get()) config.getTerminationConfig().setSpentLimit(Duration.ofSeconds(2));
          }
        };
    var fixture = fixture(List.of(), provider);
    try (var ignored = fixture.store()) {
      corrupt.set(true);
      var destination = directory.resolve("selected");
      assertThatThrownBy(() -> fixture.export(destination))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Runtime reconstruction changed selected XML-visible behavior");
      assertThat(destination).doesNotExist();
    }
  }

  @Test
  void reportsRecoveredAttemptsWithoutInventingScoresOrIncludingThemInAggregates()
      throws Exception {
    var fixture = fixture(List.of());
    try (var store = fixture.store()) {
      store.append(
          "EVALUATION_STARTED",
          Map.of(
              "evaluationId",
              "crashed-attempt",
              "attempt",
              "0",
              "stage",
              "TRAINING",
              "trialId",
              "1",
              "problemId",
              "train-small",
              "solverSeed",
              "11",
              "operationId",
              "session-1/run-1",
              "horizonNanos",
              "1000000000",
              "candidateIdentity",
              "candidate"));
      store.append(
          "ACTIVE_HEARTBEAT",
          Map.of(
              "operationId",
              "session-1/run-1",
              "evaluationId",
              "crashed-attempt",
              "evaluationElapsedNanos",
              "123456789",
              "elapsedNanos",
              "987654321"));
      store.append(
          "EVALUATION_RECOVERED",
          Map.of(
              "evaluationId",
              "crashed-attempt",
              "attempt",
              "0",
              "status",
              "INTERRUPTED",
              "reason",
              "Process ended before terminal commit."));
      var exported = fixture.export(directory.resolve("selected"));
      var csv = Files.readString(exported.evaluationsCsvPath());
      assertThat(csv)
          .contains(
              "\"crashed-attempt\",\"TRAINING\",\"1\",\"candidate\",\"train-small\",\"11\",\"0\",\"INTERRUPTED\",,,,,,,");
      assertThat(csv).contains("\"PROCESS_INTERRUPTION\"", "\"true\",\"123456789\"");
      var manifest = StoreJson.readObject(Files.readAllBytes(exported.selectionManifestPath()));
      assertThat(object(object(manifest.get("trainingFeasibility")).get("fraction")))
          .containsEntry("numerator", "1")
          .containsEntry("denominator", "1");
      assertThat(fixture.observations()).hasSize(6);
    }
  }

  @Test
  void
      registeredExactScoreAdapterDrivesPairedDifferencesAndVariabilityWhileCsvPreservesNativeScores()
          throws Exception {
    ExactScoreAdapter<HardSoftScore> adapter =
        new ExactScoreAdapter<>() {
          @Override
          public ComponentIdentity identity() {
            return new ComponentIdentity("report-test-scaled-levels", "1", "fixture-artifact");
          }

          @Override
          public List<ExactRational> levels(HardSoftScore score) {
            return ExactScoreAdapter.<HardSoftScore>standard().levels(score).stream()
                .map(level -> level.multiply(ExactRational.of(2)))
                .toList();
          }
        };
    var fixture = fixture(List.of(), null, adapter);
    try (var ignored = fixture.store()) {
      assertThat(fixture.definition().objective().scoreAdapter().identity())
          .isEqualTo(adapter.identity());
      var exported = fixture.export(directory.resolve("selected"));
      var manifest = StoreJson.readObject(Files.readAllBytes(exported.selectionManifestPath()));
      var training = object(list(manifest.get("pairedInstanceDifferences")).getFirst());
      assertThat(training).containsEntry("complete", true).containsEntry("pairedSeeds", "2");
      var differences = list(training.get("meanSelectedMinusBaselineNativeLevels"));
      assertThat(object(differences.get(1)))
          .containsEntry("numerator", "3")
          .containsEntry("denominator", "1");
      var variability =
          list(manifest.get("seedVariability")).stream()
              .map(StudyExporterTest::object)
              .filter(
                  value ->
                      value.get("trialId").equals("1") && value.get("stage").equals("TRAINING"))
              .findFirst()
              .orElseThrow();
      var variance = list(variability.get("populationVarianceNativeLevels"));
      assertThat(object(variance.get(1)))
          .containsEntry("numerator", "1")
          .containsEntry("denominator", "1");
      assertThat(Files.readString(exported.evaluationsCsvPath()))
          .contains("-9007199254740993", "java.lang.Long");
    }
  }

  @Test
  void registeredExactScoreAdapterFailureAbortsExportInsteadOfHidingMeasuredPairs()
      throws Exception {
    ExactScoreAdapter<HardSoftScore> adapter =
        new ExactScoreAdapter<>() {
          @Override
          public ComponentIdentity identity() {
            return new ComponentIdentity("broken-report-adapter", "1");
          }

          @Override
          public List<ExactRational> levels(HardSoftScore score) {
            throw new IllegalStateException("Application adapter conversion failed.");
          }
        };
    var fixture = fixture(List.of(), null, adapter);
    try (var ignored = fixture.store()) {
      var destination = directory.resolve("selected");
      assertThatThrownBy(() -> fixture.export(destination))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Application adapter conversion failed.");
      assertThat(destination).doesNotExist();
    }
  }

  private Fixture fixture(List<Path> inputs) throws Exception {
    return fixture(inputs, null);
  }

  private Fixture fixture(List<Path> inputs, RuntimeReconstructionProvider provider)
      throws Exception {
    return fixture(inputs, provider, ExactScoreAdapter.standard());
  }

  private Fixture fixture(
      List<Path> inputs,
      RuntimeReconstructionProvider provider,
      ExactScoreAdapter<HardSoftScore> scoreAdapter)
      throws Exception {
    var delegate = SchedulingExample.problemAdapter();
    ProblemAdapter<SchedulingExample.Schedule> adapter =
        new ProblemAdapter<>() {
          @Override
          public ComponentIdentity identity() {
            return delegate.identity();
          }

          @Override
          public ProblemDescriptor describe(String id) {
            return delegate.describe(id);
          }

          @Override
          public SchedulingExample.Schedule loadFresh(String id) {
            return delegate.loadFresh(id);
          }

          @Override
          public List<Path> inputPaths() {
            return inputs;
          }
        };
    var definition =
        GreybayStudyDefinition.<SchedulingExample.Schedule, HardSoftScore>builder()
            .baseSolverConfig(SchedulingExample.baseSolverConfig())
            .runtimeReconstructionProvider(provider)
            .problemAdapter(adapter)
            .trainingPanel(new ProblemPanel(List.of("train-small"), List.of(11L, 13L)))
            .heldOutPanel(new ProblemPanel(List.of("heldout-small"), List.of(101L, 103L)))
            .templates(List.of(GreybayWorkflowExample.template()))
            .objective(new BalancedQualityObjective<>(scoreAdapter))
            .sampler(new RandomSamplerConfig().buildSampler())
            .optimizerSeed(17L)
            .solveDuration(Duration.ofSeconds(1))
            .warmupDuration(Duration.ofSeconds(40))
            .maxTrials(1L)
            .activeTimeLimit(Duration.ofMinutes(5))
            .build();
    var config =
        StudyProtocol.apply(
            definition, definition.baseSolverConfig(), null, null, 99L, definition.solveDuration());
    var frozen = FrozenSolverConfig.capture(config, provider);
    var baselineRecords =
        List.of(
            record("TRAINING", 0, "train-small", 11, -9007199254740993L),
            record("TRAINING", 0, "train-small", 13, -9007199254740993L));
    var selectedRecords =
        List.of(
            record("TRAINING", 1, "train-small", 11, -9007199254740992L),
            record("TRAINING", 1, "train-small", 13, -9007199254740991L));
    var baselineValue =
        new BalancedObjectiveValue(
            ExactRational.ONE, List.of(ExactRational.ZERO, ExactRational.ZERO));
    var selectedValue =
        new BalancedObjectiveValue(
            ExactRational.ONE, List.of(ExactRational.ZERO, ExactRational.of(1, 6004799503160662L)));
    var baseline =
        new TrialResult<>(
            0L,
            frozen.identity(),
            null,
            TrialStatus.COMPLETE,
            baselineValue,
            baselineRecords,
            null);
    var selected =
        new TrialResult<>(
            1L,
            frozen.identity(),
            null,
            TrialStatus.COMPLETE,
            selectedValue,
            selectedRecords,
            null);
    var study =
        new StudyResult<>(
            "fixture-study",
            StudyState.FINISHED,
            TrainingStatus.IMPROVEMENT_FOUND,
            List.of(baseline, selected),
            selected,
            1L,
            null,
            10000000001L,
            12000000001L,
            true,
            null);
    var observations = new ArrayList<EvaluationRecord<HardSoftScore>>(baselineRecords);
    observations.addAll(selectedRecords);
    observations.add(
        new EvaluationRecord<>(
            "failed",
            "HELD_OUT_SELECTED",
            1,
            "heldout-small",
            101L,
            0,
            EvaluationStatus.FAILED,
            null,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            null,
            "java.lang.IllegalStateException",
            "line one,\"quoted\"\nline two"));
    observations.add(
        new EvaluationRecord<>(
            "warmup",
            "WARMUP:2:template",
            -1,
            "train-small",
            -1L,
            0,
            EvaluationStatus.BUDGET_EXCEEDED,
            null,
            -1,
            -1,
            -1,
            43000000000L,
            0L,
            0L,
            null,
            null,
            null));
    var qualification =
        new QualificationResult<HardSoftScore>(
            QualificationStatus.NO_HELD_OUT_IMPROVEMENT,
            QualificationComparison.WORSE,
            1L,
            ExactRational.ONE,
            baselineValue,
            selectedValue,
            List.of(),
            "fixture");
    List<Map<String, Object>> progression =
        List.of(
            Map.of(
                "trialId",
                "1",
                "status",
                "COMPLETE",
                "measuredActiveNanos",
                "10000000001",
                "admissionChargedNanos",
                "12000000001",
                "objective",
                definition.objective().codec().encodeValue(selectedValue)));
    var store = StudyStore.create(directory.resolve("study"), Map.of("studyId", "fixture-study"));
    return new Fixture(
        definition,
        study,
        qualification,
        List.copyOf(observations),
        progression,
        frozen.xml().getBytes(StandardCharsets.UTF_8),
        store,
        selected);
  }

  private static EvaluationRecord<HardSoftScore> record(
      String stage, long trial, String problem, long seed, long soft) {
    return new EvaluationRecord<>(
        stage + "/" + trial + "/" + problem + "/" + seed,
        stage,
        trial,
        problem,
        seed,
        0,
        EvaluationStatus.COMPLETE,
        HardSoftScore.of(0, soft),
        0,
        999_000_000L,
        999L,
        1_000_100_000L,
        10_000L,
        20_000L,
        VerificationResult.notConfigured(),
        null,
        null);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object value) {
    return (List<Object>) value;
  }

  private record Fixture(
      GreybayStudyDefinition<SchedulingExample.Schedule, HardSoftScore> definition,
      StudyResult<HardSoftScore> study,
      QualificationResult<HardSoftScore> qualification,
      List<EvaluationRecord<HardSoftScore>> observations,
      List<Map<String, Object>> progression,
      byte[] xml,
      StudyStore store,
      TrialResult<HardSoftScore> selected) {
    ExportResult export(Path destination) {
      return StudyExporter.export(
          destination, store, definition, study, qualification, observations, progression, xml);
    }
  }
}
