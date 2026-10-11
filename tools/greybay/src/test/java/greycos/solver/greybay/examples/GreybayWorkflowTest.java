package greycos.solver.greybay.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.GreybayStudies;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.QualificationStatus;
import greycos.solver.greybay.api.StudyState;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.config.StudyRunLimits;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GreybayWorkflowTest {
  @TempDir Path directory;

  @Test
  void schedulingCreatesStopsResumesQualifiesExportsAndReloads() throws Exception {
    lifecycle(SchedulingExample.baseSolverConfig(), SchedulingExample.problemAdapter());
  }

  @Test
  void routingCreatesStopsResumesQualifiesExportsAndReloads() throws Exception {
    lifecycle(RoutingExample.baseSolverConfig(), RoutingExample.problemAdapter());
  }

  private <Solution_> void lifecycle(SolverConfig base, ProblemAdapter<Solution_> adapter)
      throws Exception {
    var definition = GreybayWorkflowExample.definition(base, adapter, 0, Duration.ofSeconds(1));
    var studyDirectory = directory.resolve("study");
    int initialTrials;
    try (var study = GreybayStudies.create(studyDirectory, definition)) {
      var training = study.run();
      assertThat(training.hasEligibleIncumbent()).isTrue();
      assertThat(training.incumbent().trialId()).isZero();
      initialTrials = training.trials().size();
      study.requestStop();
    }
    assertThatThrownBy(() -> GreybayStudies.create(studyDirectory, definition))
        .isInstanceOf(RuntimeException.class);
    try (var resumed =
        GreybayStudies.resume(
            studyDirectory, definition, StudyRunLimits.of(1, Duration.ofMinutes(6)))) {
      assertThat(resumed.snapshot().trials()).hasSize(initialTrials);
      var trained = resumed.run();
      assertThat(trained.hasEligibleIncumbent()).isTrue();
      assertThat(trained.trials().size()).isGreaterThan(initialTrials);
      assertThat(trained.trials())
          .allSatisfy(
              trial ->
                  assertThat(trial.evaluations())
                      .allSatisfy(
                          evaluation -> {
                            assertThat(evaluation.eligible())
                                .as(evaluation.failureMessage())
                                .isTrue();
                            assertThat(evaluation.verification().status())
                                .isEqualTo(VerificationResult.Status.PASSED);
                          }));
      var qualification = resumed.qualify();
      assertThat(qualification.status())
          .isNotIn(QualificationStatus.FAILED, QualificationStatus.INCONCLUSIVE);
      assertThat(qualification.evaluations())
          .allSatisfy(
              evaluation ->
                  assertThat(evaluation.verification().status())
                      .isEqualTo(VerificationResult.Status.PASSED));
      assertThat(resumed.snapshot().frozenSelectionTrialId()).isNotNull();
      var exported = resumed.exportSelected(directory.resolve("selected"));
      assertThat(exported.solverConfigPath()).isRegularFile();
      assertThat(exported.selectionManifestPath()).isRegularFile();
      assertThat(exported.evaluationsCsvPath()).isRegularFile();
      assertThat(exported.reportPath()).isRegularFile();
      var reloaded = SolverConfig.createFromXmlFile(exported.solverConfigPath().toFile());
      assertThat(reloaded.getRandomSeed()).isEqualTo(11L);
      assertThat(reloaded.getTerminationConfig().getSpentLimit()).isEqualTo(Duration.ofSeconds(1));
      assertThat(reloaded.getSolutionClass()).isEqualTo(base.getSolutionClass());
      var solution =
          SolverFactory.<Solution_>create(reloaded)
              .buildSolver()
              .solve(adapter.loadFresh("heldout-small"));
      assertThat(adapter.verifier().orElseThrow().verify("heldout-small", solution).status())
          .isEqualTo(VerificationResult.Status.PASSED);
      assertThat(base.getTerminationConfig()).isNull();
    }
  }

  @Test
  void stopBeforeRunDoesNotAdmitWorkAndExplicitResumeClearsStop() {
    var definition =
        GreybayWorkflowExample.definition(
            SchedulingExample.baseSolverConfig(),
            SchedulingExample.problemAdapter(),
            0,
            Duration.ofSeconds(1));
    var studyDirectory = directory.resolve("stopped");
    try (var study = GreybayStudies.create(studyDirectory, definition)) {
      study.requestStop();
      var stopped = study.run();
      assertThat(stopped.state()).isEqualTo(StudyState.PAUSED);
      assertThat(stopped.trials())
          .hasSize(1)
          .allSatisfy(
              trial -> {
                assertThat(trial.trialId()).isZero();
                assertThat(trial.evaluations()).isEmpty();
              });
    }
    try (var resumed = GreybayStudies.resume(studyDirectory, definition)) {
      assertThat(resumed.run().hasEligibleIncumbent()).isTrue();
    }
  }

  @Test
  void optionalBenchmarkPreservesExactSeedEntriesWithoutMutatingInputs() {
    var baseline = SchedulingExample.baseSolverConfig();
    var selected = SchedulingExample.baseSolverConfig();
    var config =
        ExportedBenchmarkExample.configuration(baseline, selected, List.of(101L, 997L), directory);
    assertThat(config.getParallelBenchmarkCount()).isEqualTo("1");
    assertThat(config.getWarmUpMillisecondsSpentLimit()).isZero();
    assertThat(config.getSolverBenchmarkConfigList())
        .hasSize(4)
        .allSatisfy(entry -> assertThat(entry.getSubSingleCount()).isEqualTo(1));
    assertThat(config.getSolverBenchmarkConfigList())
        .extracting(entry -> entry.getSolverConfig().getRandomSeed())
        .containsExactly(101L, 101L, 997L, 997L);
    assertThat(config.getSolverBenchmarkConfigList())
        .extracting(entry -> entry.getName())
        .containsExactly(
            "baseline-seed-101", "selected-seed-101", "baseline-seed-997", "selected-seed-997");
    assertThat(baseline.getRandomSeed()).isNull();
    assertThat(selected.getRandomSeed()).isNull();
  }
}
