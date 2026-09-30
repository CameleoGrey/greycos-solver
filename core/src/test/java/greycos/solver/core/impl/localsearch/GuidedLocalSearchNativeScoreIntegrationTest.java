package greycos.solver.core.impl.localsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.FloatingScoreAccumulator;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchSearchMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchScale;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The same finite landscape exercises native score detection, three independent scoring backends,
 * temporary move undo, automatic GLS features, and ordered move workers.
 */
@Timeout(60)
class GuidedLocalSearchNativeScoreIntegrationTest {

  private static final BigDecimal QUARTER = new BigDecimal("0.25");
  private static final List<Family<?>> FAMILIES = families();

  static Stream<Family<?>> nativeFamilies() {
    return FAMILIES.stream();
  }

  static Stream<Arguments> familiesAndWorkers() {
    return nativeFamilies()
        .flatMap(family -> Stream.of("NONE", "2").map(workers -> Arguments.of(family, workers)));
  }

  @Test
  void matrixContainsEveryNativeScoreTypeWithoutCustomScoreDefinitions() {
    assertThat(FAMILIES).hasSize(16);
    var nativeScoreClasses =
        FAMILIES.stream().<Class<?>>map(family -> family.zero().getClass()).toList();
    assertThat(nativeScoreClasses)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrder(
            SimpleScore.class,
            HardSoftScore.class,
            HardMediumSoftScore.class,
            BendableScore.class,
            SimpleBigDecimalScore.class,
            HardSoftBigDecimalScore.class,
            HardMediumSoftBigDecimalScore.class,
            BendableBigDecimalScore.class,
            SimpleFloatScore.class,
            HardSoftFloatScore.class,
            HardMediumSoftFloatScore.class,
            BendableFloatScore.class,
            SimpleDoubleScore.class,
            HardSoftDoubleScore.class,
            HardMediumSoftDoubleScore.class,
            BendableDoubleScore.class);
  }

  @ParameterizedTest(name = "{0}: Easy, Incremental, Constraint Streams; NONE and 2 workers")
  @MethodSource("nativeFamilies")
  <Score_ extends Score<Score_>>
      void automaticGlsHasIdenticalBusinessTrajectoriesAcrossBackendsAndWorkers(
          Family<Score_> family) {
    List<Step<Score_>> reference = null;
    GuidedLocalSearchDecider.Statistics referenceStatistics = null;
    for (var backend : Backend.values()) {
      for (var workers : List.of("NONE", "2")) {
        DefaultSolver<FamilySolution<Score_>> solver =
            solver(config(family, backend, workers, true, 32));
        var trace = recordSteps(solver);
        var bestScores = recordBestSolutions(solver);
        var problem = family.problem();
        assertThat(oracle(problem)).isEqualTo(family.score(initialLevels(family.levelCount())));
        // Both one-variable neighbors are worse; BB is the unique optimum among all four states.
        var mixed = mixedLevels(family.levelCount());
        assertThat(family.score(mixed)).isLessThan(oracle(problem));
        assertThat(family.zero()).isGreaterThan(oracle(problem));

        var result = solver.solve(problem);

        assertOptimum(result);
        assertThat(problem.entities)
            .extracting(entity -> entity.choice.id)
            .containsExactly("A", "A");
        assertThat(trace)
            .as("%s / %s / %s", family, backend, workers)
            .hasSizeGreaterThanOrEqualTo(2);
        assertThat(trace.getFirst().choices()).containsExactly("B", "A");
        assertThat(trace.getFirst().score()).isEqualTo(family.score(mixed));
        assertThat(trace.getLast().choices()).containsExactly("B", "B");
        if (family.levelCount() > 1) {
          assertThat(trace.stream().anyMatch(step -> !step.score().isFeasible())).isTrue();
        }
        assertThat(bestScores)
            .contains(family.zero())
            .allSatisfy(score -> assertThat(score).isGreaterThanOrEqualTo(oracle(problem)));
        var statistics = statistics(solver);
        assertThat(statistics.penaltyUpdates()).isPositive();
        if (reference == null) {
          reference = List.copyOf(trace);
          referenceStatistics = statistics;
        } else {
          assertThat(trace)
              .as("%s / %s / %s", family, backend, workers)
              .containsExactlyElementsOf(reference);
          assertThat(statistics).isEqualTo(referenceStatistics);
        }
      }
    }
  }

  @ParameterizedTest(name = "{0}, move workers {1}")
  @MethodSource("familiesAndWorkers")
  <Score_ extends Score<Score_>> void adoptedAssignmentsResetAutomaticGuidanceAndKeepNativeScores(
      Family<Score_> family, String workers) {
    DefaultSolver<FamilySolution<Score_>> solver =
        solver(config(family, Backend.INCREMENTAL, workers, false, 4));
    var trace = recordSteps(solver);
    recordBestSolutions(solver);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<FamilySolution<Score_>> scope) {
            var gls = decider(solver);
            if (scope.getStepIndex() == 1) {
              assertThat(gls.getAutomaticScale(family.levelCount() - 1))
                  .isNotEqualTo(GuidedLocalSearchScale.ONE);
            } else if (scope.getStepIndex() == 2) {
              assertThat(gls.getFocusScoreLevelIndex()).isEqualTo(family.levelCount() - 1);
              for (int level = 0; level < family.levelCount(); level++) {
                assertThat(gls.getAutomaticScale(level)).isEqualTo(GuidedLocalSearchScale.ONE);
              }
            }
          }

          @Override
          public void stepEnded(AbstractStepScope<FamilySolution<Score_>> scope) {
            if (scope.getStepIndex() == 0) {
              var working = scope.getWorkingSolution();
              var descriptor =
                  scope
                      .getScoreDirector()
                      .getSolutionDescriptor()
                      .findEntityDescriptorOrFail(Assignment.class)
                      .getGenuineVariableDescriptor("choice");
              var adopt =
                  new ChangeMove<>(
                      descriptor, working.entities.getLast(), working.choices.getLast());
              // Island adoption uses this same pending move/reset path after importing an improved
              // assignment.
              scope
                  .getPhaseScope()
                  .getSolverScope()
                  .setPendingMoveIfBetter(adopt, InnerScore.fullyAssigned(family.zero()), true);
            }
          }
        });

    assertOptimum(solver.solve(family.problem()));
    assertThat(trace).hasSize(4);
    assertThat(trace.get(0).choices()).containsExactly("B", "A");
    assertThat(trace.get(1)).isEqualTo(new Step<>(List.of("B", "B"), family.zero()));
    assertThat(statistics(solver).penaltyUpdates()).isGreaterThanOrEqualTo(2L);
    var firstTrace = List.copyOf(trace);
    trace.clear();

    assertOptimum(solver.solve(family.problem()));
    assertThat(trace).containsExactlyElementsOf(firstTrace);
  }

  @ParameterizedTest(name = "{0}, island move workers {1}")
  @MethodSource("familiesAndWorkers")
  <Score_ extends Score<Score_>> void islandShorthandSupportsAutomaticGuidanceForEveryNativeScore(
      Family<Score_> family, String workers) {
    var config = config(family, Backend.CONSTRAINT_STREAMS, "NONE", true, 32);
    config.withPhases(
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMoveThreadCount(workers)
            .withMigrationFrequency(1)
            .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
            .withGuidedLocalSearchConfig(guidance())
            .withMoveSelectorConfig(originalChanges())
            .withTerminationConfig(termination(family, true, 64)));
    DefaultSolver<FamilySolution<Score_>> solver = solver(config);
    var publishedScores = recordBestSolutions(solver);

    assertOptimum(solver.solve(family.problem()));
    assertThat(publishedScores).contains(family.zero());
  }

  private static <Score_ extends Score<Score_>> SolverConfig config(
      Family<Score_> family, Backend backend, String workers, boolean stopAtOptimum, int steps) {
    var scoring = new ScoreDirectorFactoryConfig();
    switch (backend) {
      case EASY -> scoring.withEasyScoreCalculatorClass(FamilyEasyCalculator.class);
      case INCREMENTAL ->
          scoring.withIncrementalScoreCalculatorClass(FamilyIncrementalCalculator.class);
      case CONSTRAINT_STREAMS ->
          scoring
              .withConstraintProviderClass(FamilyConstraints.class)
              .withConstraintProviderCustomProperties(Map.of("familyName", family.name()));
    }
    return new SolverConfig()
        .withSolutionClass(family.solutionClass())
        .withEntityClasses(Assignment.class)
        .withScoreDirectorFactory(scoring)
        .withRandomSeed(0L)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withMoveThreadCount(workers)
        .withPhases(
            new LocalSearchPhaseConfig()
                .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
                .withGuidedLocalSearchConfig(guidance())
                .withMoveSelectorConfig(originalChanges())
                .withTerminationConfig(termination(family, stopAtOptimum, steps)));
  }

  private static GuidedLocalSearchConfig guidance() {
    // ALL_LEVELS and automatic features are intentionally left at their defaults.
    return new GuidedLocalSearchConfig()
        .withSearchMode(GuidedLocalSearchSearchMode.EXHAUSTIVE)
        .withPenaltyFactor(BigDecimal.TEN)
        .withFocusStepLimit(4)
        .withFocusPenaltyUpdateLimit(2);
  }

  private static ChangeMoveSelectorConfig originalChanges() {
    return new ChangeMoveSelectorConfig().withSelectionOrder(SelectionOrder.ORIGINAL);
  }

  private static TerminationConfig termination(Family<?> family, boolean stopAtOptimum, int steps) {
    var termination = new TerminationConfig().withStepCountLimit(steps);
    if (stopAtOptimum) {
      termination.withBestScoreLimit(family.zero().toString());
    }
    return termination;
  }

  private static <Score_ extends Score<Score_>> DefaultSolver<FamilySolution<Score_>> solver(
      SolverConfig config) {
    return (DefaultSolver<FamilySolution<Score_>>)
        SolverFactory.<FamilySolution<Score_>>create(config).buildSolver();
  }

  private static <Score_ extends Score<Score_>> List<Step<Score_>> recordSteps(
      DefaultSolver<FamilySolution<Score_>> solver) {
    var trace = new ArrayList<Step<Score_>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<FamilySolution<Score_>> scope) {
            var working = scope.getWorkingSolution();
            var expected = oracle(working);
            assertThat(scope.getScore().raw()).isEqualTo(expected);
            assertThat(working.getScore()).isEqualTo(expected);
            trace.add(
                new Step<>(
                    working.entities.stream().map(entity -> entity.choice.id).toList(), expected));
          }
        });
    return trace;
  }

  private static <Score_ extends Score<Score_>> List<Score_> recordBestSolutions(
      DefaultSolver<FamilySolution<Score_>> solver) {
    var scores = new CopyOnWriteArrayList<Score_>();
    solver.addEventListener(
        event -> {
          var best = event.getNewBestSolution();
          assertThat(best.getScore()).isEqualTo(oracle(best));
          scores.add(best.getScore());
        });
    return scores;
  }

  private static GuidedLocalSearchDecider.Statistics statistics(DefaultSolver<?> solver) {
    return decider(solver).getStatistics();
  }

  private static GuidedLocalSearchDecider<?> decider(DefaultSolver<?> solver) {
    var phase = (DefaultLocalSearchPhase<?>) solver.getPhaseList().getFirst();
    return (GuidedLocalSearchDecider<?>) phase.getDecider();
  }

  private static <Score_ extends Score<Score_>> void assertOptimum(
      FamilySolution<Score_> solution) {
    assertThat(solution.entities).extracting(entity -> entity.id).containsExactly("left", "right");
    assertThat(solution.entities).extracting(entity -> entity.choice.id).containsExactly("B", "B");
    assertThat(solution.getScore()).isEqualTo(solution.family.zero()).isEqualTo(oracle(solution));
  }

  /** An independent four-state oracle: it never invokes a calculator or the GLS machinery. */
  private static <Score_ extends Score<Score_>> Score_ oracle(FamilySolution<Score_> solution) {
    String state = solution.entities.getFirst().choice.id + solution.entities.getLast().choice.id;
    return switch (state) {
      case "AA" -> solution.family.score(initialLevels(solution.family.levelCount()));
      case "AB", "BA" -> solution.family.score(mixedLevels(solution.family.levelCount()));
      case "BB" -> solution.family.zero();
      default -> throw new IllegalArgumentException("Unexpected assignment state: " + state);
    };
  }

  private static long[] initialLevels(int levelCount) {
    var levels = new long[levelCount];
    levels[levelCount - 1] = -2L;
    return levels;
  }

  private static long[] mixedLevels(int levelCount) {
    return switch (levelCount) {
      case 1 -> new long[] {-5L};
      case 2 -> new long[] {-1L, -5L};
      case 3 -> new long[] {-1L, -3L, -5L};
      case 4 -> new long[] {-1L, -3L, -4L, -5L};
      default -> throw new IllegalArgumentException("Unexpected level count: " + levelCount);
    };
  }

  private static long cost(int level, int levelCount, int bCount) {
    if (bCount == 2) return 0L;
    if (bCount == 0) return level == levelCount - 1 ? 2L : 0L;
    if (level == levelCount - 1) return 5L;
    return level == 0 ? 1L : level + 2L;
  }

  private static BigDecimal decimal(long units) {
    return QUARTER.multiply(BigDecimal.valueOf(units));
  }

  private static List<Family<?>> families() {
    return List.of(
        new Family<>(
            "SimpleScore",
            SimpleLongSolution.class,
            SimpleLongSolution::new,
            1,
            false,
            levels -> SimpleScore.of(levels[0])),
        new Family<>(
            "HardSoftScore",
            HardSoftLongSolution.class,
            HardSoftLongSolution::new,
            2,
            false,
            levels -> HardSoftScore.of(levels[0], levels[1])),
        new Family<>(
            "HardMediumSoftScore",
            HardMediumSoftLongSolution.class,
            HardMediumSoftLongSolution::new,
            3,
            false,
            levels -> HardMediumSoftScore.of(levels[0], levels[1], levels[2])),
        new Family<>(
            "BendableScore",
            BendableLongSolution.class,
            BendableLongSolution::new,
            4,
            false,
            levels ->
                BendableScore.of(
                    new long[] {levels[0], levels[1]}, new long[] {levels[2], levels[3]})),
        new Family<>(
            "SimpleBigDecimalScore",
            SimpleDecimalSolution.class,
            SimpleDecimalSolution::new,
            1,
            false,
            levels -> SimpleBigDecimalScore.of(decimal(levels[0]))),
        new Family<>(
            "HardSoftBigDecimalScore",
            HardSoftDecimalSolution.class,
            HardSoftDecimalSolution::new,
            2,
            false,
            levels -> HardSoftBigDecimalScore.of(decimal(levels[0]), decimal(levels[1]))),
        new Family<>(
            "HardMediumSoftBigDecimalScore",
            HardMediumSoftDecimalSolution.class,
            HardMediumSoftDecimalSolution::new,
            3,
            false,
            levels ->
                HardMediumSoftBigDecimalScore.of(
                    decimal(levels[0]), decimal(levels[1]), decimal(levels[2]))),
        new Family<>(
            "BendableBigDecimalScore",
            BendableDecimalSolution.class,
            BendableDecimalSolution::new,
            4,
            false,
            levels ->
                BendableBigDecimalScore.of(
                    new BigDecimal[] {decimal(levels[0]), decimal(levels[1])},
                    new BigDecimal[] {decimal(levels[2]), decimal(levels[3])})),
        new Family<>(
            "SimpleFloatScore",
            SimpleFloatSolution.class,
            SimpleFloatSolution::new,
            1,
            true,
            levels -> SimpleFloatScore.of(levels[0] * 0.25F)),
        new Family<>(
            "HardSoftFloatScore",
            HardSoftFloatSolution.class,
            HardSoftFloatSolution::new,
            2,
            true,
            levels -> HardSoftFloatScore.of(levels[0] * 0.25F, levels[1] * 0.25F)),
        new Family<>(
            "HardMediumSoftFloatScore",
            HardMediumSoftFloatSolution.class,
            HardMediumSoftFloatSolution::new,
            3,
            true,
            levels ->
                HardMediumSoftFloatScore.of(
                    levels[0] * 0.25F, levels[1] * 0.25F, levels[2] * 0.25F)),
        new Family<>(
            "BendableFloatScore",
            BendableFloatSolution.class,
            BendableFloatSolution::new,
            4,
            true,
            levels ->
                BendableFloatScore.of(
                    new float[] {levels[0] * 0.25F, levels[1] * 0.25F},
                    new float[] {levels[2] * 0.25F, levels[3] * 0.25F})),
        new Family<>(
            "SimpleDoubleScore",
            SimpleDoubleSolution.class,
            SimpleDoubleSolution::new,
            1,
            true,
            levels -> SimpleDoubleScore.of(levels[0] * 0.25D)),
        new Family<>(
            "HardSoftDoubleScore",
            HardSoftDoubleSolution.class,
            HardSoftDoubleSolution::new,
            2,
            true,
            levels -> HardSoftDoubleScore.of(levels[0] * 0.25D, levels[1] * 0.25D)),
        new Family<>(
            "HardMediumSoftDoubleScore",
            HardMediumSoftDoubleSolution.class,
            HardMediumSoftDoubleSolution::new,
            3,
            true,
            levels ->
                HardMediumSoftDoubleScore.of(
                    levels[0] * 0.25D, levels[1] * 0.25D, levels[2] * 0.25D)),
        new Family<>(
            "BendableDoubleScore",
            BendableDoubleSolution.class,
            BendableDoubleSolution::new,
            4,
            true,
            levels ->
                BendableDoubleScore.of(
                    new double[] {levels[0] * 0.25D, levels[1] * 0.25D},
                    new double[] {levels[2] * 0.25D, levels[3] * 0.25D})));
  }

  enum Backend {
    EASY,
    INCREMENTAL,
    CONSTRAINT_STREAMS
  }

  private record Step<Score_ extends Score<Score_>>(List<String> choices, Score_ score) {}

  private record Family<Score_ extends Score<Score_>>(
      String name,
      Class<? extends FamilySolution<Score_>> solutionClass,
      Supplier<? extends FamilySolution<Score_>> constructor,
      int levelCount,
      boolean floating,
      Function<long[], Score_> scoreFactory) {

    Score_ score(long[] levels) {
      return scoreFactory.apply(levels);
    }

    Score_ zero() {
      return score(new long[levelCount]);
    }

    Score_ unit(int level, long units) {
      var levels = new long[levelCount];
      levels[level] = units;
      return score(levels);
    }

    FamilySolution<Score_> problem() {
      FamilySolution<Score_> solution = constructor.get();
      solution.family = this;
      var a = new Choice("A");
      var b = new Choice("B");
      solution.choices = List.of(a, b);
      solution.entities =
          new ArrayList<>(List.of(new Assignment("left", a), new Assignment("right", a)));
      return solution;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  @PlanningSolution
  public abstract static class FamilySolution<Score_ extends Score<Score_>> {
    private Family<Score_> family;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "choices")
    public List<Choice> choices;

    @PlanningEntityCollectionProperty public List<Assignment> entities;

    public abstract Score_ getScore();

    public abstract void setScore(Score_ score);
  }

  public static class Choice {
    @PlanningId public String id;

    public Choice() {}

    private Choice(String id) {
      this.id = id;
    }
  }

  @PlanningEntity
  public static class Assignment {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "choices")
    public Choice choice;

    public Assignment() {}

    private Assignment(String id, Choice choice) {
      this.id = id;
      this.choice = choice;
    }
  }

  public static class FamilyEasyCalculator<Score_ extends Score<Score_>>
      implements EasyScoreCalculator<FamilySolution<Score_>, Score_> {
    @Override
    public Score_ calculateScore(FamilySolution<Score_> solution) {
      var ledger = new ImpactLedger<>(solution.family);
      if (solution.entities.stream().anyMatch(entity -> entity.choice == null))
        return ledger.score();
      int bCount =
          (int) solution.entities.stream().filter(entity -> "B".equals(entity.choice.id)).count();
      for (int level = 0; level < solution.family.levelCount(); level++) {
        ledger.add(level, cost(level, solution.family.levelCount(), bCount));
      }
      return ledger.score();
    }
  }

  public static class FamilyIncrementalCalculator<Score_ extends Score<Score_>>
      implements IncrementalScoreCalculator<FamilySolution<Score_>, Score_> {
    private Family<Score_> family;
    private ImpactLedger<Score_> ledger;
    private final List<Score_> impacts = new ArrayList<>();
    private int assigned;
    private int bCount;

    @Override
    public void resetWorkingSolution(FamilySolution<Score_> solution) {
      family = solution.family;
      ledger = new ImpactLedger<>(family);
      impacts.clear();
      assigned = bCount = 0;
      solution.entities.forEach(entity -> update(entity, 1));
      insertPair();
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {
      impacts.forEach(ledger::remove);
      impacts.clear();
      update((Assignment) entity, -1);
    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
      update((Assignment) entity, 1);
      insertPair();
    }

    private void update(Assignment entity, int delta) {
      if (entity.choice != null) {
        assigned += delta;
        if ("B".equals(entity.choice.id)) bCount += delta;
      }
    }

    private void insertPair() {
      if (assigned != 2) return;
      for (int level = 0; level < family.levelCount(); level++) {
        impacts.add(ledger.add(level, cost(level, family.levelCount(), bCount)));
      }
    }

    @Override
    public Score_ calculateScore() {
      return ledger.score();
    }
  }

  /**
   * Floating calculators use the same documented exact active-sum contract as Constraint Streams.
   */
  private static final class ImpactLedger<Score_ extends Score<Score_>> {
    private final Family<Score_> family;
    private final FloatingScoreAccumulator<Score_> floating;
    private Score_ total;

    private ImpactLedger(Family<Score_> family) {
      this.family = family;
      total = family.zero();
      floating = family.floating() ? FloatingScoreAccumulator.create(total) : null;
    }

    private Score_ add(int level, long cost) {
      var weight = family.unit(level, -1L);
      if (floating != null) return floating.addWeighted(weight, cost);
      var impact = weight.multiply(cost);
      total = total.add(impact);
      return impact;
    }

    private void remove(Score_ impact) {
      if (floating != null) floating.subtract(impact);
      else total = total.subtract(impact);
    }

    private Score_ score() {
      return floating == null ? total : floating.extractScore();
    }
  }

  public static class FamilyConstraints implements ConstraintProvider {
    private String familyName;

    public void setFamilyName(String familyName) {
      this.familyName = familyName;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      var family =
          FAMILIES.stream()
              .filter(candidate -> candidate.name().equals(familyName))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown family: " + familyName));
      return constraints(factory, family);
    }

    private static <Score_ extends Score<Score_>> Constraint[] constraints(
        ConstraintFactory factory, Family<Score_> family) {
      var constraints = new Constraint[family.levelCount()];
      for (int level = 0; level < constraints.length; level++) {
        int currentLevel = level;
        constraints[level] =
            factory
                .forEachUniquePair(Assignment.class)
                .penalize(
                    family.unit(level, 1L),
                    (left, right) -> {
                      int bCount =
                          ("B".equals(left.choice.id) ? 1 : 0)
                              + ("B".equals(right.choice.id) ? 1 : 0);
                      return cost(currentLevel, family.levelCount(), bCount);
                    })
                .asConstraint("Landscape level " + level);
      }
      return constraints;
    }
  }

  @PlanningSolution
  public static class SimpleLongSolution extends FamilySolution<SimpleScore> {
    private SimpleScore score;

    @Override
    @PlanningScore
    public SimpleScore getScore() {
      return score;
    }

    @Override
    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class HardSoftLongSolution extends FamilySolution<HardSoftScore> {
    private HardSoftScore score;

    @Override
    @PlanningScore
    public HardSoftScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardSoftScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class HardMediumSoftLongSolution extends FamilySolution<HardMediumSoftScore> {
    private HardMediumSoftScore score;

    @Override
    @PlanningScore
    public HardMediumSoftScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardMediumSoftScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class BendableLongSolution extends FamilySolution<BendableScore> {
    private BendableScore score;

    @Override
    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 2)
    public BendableScore getScore() {
      return score;
    }

    @Override
    public void setScore(BendableScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class SimpleDecimalSolution extends FamilySolution<SimpleBigDecimalScore> {
    private SimpleBigDecimalScore score;

    @Override
    @PlanningScore
    public SimpleBigDecimalScore getScore() {
      return score;
    }

    @Override
    public void setScore(SimpleBigDecimalScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class HardSoftDecimalSolution extends FamilySolution<HardSoftBigDecimalScore> {
    private HardSoftBigDecimalScore score;

    @Override
    @PlanningScore
    public HardSoftBigDecimalScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardSoftBigDecimalScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class HardMediumSoftDecimalSolution
      extends FamilySolution<HardMediumSoftBigDecimalScore> {
    private HardMediumSoftBigDecimalScore score;

    @Override
    @PlanningScore
    public HardMediumSoftBigDecimalScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardMediumSoftBigDecimalScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class BendableDecimalSolution extends FamilySolution<BendableBigDecimalScore> {
    private BendableBigDecimalScore score;

    @Override
    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 2)
    public BendableBigDecimalScore getScore() {
      return score;
    }

    @Override
    public void setScore(BendableBigDecimalScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class SimpleFloatSolution extends FamilySolution<SimpleFloatScore> {
    private SimpleFloatScore score;

    @Override
    @PlanningScore
    public SimpleFloatScore getScore() {
      return score;
    }

    @Override
    public void setScore(SimpleFloatScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class HardSoftFloatSolution extends FamilySolution<HardSoftFloatScore> {
    private HardSoftFloatScore score;

    @Override
    @PlanningScore
    public HardSoftFloatScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardSoftFloatScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class HardMediumSoftFloatSolution extends FamilySolution<HardMediumSoftFloatScore> {
    private HardMediumSoftFloatScore score;

    @Override
    @PlanningScore
    public HardMediumSoftFloatScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardMediumSoftFloatScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class BendableFloatSolution extends FamilySolution<BendableFloatScore> {
    private BendableFloatScore score;

    @Override
    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 2)
    public BendableFloatScore getScore() {
      return score;
    }

    @Override
    public void setScore(BendableFloatScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class SimpleDoubleSolution extends FamilySolution<SimpleDoubleScore> {
    private SimpleDoubleScore score;

    @Override
    @PlanningScore
    public SimpleDoubleScore getScore() {
      return score;
    }

    @Override
    public void setScore(SimpleDoubleScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class HardSoftDoubleSolution extends FamilySolution<HardSoftDoubleScore> {
    private HardSoftDoubleScore score;

    @Override
    @PlanningScore
    public HardSoftDoubleScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardSoftDoubleScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class HardMediumSoftDoubleSolution
      extends FamilySolution<HardMediumSoftDoubleScore> {
    private HardMediumSoftDoubleScore score;

    @Override
    @PlanningScore
    public HardMediumSoftDoubleScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardMediumSoftDoubleScore score) {
      this.score = score;
    }
  }

  @PlanningSolution
  public static class BendableDoubleSolution extends FamilySolution<BendableDoubleScore> {
    private BendableDoubleScore score;

    @Override
    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 2)
    public BendableDoubleScore getScore() {
      return score;
    }

    @Override
    public void setScore(BendableDoubleScore score) {
      this.score = score;
    }
  }
}
