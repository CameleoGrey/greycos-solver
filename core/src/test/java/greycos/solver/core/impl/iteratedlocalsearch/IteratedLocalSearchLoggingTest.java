package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchStepLoggingMode;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.EmptyMoves;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.LandscapeScoreCalculator;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.move.SolutionAssignmentMove;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** Logger levels and the move-description counter are shared across solver tests. */
@Isolated
@Timeout(30)
class IteratedLocalSearchLoggingTest {
  private static final AtomicInteger MOVE_STRINGS = new AtomicInteger();
  private static final int[] SCORES = {0, 3, 1, 2, 3, 4};

  @ParameterizedTest
  @NullSource
  @EnumSource(value = LocalSearchStepLoggingMode.class, names = "ALL")
  void defaultAndAllLogEachCommittedStepWithPreExecutionDescriptions(
      LocalSearchStepLoggingMode mode) {
    var phase = scriptedPhase(mode);
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var run = run(phase, SCORES);
      assertThat(run.score()).isEqualTo(SimpleScore.of(4));
      assertThat(run.value()).isEqualTo("5:4");
      assertThat(capture.steps()).hasSize(5);
      assertThat(capture.steps())
          .extracting(event -> event.getArgumentArray()[1])
          .containsExactly(0, 1, 2, 3, 4);
      assertThat(capture.steps())
          .extracting(event -> event.getArgumentArray()[4])
          .containsExactly("new", "   ", "   ", "   ", "new");
      assertThat(capture.steps())
          .extracting(event -> event.getArgumentArray()[8])
          .containsExactly(
              "e0 {0:0 -> 1:3}",
              "e0 {1:3 -> 2:1}",
              "e0 {2:1 -> 3:2}",
              "e0 {3:2 -> 4:3}",
              "e0 {4:3 -> 5:4}");
      assertThat(capture.steps())
          .allSatisfy(
              event -> {
                assertThat(event.getArgumentArray()[3]).isInstanceOf(SimpleScore.class);
                assertThat(event.getArgumentArray()[5]).isInstanceOf(SimpleScore.class);
                assertThat(event.getFormattedMessage())
                    .contains(
                        "accepted/selected move count (1/1)",
                        "phase (0)",
                        "origin (LOCAL_SEARCH)",
                        "strength (1)");
              });
      assertThat(capture.steps())
          .extracting(event -> ((Number) event.getArgumentArray()[2]).longValue())
          .isSorted();
      assertThat(MOVE_STRINGS).hasValue(5);
      assertThat(capture.summaries())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage())
                      .contains(
                          "time spent (",
                          "environment mode (REPRODUCIBLE)",
                          "best score (4)",
                          "move evaluation speed (",
                          "step total (5)",
                          "completion reason (NO_PROGRESS)",
                          "iterations (1)",
                          "episodes (1)"));
      assertThat(capture.events)
          .noneSatisfy(
              event ->
                  assertThat(event.getLoggerName())
                      .isEqualTo(DefaultLocalSearchPhase.class.getName()));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void bestOnlyUsesSolverBestAndFormatsOnlyPotentialBestSteps(String workers) {
    var phase =
        scriptedPhase(LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED).withMoveThreadCount(workers);
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var run = run(phase, SCORES);
      assertThat(run.steps())
          .extracting(ObservedStep::improved)
          .containsExactly(true, false, false, false, true);
      assertThat(capture.steps())
          .extracting(event -> event.getArgumentArray()[1])
          .containsExactly(0, 4);
      assertThat(capture.steps())
          .extracting(event -> event.getArgumentArray()[8])
          .containsExactly("e0 {0:0 -> 1:3}", "e0 {4:3 -> 5:4}");
      assertThat(capture.steps())
          .allSatisfy(event -> assertThat(event.getFormattedMessage()).contains("new best score"));
      assertThat(MOVE_STRINGS).hasValue(2);
      assertThat(capture.messages("ILS episode (")).hasSize(2);
      assertThat(capture.messages("ILS iteration (")).hasSize(1);
    }
  }

  @ParameterizedTest
  @EnumSource(LocalSearchStepLoggingMode.class)
  void perturbationLogsIntermediateBestEvenWhenInverseShakeRestoresIncumbent(
      LocalSearchStepLoggingMode mode) {
    var phase =
        phase(EmptyMoves.class, CountingMoves.class, 5)
            .withPerturbationStrengths(2)
            .withPerturbationAttemptLimit(2);
    phase.getLocalSearchConfig().setStepLoggingMode(mode);
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var run = run(phase, 0, 5);
      assertThat(run.score()).isEqualTo(SimpleScore.of(5));
      assertThat(run.diagnostics().noChangeCount()).isEqualTo(1);
      assertThat(run.steps()).hasSize(2);
      assertThat(capture.steps()).hasSize(mode == LocalSearchStepLoggingMode.ALL ? 2 : 1);
      assertThat(capture.steps().getFirst().getFormattedMessage())
          .contains(
              "ILS step (0)",
              "new best score (5)",
              "picked move (e0 {0:0 -> 1:5})",
              "origin (PERTURBATION)",
              "strength (2)");
      assertThat(capture.messages("ILS iteration ("))
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage())
                      .contains(
                          "NO_PERTURBATION",
                          "candidate (unavailable)",
                          "incumbent (0)",
                          "solver best (5)"));
    }
  }

  @Test
  void stepIndicesContinueAcrossPerturbationsAndFreshEpisodes() {
    var phase = phase(CountingMoves.class, CountingMoves.class, 1).withIterationCountLimit(2);
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var run = run(phase, 0, 1, 2, 3, 4, 5);
      assertThat(run.score()).isEqualTo(SimpleScore.of(5));
      assertThat(capture.steps())
          .extracting(event -> event.getArgumentArray()[1])
          .containsExactly(0, 1, 2, 3, 4);
      assertThat(capture.steps())
          .extracting(event -> event.getArgumentArray()[10])
          .containsExactly(
              IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH,
              IteratedLocalSearchStepScope.Origin.PERTURBATION,
              IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH,
              IteratedLocalSearchStepScope.Origin.PERTURBATION,
              IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH);
      assertThat(capture.messages("ILS episode (")).hasSize(6);
      assertThat(capture.messages("ILS iteration (")).hasSize(2);
    }
  }

  @Test
  void vnsStepsShowUsedStrengthAndIterationSummariesShowSettledIncumbent() {
    var phase =
        phase(EmptyMoves.class, CountingMoves.class, 2)
            .withPerturbationStrengths(1, 2, 3)
            .withIterationCountLimit(3);
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var run = run(phase, 0, -1, 2, -3, -4);
      assertThat(run.score()).isEqualTo(SimpleScore.of(2));
      assertThat(capture.steps())
          .extracting(event -> event.getArgumentArray()[11])
          .containsExactly(1, 2, 2, 1);
      var iterations = capture.messages("ILS iteration (");
      assertThat(iterations).hasSize(3);
      assertThat(iterations.get(0).getFormattedMessage())
          .contains("REJECTED", "candidate (-1)", "incumbent (0)");
      assertThat(iterations.get(1).getFormattedMessage())
          .contains("ACCEPTED", "candidate (2)", "incumbent (2)");
      assertThat(iterations.get(2).getFormattedMessage())
          .contains("REJECTED", "candidate (-3)", "incumbent (2)");
      assertThat(capture.steps()).hasSize(run.steps().size());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void loggingModesPreserveAssignmentsScoresStepsAndWorkCounts(String workers) {
    ObservedRun baseline = null;
    for (var level : List.of(Level.INFO, Level.DEBUG)) {
      for (var mode : LocalSearchStepLoggingMode.values()) {
        try (var capture = new LogCapture(level, Level.DEBUG)) {
          var run = run(scriptedPhase(mode).withMoveThreadCount(workers), SCORES);
          if (baseline == null) baseline = run;
          assertThat(run.score()).isEqualTo(baseline.score());
          assertThat(run.value()).isEqualTo(baseline.value());
          assertThat(run.steps()).isEqualTo(baseline.steps());
          assertThat(run.calculations()).isEqualTo(baseline.calculations());
          assertThat(run.moves()).isEqualTo(baseline.moves());
          assertThat(run.diagnostics())
              .usingRecursiveComparison()
              .ignoringFields(
                  "snapshotNanos", "restorationNanos", "resourceSetupNanos", "episodeSetupNanos")
              .isEqualTo(baseline.diagnostics());
          assertThat(capture.summaries()).hasSize(1);
          if (level == Level.INFO) {
            assertThat(capture.steps()).isEmpty();
            assertThat(MOVE_STRINGS).hasValue(0);
          }
        }
      }
    }
  }

  @Test
  void traceRetainsMoveDiagnosticsWhileBestOnlyStillFiltersStepRecords() {
    try (var capture = new LogCapture(Level.TRACE, Level.TRACE)) {
      run(scriptedPhase(LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED), SCORES);
      assertThat(capture.steps()).hasSize(2);
      assertThat(
              capture.events.stream()
                  .filter(
                      event ->
                          event.getLevel() == Level.TRACE
                              && event.getMessage().contains("Move index ("))
                  .toList())
          .hasSize(5);
    }
  }

  @Test
  void abortedDecisionAtAttemptLimitDoesNotLogOrFormatAnUncommittedMove() {
    var phase = scriptedPhase(LocalSearchStepLoggingMode.ALL).withEpisodeCandidateAttemptLimit(1);
    phase
        .getLocalSearchConfig()
        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(2));
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var run = run(phase, SCORES);
      assertThat(run.steps()).isEmpty();
      assertThat(capture.steps()).isEmpty();
      assertThat(MOVE_STRINGS).hasValue(0);
      assertThat(capture.summaries()).hasSize(1);
    }
  }

  @Test
  void enclosingTerminationLogsCommittedStepOnceAndKeepsCompletionSummary() {
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var run =
          run(
              scriptedPhase(LocalSearchStepLoggingMode.ALL),
              solver ->
                  solver.addPhaseLifecycleListener(
                      new PhaseLifecycleListenerAdapter<>() {
                        @Override
                        public void stepEnded(AbstractStepScope<TestdataSolution> step) {
                          solver.terminateEarly();
                        }
                      }),
              SCORES);
      assertThat(run.steps()).hasSize(1);
      assertThat(capture.steps()).hasSize(1);
      assertThat(capture.messages("ILS iteration (")).isEmpty();
      assertThat(capture.summaries())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage())
                      .contains("completion reason (ENCLOSING_TERMINATION)", "step total (1)"));
    }
  }

  @Test
  void migrantAdoptionDoesNotInventACommittedStep() {
    var phase =
        phase(EmptyMoves.class, EmptyMoves.class, 2)
            .withTerminationConfig(new TerminationConfig().withBestScoreLimit("10"));
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var run =
          run(
              phase,
              solver ->
                  solver.addPhaseLifecycleListener(
                      new PhaseLifecycleListenerAdapter<>() {
                        @Override
                        public void stepStarted(AbstractStepScope<TestdataSolution> step) {
                          var director = step.getScoreDirector();
                          var migrant = director.cloneWorkingSolution();
                          migrant
                              .getEntityList()
                              .getFirst()
                              .setValue(migrant.getValueList().getLast());
                          migrant.setScore(SimpleScore.of(10));
                          var move =
                              new SolutionAssignmentMove<>(
                                  SolutionAssignments.captureComplete(
                                          director.getSolutionDescriptor(), migrant)
                                      .rebase(director));
                          step.getPhaseScope()
                              .getSolverScope()
                              .setPendingMoveIfBetter(
                                  move, InnerScore.fullyAssigned(SimpleScore.of(10)), true);
                        }
                      }),
              0,
              10);
      assertThat(run.score()).isEqualTo(SimpleScore.of(10));
      assertThat(run.steps()).isEmpty();
      assertThat(capture.steps()).isEmpty();
      assertThat(capture.summaries())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage())
                      .contains("best score (10)", "step total (0)"));
    }
  }

  @Test
  void concurrentIslandsIdentifyTheirPhaseAndKeepSeparateStepSequences() {
    var phase = scriptedPhase(LocalSearchStepLoggingMode.ALL);
    var config =
        config(phase)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMoveThreadCount("NONE")
                    .withMigrationFrequency(100)
                    .withReceiveGlobalUpdateFrequency(100)
                    .withPhaseConfigList(List.of(phase)));
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      var solver =
          (DefaultSolver<TestdataSolution>)
              SolverFactory.<TestdataSolution>create(config).buildSolver();
      var result = solver.solve(problem(SCORES));
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(4));
      assertThat(capture.steps()).hasSize(10);
      for (int island = 0; island < 2; island++) {
        var suffix = ", island (" + island + ")";
        var steps =
            capture.steps().stream()
                .filter(event -> suffix.equals(event.getArgumentArray()[12]))
                .toList();
        assertThat(steps)
            .extracting(event -> event.getArgumentArray()[1])
            .containsExactly(0, 1, 2, 3, 4);
        assertThat(steps)
            .allSatisfy(
                event ->
                    assertThat(event.getFormattedMessage())
                        .contains("phase (0)")
                        .endsWith(suffix + "."));
      }
      assertThat(capture.summaries()).hasSize(2);
      solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
    }
  }

  private static IteratedLocalSearchPhaseConfig scriptedPhase(LocalSearchStepLoggingMode mode) {
    var phase = phase(CountingMoves.class, EmptyMoves.class, 5);
    phase.getLocalSearchConfig().setStepLoggingMode(mode);
    return phase;
  }

  private static IteratedLocalSearchPhaseConfig phase(
      Class<? extends MoveIteratorFactory> improvement,
      Class<? extends MoveIteratorFactory> perturbation,
      int episodeSteps) {
    return new IteratedLocalSearchPhaseConfig()
        .withMoveThreadCount("NONE")
        .withLocalSearch(
            new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(selector(improvement))
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(100))
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(episodeSteps)))
        .withPerturbationMoveSelectorConfig(selector(perturbation))
        .withPerturbationStrengths(1)
        .withPerturbationAttemptLimit(8)
        .withEpisodeCandidateAttemptLimit(100)
        .withIterationCountLimit(1);
  }

  private static MoveIteratorFactoryConfig selector(Class<? extends MoveIteratorFactory> type) {
    return new MoveIteratorFactoryConfig()
        .withMoveIteratorFactoryClass(type)
        .withSelectionOrder(SelectionOrder.RANDOM);
  }

  private static SolverConfig config(IteratedLocalSearchPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(LandscapeScoreCalculator.class)
        .withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
        .withRandomSeed(0L)
        .withPhases(phase);
  }

  private static ObservedRun run(IteratedLocalSearchPhaseConfig phase, int... scores) {
    return run(phase, solver -> {}, scores);
  }

  private static ObservedRun run(
      IteratedLocalSearchPhaseConfig phase,
      Consumer<DefaultSolver<TestdataSolution>> setup,
      int... scores) {
    MOVE_STRINGS.set(0);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config(phase)).buildSolver();
    var steps = new ArrayList<ObservedStep>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            var ils = (IteratedLocalSearchStepScope<TestdataSolution>) step;
            assertThat(new LandscapeScoreCalculator().calculateScore(step.getWorkingSolution()))
                .isEqualTo(step.getScore().raw());
            steps.add(
                new ObservedStep(
                    step.getStepIndex(),
                    step.<SimpleScore>getScore().raw(),
                    value(step.getWorkingSolution()),
                    step.getBestScoreImproved(),
                    ils.getOrigin(),
                    ils.getAcceptedMoveCount(),
                    ils.getSelectedMoveCount()));
          }
        });
    setup.accept(solver);
    var result = solver.solve(problem(scores));
    assertThat(new LandscapeScoreCalculator().calculateScore(result)).isEqualTo(result.getScore());
    solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
    var diagnostics =
        ((DefaultIteratedLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst())
            .getDiagnostics();
    return new ObservedRun(
        result.getScore(),
        value(result),
        solver.getScoreCalculationCount(),
        solver.getMoveEvaluationCount(),
        List.copyOf(steps),
        diagnostics);
  }

  private static TestdataSolution problem(int... scores) {
    var problem = new TestdataSolution("logging");
    var values = new ArrayList<TestdataValue>();
    for (int i = 0; i < scores.length; i++) values.add(new TestdataValue(i + ":" + scores[i]));
    problem.setValueList(values);
    problem.setEntityList(List.of(new TestdataEntity("e0", values.getFirst())));
    return problem;
  }

  private static String value(TestdataSolution solution) {
    return solution.getEntityList().getFirst().getValue().getCode();
  }

  public static class CountingMoves
      implements MoveIteratorFactory<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public long getSize(ScoreDirector<TestdataSolution> director) {
      return 1;
    }

    @Override
    public Iterator<Move<TestdataSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataSolution> director) {
      var solution = director.getWorkingSolution();
      var entity = solution.getEntityList().getFirst();
      var target =
          solution
              .getValueList()
              .get(
                  (solution.getValueList().indexOf(entity.getValue()) + 1)
                      % solution.getValueList().size());
      var descriptor =
          ((InnerScoreDirector<TestdataSolution, ?>) director)
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataEntity.class)
              .getGenuineVariableDescriptor("value");
      return List.<Move<TestdataSolution>>of(new CountingChangeMove(descriptor, entity, target))
          .iterator();
    }

    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      return createOriginalMoveIterator(director);
    }
  }

  private static final class CountingChangeMove extends ChangeMove<TestdataSolution> {
    private CountingChangeMove(
        GenuineVariableDescriptor<TestdataSolution> descriptor, Object entity, Object value) {
      super(descriptor, entity, value);
    }

    @Override
    public ChangeMove<TestdataSolution> rebase(ScoreDirector<TestdataSolution> destination) {
      return new CountingChangeMove(
          variableDescriptor,
          destination.lookUpWorkingObject(entity),
          destination.lookUpWorkingObject(toPlanningValue));
    }

    @Override
    public String toString() {
      MOVE_STRINGS.incrementAndGet();
      return super.toString();
    }
  }

  private record ObservedStep(
      int index,
      SimpleScore score,
      String value,
      boolean improved,
      IteratedLocalSearchStepScope.Origin origin,
      long accepted,
      long selected) {}

  private record ObservedRun(
      SimpleScore score,
      String value,
      long calculations,
      long moves,
      List<ObservedStep> steps,
      DefaultIteratedLocalSearchPhase.Diagnostics diagnostics) {}

  private static final class LogCapture extends AppenderBase<ILoggingEvent>
      implements AutoCloseable {
    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final Logger phaseLogger =
        (Logger) LoggerFactory.getLogger(DefaultIteratedLocalSearchPhase.class);
    private final Logger localLogger =
        (Logger) LoggerFactory.getLogger("greycos.solver.core.impl.localsearch");
    private final Level previousPhaseLevel = phaseLogger.getLevel();
    private final Level previousLocalLevel = localLogger.getLevel();
    private final boolean previousPhaseAdditive = phaseLogger.isAdditive();
    private final boolean previousLocalAdditive = localLogger.isAdditive();

    private LogCapture(Level phaseLevel, Level localLevel) {
      setContext(phaseLogger.getLoggerContext());
      start();
      phaseLogger.addAppender(this);
      phaseLogger.setLevel(phaseLevel);
      phaseLogger.setAdditive(false);
      localLogger.addAppender(this);
      localLogger.setLevel(localLevel);
      localLogger.setAdditive(false);
    }

    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      events.add(event);
    }

    private List<ILoggingEvent> messages(String fragment) {
      return events.stream()
          .filter(
              event ->
                  event.getLoggerName().equals(phaseLogger.getName())
                      && event.getMessage().contains(fragment))
          .toList();
    }

    private List<ILoggingEvent> steps() {
      return messages("ILS step (");
    }

    private List<ILoggingEvent> summaries() {
      return messages("ended: time spent (");
    }

    @Override
    public void close() {
      phaseLogger.detachAppender(this);
      phaseLogger.setLevel(previousPhaseLevel);
      phaseLogger.setAdditive(previousPhaseAdditive);
      localLogger.detachAppender(this);
      localLogger.setLevel(previousLocalLevel);
      localLogger.setAdditive(previousLocalAdditive);
      stop();
    }
  }
}
