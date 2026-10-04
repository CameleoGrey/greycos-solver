package greycos.solver.core.impl.localsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchStepLoggingMode;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** Logging configuration is shared with other solver tests. */
@Isolated
@Timeout(30)
class LocalSearchLoggingTest {

  private static final int[] SCORES = {0, 3, 1, 2, 3, 4};
  private static final AtomicInteger MOVE_STRINGS = new AtomicInteger();

  static Stream<Arguments> allStepModes() {
    return Stream.of(Arguments.of((Object) null), Arguments.of(LocalSearchStepLoggingMode.ALL));
  }

  static Stream<Arguments> pendingMoveConfigurations() {
    return Stream.of(LocalSearchType.LATE_ACCEPTANCE, LocalSearchType.GUIDED_LOCAL_SEARCH)
        .flatMap(type -> Stream.of("NONE", "2").map(threads -> Arguments.of(type, threads)));
  }

  @ParameterizedTest
  @MethodSource("allStepModes")
  void defaultAndAllLogEveryCompletedStep(LocalSearchStepLoggingMode mode) {
    try (var capture = new LogCapture(Level.DEBUG)) {
      var run =
          run(scriptedPhase(LocalSearchType.LATE_ACCEPTANCE, mode), "NONE", false, problem(0));

      assertScriptedRun(run);
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
              "e0 {v0 -> v1}", "e0 {v1 -> v2}", "e0 {v2 -> v3}", "e0 {v3 -> v4}", "e0 {v4 -> v5}");
      assertThat(MOVE_STRINGS).hasValue(5);
      assertThat(capture.summaries()).hasSize(1);
    }
  }

  @Test
  void bestOnlySkipsWorseCurrentImprovementAndEqualBestStepsBeforeFormatting() {
    try (var capture = new LogCapture(Level.DEBUG)) {
      var run =
          run(
              scriptedPhase(
                  LocalSearchType.LATE_ACCEPTANCE, LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED),
              "NONE",
              false,
              problem(0));

      assertScriptedRun(run);
      assertBestStepLogs(capture);
      assertThat(MOVE_STRINGS).hasValue(2);
      assertThat(capture.summaries())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage())
                      .contains("best score (4)", "step total (5)"));
    }
  }

  @ParameterizedTest
  @EnumSource(LocalSearchStepLoggingMode.class)
  void infoSkipsMoveFormattingEvenWhenDecidersHaveDebugEnabled(LocalSearchStepLoggingMode mode) {
    try (var capture = new LogCapture(Level.INFO, Level.DEBUG)) {
      var run =
          run(scriptedPhase(LocalSearchType.LATE_ACCEPTANCE, mode), "NONE", false, problem(0));

      assertScriptedRun(run);
      assertThat(capture.steps()).isEmpty();
      assertThat(capture.summaries()).hasSize(1);
      assertThat(MOVE_STRINGS).hasValue(0);
    }
  }

  @Test
  void phaseLoggerAloneEnablesPreMoveDescriptions() {
    try (var capture = new LogCapture(Level.DEBUG, Level.INFO)) {
      run(
          scriptedPhase(
              LocalSearchType.LATE_ACCEPTANCE, LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED),
          "NONE",
          false,
          problem(0));

      assertBestStepLogs(capture);
      assertThat(MOVE_STRINGS).hasValue(2);
    }
  }

  @Test
  void traceKeepsMoveDiagnosticsAndStillFiltersCompletedStepSummaries() {
    try (var capture = new LogCapture(Level.TRACE)) {
      var run =
          run(
              scriptedPhase(
                  LocalSearchType.LATE_ACCEPTANCE, LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED),
              "NONE",
              false,
              problem(0));

      assertScriptedRun(run);
      assertBestStepLogs(capture);
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
  void moveWorkersPreserveFilteringAndPreMoveDescriptions() {
    try (var capture = new LogCapture(Level.DEBUG)) {
      var run =
          run(
              scriptedPhase(
                  LocalSearchType.LATE_ACCEPTANCE, LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED),
              "2",
              false,
              problem(0));

      assertScriptedRun(run);
      assertBestStepLogs(capture);
      assertThat(MOVE_STRINGS).hasValue(2);
    }
  }

  @ParameterizedTest
  @MethodSource("pendingMoveConfigurations")
  void pendingMovesUseTheSameFilteringAcrossDeciders(LocalSearchType type, String threads) {
    try (var capture = new LogCapture(Level.DEBUG)) {
      var run =
          run(
              scriptedPhase(type, LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED),
              threads,
              true,
              problem(0));

      assertScriptedRun(run);
      assertBestStepLogs(capture);
      assertThat(MOVE_STRINGS).hasValue(2);
    }
  }

  @ParameterizedTest
  @EnumSource(LocalSearchStepLoggingMode.class)
  void prematureRejectedFallbackIsLoggedOnlyInAllMode(LocalSearchStepLoggingMode mode) {
    var phase =
        scriptedPhase(LocalSearchType.LATE_ACCEPTANCE, mode)
            .withTerminationConfig(new TerminationConfig().withMoveCountLimit(1L));
    try (var capture = new LogCapture(Level.DEBUG)) {
      var run = run(phase, "NONE", false, problem(5));

      assertThat(run.score()).isEqualTo(SimpleScore.of(4));
      assertThat(run.values()).containsExactly("v5");
      assertThat(run.steps())
          .singleElement()
          .satisfies(
              step -> {
                assertThat(step.score()).isEqualTo(SimpleScore.ZERO);
                assertThat(step.improved()).isFalse();
                assertThat(step.accepted()).isZero();
                assertThat(step.selected()).isEqualTo(1L);
              });
      if (mode == LocalSearchStepLoggingMode.ALL) {
        assertThat(capture.steps())
            .singleElement()
            .satisfies(
                event ->
                    assertThat(event.getFormattedMessage())
                        .contains(
                            "score (0)",
                            "best score (4)",
                            "terminated prematurely after selecting 1 moves"));
      } else {
        assertThat(capture.steps()).isEmpty();
        assertThat(MOVE_STRINGS).hasValue(0);
      }
      assertThat(capture.summaries()).hasSize(1);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = LocalSearchType.class,
      names = {"LATE_ACCEPTANCE", "GREAT_DELUGE"})
  void fixedStepSearchIsIdenticalAcrossLoggingModesAndLevels(LocalSearchType type) {
    ObservedRun all;
    try (var capture = new LogCapture(Level.DEBUG)) {
      all =
          run(
              randomPhase(type, LocalSearchStepLoggingMode.ALL),
              "NONE",
              false,
              problem(0, 1, 2, 3));
      assertThat(capture.steps()).hasSize(50);
    }
    try (var capture = new LogCapture(Level.DEBUG)) {
      var best =
          run(
              randomPhase(type, LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED),
              "NONE",
              false,
              problem(0, 1, 2, 3));
      assertThat(best).isEqualTo(all);
      assertThat(capture.steps())
          .hasSize((int) best.steps().stream().filter(ObservedStep::improved).count());
      assertThat(capture.steps()).hasSizeLessThan(50);
    }
    try (var capture = new LogCapture(Level.INFO)) {
      var info =
          run(
              randomPhase(type, LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED),
              "NONE",
              false,
              problem(0, 1, 2, 3));
      assertThat(info).isEqualTo(all);
      assertThat(capture.steps()).isEmpty();
      assertThat(capture.summaries()).hasSize(1);
    }
  }

  private static LocalSearchPhaseConfig scriptedPhase(
      LocalSearchType type, LocalSearchStepLoggingMode mode) {
    var phase =
        new LocalSearchPhaseConfig()
            .withLocalSearchType(type)
            .withMoveSelectorConfig(
                new MoveIteratorFactoryConfig()
                    .withMoveIteratorFactoryClass(NextValueMoves.class)
                    .withSelectionOrder(SelectionOrder.ORIGINAL))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5));
    phase.setStepLoggingMode(mode);
    return phase;
  }

  private static LocalSearchPhaseConfig randomPhase(
      LocalSearchType type, LocalSearchStepLoggingMode mode) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(type)
        .withStepLoggingMode(mode)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(50));
  }

  private static ObservedRun run(
      LocalSearchPhaseConfig phase, String threads, boolean pending, TestdataSolution problem) {
    MOVE_STRINGS.set(0);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(LandscapeScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withRandomSeed(0L)
            .withMoveThreadCount(threads)
            .withPhases(phase);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var steps = new ArrayList<ObservedStep>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> stepScope) {
            if (pending) {
              stepScope
                  .getPhaseScope()
                  .getSolverScope()
                  .setPendingMove(nextMove(stepScope.getScoreDirector()));
            }
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> stepScope) {
            var local = (LocalSearchStepScope<TestdataSolution>) stepScope;
            steps.add(
                new ObservedStep(
                    stepScope.<SimpleScore>getScore().raw(),
                    values(stepScope.getWorkingSolution()),
                    stepScope.getBestScoreImproved(),
                    local.getAcceptedMoveCount(),
                    local.getSelectedMoveCount()));
          }
        });
    var solution = solver.solve(problem);
    assertThat(solution.getScore())
        .isEqualTo(new LandscapeScoreCalculator().calculateScore(solution));
    solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
    return new ObservedRun(
        solution.getScore(),
        values(solution),
        solver.getScoreCalculationCount(),
        solver.getMoveEvaluationCount(),
        List.copyOf(steps));
  }

  private static void assertScriptedRun(ObservedRun run) {
    assertThat(run.score()).isEqualTo(SimpleScore.of(4));
    assertThat(run.values()).containsExactly("v5");
    assertThat(run.moves()).isEqualTo(5L);
    assertThat(run.steps())
        .extracting(ObservedStep::score)
        .containsExactly(
            SimpleScore.of(3),
            SimpleScore.of(1),
            SimpleScore.of(2),
            SimpleScore.of(3),
            SimpleScore.of(4));
    assertThat(run.steps())
        .extracting(ObservedStep::improved)
        .containsExactly(true, false, false, false, true);
    assertThat(run.steps())
        .allSatisfy(
            step -> {
              assertThat(step.accepted()).isEqualTo(1L);
              assertThat(step.selected()).isEqualTo(1L);
            });
  }

  private static void assertBestStepLogs(LogCapture capture) {
    assertThat(capture.steps()).hasSize(2);
    assertThat(capture.steps())
        .extracting(event -> event.getArgumentArray()[1])
        .containsExactly(0, 4);
    assertThat(capture.steps())
        .allSatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
              assertThat(event.getArgumentArray()[4]).isEqualTo("new");
              assertThat(event.getArgumentArray()[3]).isEqualTo(event.getArgumentArray()[5]);
              assertThat(event.getFormattedMessage())
                  .contains("accepted/selected move count (1/1)");
            });
    assertThat(capture.steps())
        .extracting(event -> event.getArgumentArray()[8])
        .containsExactly("e0 {v0 -> v1}", "e0 {v4 -> v5}");
  }

  private static TestdataSolution problem(int... initialValueIndices) {
    var solution = new TestdataSolution("logging");
    var values = new ArrayList<TestdataValue>();
    for (int i = 0; i < SCORES.length; i++) values.add(new TestdataValue("v" + i));
    solution.setValueList(values);
    var entities = new ArrayList<TestdataEntity>();
    for (int i = 0; i < initialValueIndices.length; i++) {
      entities.add(new TestdataEntity("e" + i, values.get(initialValueIndices[i])));
    }
    solution.setEntityList(entities);
    return solution;
  }

  private static List<String> values(TestdataSolution solution) {
    return solution.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList();
  }

  private static CountingChangeMove nextMove(ScoreDirector<TestdataSolution> director) {
    var solution = director.getWorkingSolution();
    var entity = solution.getEntityList().getFirst();
    int index = solution.getValueList().indexOf(entity.getValue());
    var target = solution.getValueList().get((index + 1) % solution.getValueList().size());
    var descriptor =
        ((InnerScoreDirector<TestdataSolution, ?>) director)
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataEntity.class)
            .getGenuineVariableDescriptor("value");
    return new CountingChangeMove(descriptor, entity, target);
  }

  public static class NextValueMoves
      implements MoveIteratorFactory<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public long getSize(ScoreDirector<TestdataSolution> director) {
      return 1L;
    }

    @Override
    public Iterator<Move<TestdataSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataSolution> director) {
      return List.<Move<TestdataSolution>>of(nextMove(director)).iterator();
    }

    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      return createOriginalMoveIterator(director);
    }
  }

  public static class LandscapeScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(
          solution.getEntityList().stream()
              .mapToInt(
                  entity -> SCORES[Integer.parseInt(entity.getValue().getCode().substring(1))])
              .sum());
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
      SimpleScore score, List<String> values, boolean improved, long accepted, long selected) {}

  private record ObservedRun(
      SimpleScore score,
      List<String> values,
      long calculations,
      long moves,
      List<ObservedStep> steps) {}

  private static final class LogCapture extends AppenderBase<ILoggingEvent>
      implements AutoCloseable {
    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final Logger packageLogger =
        (Logger) LoggerFactory.getLogger("greycos.solver.core.impl.localsearch");
    private final Logger phaseLogger =
        (Logger) LoggerFactory.getLogger(DefaultLocalSearchPhase.class);
    private final Level previousPackageLevel = packageLogger.getLevel();
    private final Level previousPhaseLevel = phaseLogger.getLevel();
    private final boolean previousAdditive = packageLogger.isAdditive();

    private LogCapture(Level level) {
      this(level, level);
    }

    private LogCapture(Level phaseLevel, Level packageLevel) {
      setContext(packageLogger.getLoggerContext());
      start();
      packageLogger.addAppender(this);
      packageLogger.setAdditive(false);
      packageLogger.setLevel(packageLevel);
      phaseLogger.setLevel(phaseLevel);
    }

    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      events.add(event);
    }

    private List<ILoggingEvent> steps() {
      return events.stream()
          .filter(
              event -> event.getLevel() == Level.DEBUG && event.getMessage().contains("LS step ("))
          .toList();
    }

    private List<ILoggingEvent> summaries() {
      return events.stream()
          .filter(
              event ->
                  event.getLevel() == Level.INFO
                      && event.getMessage().contains("Local Search phase (")
                      && event.getMessage().contains("ended:"))
          .toList();
    }

    @Override
    public void close() {
      packageLogger.detachAppender(this);
      packageLogger.setLevel(previousPackageLevel);
      packageLogger.setAdditive(previousAdditive);
      phaseLogger.setLevel(previousPhaseLevel);
      stop();
    }
  }
}
