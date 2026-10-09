package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.IntStream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmStepLoggingMode;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmEvaluatorPhaseTest.RangeConstraints;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmEvaluatorPhaseTest.RangeEntity;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmEvaluatorPhaseTest.RangeSolution;
import greycos.solver.core.impl.islandmodel.DefaultIslandModelPhase;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** Temporarily changes the shared phase logger, restoring its configuration after each capture. */
@Isolated
@Timeout(30)
class GeneticAlgorithmLoggingTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void modesAndLogLevelsPreserveFixedWorkWhileLoggingOnlyTheRequestedCompletedSteps(int workers) {
    ObservedRun baseline = null;
    for (var mode :
        new GeneticAlgorithmStepLoggingMode[] {
          null,
          GeneticAlgorithmStepLoggingMode.ALL,
          GeneticAlgorithmStepLoggingMode.BEST_SCORE_IMPROVED
        }) {
      for (var level : List.of(Level.DEBUG, Level.INFO)) {
        try (var capture = new LogCapture(level)) {
          var phase = phase(workers, 7, 125).withPBestRate(0.8).withTabuEntityRate(0.25);
          phase.setStepLoggingMode(mode);
          var run = run(phase, problem(5, 12));
          assertThat(run.steps()).hasSize(125);
          assertThat(run.steps())
              .allSatisfy(
                  step ->
                      assertThat(step.bestImproved())
                          .isEqualTo(step.bestScore().compareTo(step.bestBeforeScore()) > 0));
          assertThat(run.steps()).anySatisfy(step -> assertThat(step.bestImproved()).isTrue());
          assertThat(run.steps())
              .anySatisfy(step -> assertThat(step.score()).isEqualTo(step.beforeScore()));
          assertThat(run.steps())
              .anySatisfy(step -> assertThat(step.score()).isLessThan(step.beforeScore()));
          // Improvement of the current workspace alone is insufficient for best-only logging.
          assertThat(run.steps())
              .anySatisfy(
                  step -> {
                    assertThat(step.score()).isGreaterThan(step.beforeScore());
                    assertThat(step.score()).isLessThan(step.bestScore());
                    assertThat(step.bestImproved()).isFalse();
                  });
          var expected =
              level == Level.INFO
                  ? List.<ObservedStep>of()
                  : run.steps().stream()
                      .filter(
                          step ->
                              mode != GeneticAlgorithmStepLoggingMode.BEST_SCORE_IMPROVED
                                  || step.bestImproved())
                      .toList();
          assertLogs(capture.steps(), expected);
          assertThat(capture.summaries()).hasSize(1);
          if (level == Level.INFO) {
            assertThat(capture.events)
                .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
            assertThat(capture.summaries().getFirst().getFormattedMessage())
                .contains("attempts (125)", "best score (" + run.score() + ")");
          }
          if (baseline == null) baseline = run;
          else assertThat(run).isEqualTo(baseline);
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void cachedCandidatesKeepCandidateFitnessSeparateFromTheSettledWorkspaceScore(int workers) {
    try (var capture = new LogCapture(Level.DEBUG)) {
      var run = run(phase(workers, 32, 63), problem(2, 1));
      assertLogs(capture.steps(), run.steps());
      assertThat(run.steps())
          .anySatisfy(
              step -> {
                assertThat(step.seeding()).isFalse();
                assertThat(step.outcome()).isEqualTo(GeneticAlgorithmOutcome.DUPLICATE);
                assertThat(step.candidateScore()).isNotEqualTo(step.score());
                assertThat(step.score()).isEqualTo(step.beforeScore());
              });
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void tiedAndUnchangedCandidatesAreLoggedByDefaultAndNeverAsHistoricalImprovements(int workers) {
    for (var mode : GeneticAlgorithmStepLoggingMode.values()) {
      try (var capture = new LogCapture(Level.DEBUG)) {
        var phase =
            phase(workers, 3, 50)
                .withStepLoggingMode(mode)
                .withNoProgressAttemptLimit(1000L)
                .withMutationOperators(mutation(GeneticAlgorithmMutationType.CHANGE));
        var solver =
            solver(
                config(phase)
                    .withConstraintProviderClass(
                        GeneticAlgorithmGenerationPolicyTest.ConstantConstraints.class));
        var observed = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
        assertThat(solver.solve(problem(2, 2)).getScore()).isEqualTo(SimpleScore.ZERO);
        assertThat(observed)
            .extracting(ObservedStep::outcome)
            .contains(GeneticAlgorithmOutcome.DUPLICATE, GeneticAlgorithmOutcome.NO_CHANGE);
        assertThat(observed).allSatisfy(step -> assertThat(step.bestImproved()).isFalse());
        assertLogs(
            capture.steps(), mode == GeneticAlgorithmStepLoggingMode.ALL ? observed : List.of());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void invalidCandidatesLogNullFitnessAndTheRestoredScore(int workers) {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(RangeSolution.class)
            .withEntityClasses(RangeEntity.class)
            .withConstraintProviderClass(RangeConstraints.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withRandomSeed(37L)
            .withPhases(
                phase(workers, 1, 7)
                    .withMutationOperators(mutation(GeneticAlgorithmMutationType.SWAP)));
    var solver =
        (DefaultSolver<RangeSolution>)
            SolverFactory.<RangeSolution>create(solverConfig).buildSolver();
    var observed =
        observe(solver, solution -> solution.entities.stream().map(e -> e.value).toList());
    var first = new RangeEntity();
    first.id = "first";
    first.range = List.of(0L, 1L);
    first.value = 0L;
    var second = new RangeEntity();
    second.id = "second";
    second.range = List.of(10L, 11L);
    second.value = 10L;
    var input = new RangeSolution();
    input.entities = new ArrayList<>(List.of(first, second));
    try (var capture = new LogCapture(Level.DEBUG)) {
      assertThat(solver.solve(input).score).isEqualTo(SimpleScore.of(10));
      assertThat(observed).hasSize(7);
      assertLogs(capture.steps(), observed);
      assertThat(capture.steps())
          .allSatisfy(
              event ->
                  assertThat(event.getFormattedMessage())
                      .contains(
                          "score (10)",
                          "candidate score (null)",
                          "outcome (INVALID)",
                          "admitted (false)",
                          "changed (0)"));
    }
  }

  @Test
  void multipleTemporaryBestsAndARejectedProbeProduceOneCompletedLineWithTheFinalBest() {
    try (var capture = new LogCapture(Level.DEBUG);
        var meters = new GeneticAlgorithmLocalImprovementMetricsTest.TestMeters()) {
      var solver = GeneticAlgorithmLocalImprovementMetricsTest.controlledSolver(meters);
      var observed = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
      var publications = new ArrayList<SimpleScore>();
      solver.addEventListener(event -> publications.add(event.getNewBestSolution().getScore()));
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              if (((GeneticAlgorithmStepScope<?>) scope).getLocalImprovementProbeCount() > 0) {
                solver.terminateEarly();
              }
            }
          });

      var result = solver.solve(problem(21, 8));

      assertReplay(result);
      assertThat(publications).contains(SimpleScore.of(80), SimpleScore.of(160));
      assertLogs(capture.steps(), observed);
      assertThat(observed)
          .filteredOn(step -> step.probes() > 0)
          .singleElement()
          .satisfies(
              step -> {
                assertThat(step.probes()).isEqualTo(3);
                assertThat(step.acceptedProbes()).isEqualTo(2);
                assertThat(step.bestImproved()).isTrue();
                assertThat(step.score()).isEqualTo(SimpleScore.of(160));
                assertThat(step.bestScore()).isEqualTo(SimpleScore.of(160));
              });
      assertThat(capture.steps())
          .filteredOn(event -> "new".equals(event.getArgumentArray()[6]))
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage())
                      .contains("score (160)", "new best score (160)", "candidate score (160)"));
      assertThat(solver.getMoveEvaluationCount()).isEqualTo(observed.size() + 3L);
    }
  }

  @Test
  void cancellationAfterAPublishedProbeBestDoesNotInventACompletedStep() {
    try (var capture = new LogCapture(Level.DEBUG);
        var meters = new GeneticAlgorithmLocalImprovementMetricsTest.TestMeters()) {
      var solver = GeneticAlgorithmLocalImprovementMetricsTest.controlledSolver(meters);
      var observed = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
      var phase = recordPhase(solver);
      solver.addEventListener(
          event -> {
            if (event.getNewBestSolution().getScore().equals(SimpleScore.of(160))) {
              solver.terminateEarly();
            }
          });

      var result = solver.solve(problem(21, 8));

      assertReplay(result);
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(160));
      assertThat(phase.get().getLocalImprovementProbeCount()).isEqualTo(2);
      assertThat(phase.get().getNextStepIndex()).isEqualTo(observed.size());
      assertThat(solver.getMoveEvaluationCount()).isEqualTo(observed.size() + 2L);
      assertLogs(capture.steps(), observed);
      assertThat(capture.steps())
          .noneSatisfy(
              event -> assertThat(event.getFormattedMessage()).contains("best score (160)"));
      assertThat(capture.summaries()).hasSize(1);
    }
  }

  @Test
  void standalonePhaseIdentityAndRootTagArePreserved() {
    var solver =
        solver(
            config(phase(0, 3, 5))
                .withPhases(new ConstructionHeuristicPhaseConfig(), phase(0, 3, 5)));
    var observed = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
    try (var capture = new LogCapture(Level.DEBUG)) {
      assertReplay(solver.solve(problem(4, 8)));
      assertLogs(capture.steps(), observed);
      assertThat(capture.steps())
          .hasSize(5)
          .allSatisfy(
              event -> {
                assertThat(event.getArgumentArray()[2]).isEqualTo(1);
                assertThat(event.getArgumentArray()[17]).isEqualTo("");
                assertThat(event.getFormattedMessage()).doesNotContain("island (");
              });
    }
  }

  @Test
  void islandStepsHaveLocalOrderingAndIdentityWithoutExtraMigrationRecords() {
    var solver =
        solver(
            config(phase(0, 3, 11))
                .withPhases(
                    new IslandModelPhaseConfig()
                        .withIslandCount(2)
                        .withMigrationFrequency(1)
                        .withReceiveGlobalUpdateFrequency(1)
                        .withPhaseConfigList(List.of(phase(0, 3, 11).withMigrationRate(1.0)))));
    try (var capture = new LogCapture(Level.DEBUG)) {
      assertReplay(solver.solve(problem(5, 12)));
      assertThat(capture.steps()).hasSize(22);
      for (int island = 0; island < 2; island++) {
        var suffix = ", island (" + island + ")";
        var logs =
            capture.steps().stream()
                .filter(event -> suffix.equals(event.getArgumentArray()[17]))
                .toList();
        assertThat(logs)
            .extracting(event -> event.getArgumentArray()[1])
            .containsExactlyElementsOf(IntStream.range(0, 11).boxed().toList());
        assertThat(logs)
            .allSatisfy(
                event -> {
                  assertThat(event.getArgumentArray()[2]).isEqualTo(0);
                  assertThat(event.getFormattedMessage()).endsWith(suffix + ".");
                });
      }
      var islandPhase = (DefaultIslandModelPhase<?>) solver.getPhaseList().getFirst();
      assertThat(islandPhase.getIslandDiagnostics())
          .hasSize(2)
          .allSatisfy(
              diagnostics -> {
                assertThat(diagnostics.moveEvaluationCount()).isEqualTo(11L);
                assertThat(
                        diagnostics
                            .geneticAlgorithmPhases()
                            .getFirst()
                            .migration()
                            .exportedBatches())
                    .isPositive();
              });
      assertThat(capture.summaries()).hasSize(2);
    }
  }

  @Test
  void aBestPublishedOnlyByMigrationDoesNotCreateABestStepRecord() {
    var solver =
        solver(
            config(
                phase(0, 3, 6)
                    .withMigrationRate(1.0)
                    .withStepLoggingMode(GeneticAlgorithmStepLoggingMode.BEST_SCORE_IMPROVED)));
    var observed = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
    var ga = (DefaultGeneticAlgorithmPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    ga.setMigration(
        new GeneticAlgorithmMigration<>() {
          @Override
          public int frequency() {
            return 1;
          }

          @Override
          public void phaseStarted() {}

          @Override
          public GeneticAlgorithmMigrationBatch<TestdataSolution> exchange(
              long generation,
              List<GeneticAlgorithmMigrationBatch.Entry<TestdataSolution>> outgoing) {
            var donor = problem(101, 8);
            donor
                .getEntityList()
                .forEach(entity -> entity.setValue(donor.getValueList().getLast()));
            var entry =
                new GeneticAlgorithmMigrationBatch.Entry<>(
                    SolutionAssignments.capture(
                        solver.getSolverScope().getScoreDirector().getSolutionDescriptor(), donor),
                    InnerScore.fullyAssigned(SimpleScore.of(800)));
            return new GeneticAlgorithmMigrationBatch<>(7, generation, List.of(entry));
          }

          @Override
          public void publishBest() {}

          @Override
          public void phaseEnded() {}
        });
    try (var capture = new LogCapture(Level.DEBUG)) {
      var result = solver.solve(problem(101, 8));
      assertReplay(result);
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(800));
      assertThat(ga.getMigrationDiagnostics().admittedEntries()).isEqualTo(1);
      assertThat(observed).hasSize(6);
      assertLogs(capture.steps(), observed.stream().filter(ObservedStep::bestImproved).toList());
      assertThat(capture.steps())
          .noneSatisfy(
              event -> assertThat(event.getFormattedMessage()).contains("best score (800)"));
    }
  }

  private static GeneticAlgorithmPhaseConfig phase(int workers, int population, int steps) {
    return new GeneticAlgorithmPhaseConfig()
        .withEvaluatorThreadCount(workers)
        .withPopulationSize(population)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static GeneticAlgorithmMutationOperatorConfig mutation(
      GeneticAlgorithmMutationType type) {
    return new GeneticAlgorithmMutationOperatorConfig().withType(type).withProbability(1.0);
  }

  private static ObservedRun run(GeneticAlgorithmPhaseConfig phase, TestdataSolution input) {
    var solver = solver(config(phase));
    var observed = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
    var publications = new ArrayList<List<String>>();
    solver.addEventListener(event -> publications.add(assignments(event.getNewBestSolution())));
    var result = solver.solve(input);
    assertReplay(result);
    return new ObservedRun(
        result.getScore(),
        assignments(result),
        solver.getSolverScope().getScoreCalculationCount(),
        solver.getMoveEvaluationCount(),
        observed,
        publications);
  }

  private static <Solution_> List<ObservedStep> observe(
      DefaultSolver<Solution_> solver, Function<Solution_, ?> snapshot) {
    var observed = new ArrayList<ObservedStep>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<Solution_> scope) {
            if (scope instanceof GeneticAlgorithmPhaseScope<Solution_> phase) {
              phase.addCommittedStepListener(
                  step -> {
                    assertThat(phase.getLastCompletedStepScope()).isSameAs(step);
                    observed.add(
                        new ObservedStep(
                            step.getStepIndex(),
                            phase.getPhaseIndex(),
                            step.getGeneration(),
                            (SimpleScore) step.getBeforeScore().raw(),
                            (SimpleScore) step.getBestBeforeScore().raw(),
                            (SimpleScore) step.getScore().raw(),
                            (SimpleScore) phase.getBestScore().raw(),
                            step.getCandidateScore() == null
                                ? null
                                : (SimpleScore) step.getCandidateScore().raw(),
                            step.getBestScoreImproved(),
                            step.getOutcome(),
                            step.getLocalImprovementProbeCount(),
                            step.getLocalImprovementAcceptedCount(),
                            step.isSeeding(),
                            step.isCrossed(),
                            step.getNativeId(),
                            Arrays.asList(
                                step.getScore().raw(),
                                step.getBestScoreImproved() ? "new" : "   ",
                                phase.getBestScore().raw(),
                                step.getCandidateScore() == null
                                    ? null
                                    : step.getCandidateScore().raw(),
                                step.getCandidateId(),
                                step.getFirstParentId(),
                                step.getSecondParentId(),
                                step.getMutationType(),
                                step.getMutationGroup(),
                                step.getOutcome(),
                                step.isAdmitted(),
                                step.getChangedAssignmentCount()),
                            snapshot.apply(step.getWorkingSolution())));
                  });
            }
          }
        });
    return observed;
  }

  private static void assertLogs(List<ILoggingEvent> logs, List<ObservedStep> observed) {
    assertThat(logs).hasSameSizeAs(observed);
    for (int i = 0; i < logs.size(); i++) {
      var event = logs.get(i);
      var step = observed.get(i);
      assertThat(event.getLoggerName()).isEqualTo(DefaultGeneticAlgorithmPhase.class.getName());
      assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
      var arguments = event.getArgumentArray();
      assertThat(arguments).hasSize(18);
      assertThat(arguments[0]).isEqualTo("");
      assertThat(arguments[1]).isEqualTo(step.index());
      assertThat(arguments[2]).isEqualTo(step.phase());
      assertThat(arguments[3]).isEqualTo(step.generation());
      assertThat(((Number) arguments[4]).longValue()).isNotNegative();
      assertThat(Arrays.asList(arguments).subList(5, 17)).isEqualTo(step.fields());
      assertThat(arguments[17]).isEqualTo("");
      assertThat(event.getFormattedMessage())
          .contains("Genetic Algorithm step (" + step.index() + "), phase (" + step.phase() + ")")
          .endsWith(".");
    }
    assertThat(
            logs.stream().map(event -> ((Number) event.getArgumentArray()[4]).longValue()).toList())
        .isSorted();
  }

  private record ObservedRun(
      SimpleScore score,
      List<String> assignments,
      long scoreCalculations,
      long moves,
      List<ObservedStep> steps,
      List<List<String>> publications) {}

  private record ObservedStep(
      int index,
      int phase,
      long generation,
      SimpleScore beforeScore,
      SimpleScore bestBeforeScore,
      SimpleScore score,
      SimpleScore bestScore,
      SimpleScore candidateScore,
      boolean bestImproved,
      GeneticAlgorithmOutcome outcome,
      long probes,
      long acceptedProbes,
      boolean seeding,
      boolean crossed,
      long nativeId,
      List<Object> fields,
      Object assignments) {}

  private static final class LogCapture extends AppenderBase<ILoggingEvent>
      implements AutoCloseable {
    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final Logger logger =
        (Logger) LoggerFactory.getLogger(DefaultGeneticAlgorithmPhase.class);
    private final Level previousLevel = logger.getLevel();
    private final boolean previousAdditive = logger.isAdditive();

    LogCapture(Level level) {
      setContext(logger.getLoggerContext());
      start();
      logger.addAppender(this);
      logger.setAdditive(false);
      logger.setLevel(level);
    }

    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      events.add(event);
    }

    List<ILoggingEvent> steps() {
      return events.stream()
          .filter(
              event ->
                  event.getLevel() == Level.DEBUG
                      && event.getMessage().contains("Genetic Algorithm step ("))
          .toList();
    }

    List<ILoggingEvent> summaries() {
      return events.stream()
          .filter(
              event ->
                  event.getLevel() == Level.INFO
                      && event.getMessage().contains("Genetic Algorithm phase ("))
          .toList();
    }

    @Override
    public void close() {
      logger.detachAppender(this);
      logger.setLevel(previousLevel);
      logger.setAdditive(previousAdditive);
      stop();
    }
  }
}
