package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.score.TestdataHardSoftBigDecimalScoreSolution;
import greycos.solver.core.testcotwin.score.TestdataHardSoftScoreSolution;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class IteratedLocalSearchNativeScoreTest {
  private static final long ABOVE_DOUBLE_INTEGER_PRECISION = 9_007_199_254_740_992L;
  private static final BigDecimal DECIMAL_BASE = new BigDecimal("0.100000000000000000000000000000");
  private static final BigDecimal DECIMAL_BETTER =
      new BigDecimal("0.100000000000000000000000000001");

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void nativeIntegerLevelsPreserveUnitImprovementBeyondDoublePrecision(String threads) {
    var result =
        SolverFactory.<TestdataHardSoftScoreSolution>create(
                config(TestdataHardSoftScoreSolution.class, threads)
                    .withEasyScoreCalculatorClass(IntegralCalculator.class))
            .buildSolver()
            .solve(integralProblem());
    assertIntegralBest(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void customIncrementalCalculatorReceivesBalancedNotificationsAcrossRestorations(String threads) {
    var result =
        SolverFactory.<TestdataHardSoftScoreSolution>create(
                config(TestdataHardSoftScoreSolution.class, threads)
                    .withScoreDirectorFactory(
                        new ScoreDirectorFactoryConfig()
                            .withIncrementalScoreCalculatorClass(
                                IntegralIncrementalCalculator.class)))
            .buildSolver()
            .solve(integralProblem());
    assertIntegralBest(result);
  }

  private static TestdataHardSoftScoreSolution integralProblem() {
    var problem = new TestdataHardSoftScoreSolution("integral");
    problem.setValueList(values());
    problem.setEntityList(List.of(new TestdataEntity("entity", problem.getValueList().getFirst())));
    return problem;
  }

  private static void assertIntegralBest(TestdataHardSoftScoreSolution result) {
    assertThat(result.getEntityList().getFirst().getValue()).isSameAs(result.getValueList().get(1));
    assertThat(result.getScore())
        .isEqualTo(HardSoftScore.of(0, ABOVE_DOUBLE_INTEGER_PRECISION + 1));
    assertThat(new IntegralCalculator().calculateScore(result)).isEqualTo(result.getScore());
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void nativeDecimalOrderingPreservesSubDoubleSoftImprovementAndHardPriority(String threads) {
    var problem = new TestdataHardSoftBigDecimalScoreSolution("decimal");
    problem.setValueList(values());
    problem.setEntityList(List.of(new TestdataEntity("entity", problem.getValueList().getFirst())));
    var result =
        SolverFactory.<TestdataHardSoftBigDecimalScoreSolution>create(
                config(TestdataHardSoftBigDecimalScoreSolution.class, threads)
                    .withEasyScoreCalculatorClass(DecimalCalculator.class))
            .buildSolver()
            .solve(problem);
    assertThat(result.getEntityList().getFirst().getValue()).isSameAs(result.getValueList().get(1));
    assertThat(result.getScore())
        .isEqualTo(HardSoftBigDecimalScore.of(BigDecimal.ZERO, DECIMAL_BETTER));
    assertThat(new DecimalCalculator().calculateScore(result)).isEqualTo(result.getScore());
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void nativeFloatingLevelsRetainAdjacentRepresentableSoftScores(String threads) {
    var problem = new DoubleSolution();
    problem.values = values();
    problem.entities = List.of(new TestdataEntity("entity", problem.values.getFirst()));
    var result =
        SolverFactory.<DoubleSolution>create(
                config(DoubleSolution.class, threads)
                    .withEasyScoreCalculatorClass(DoubleCalculator.class))
            .buildSolver()
            .solve(problem);
    assertThat(result.entities.getFirst().getValue()).isSameAs(result.values.get(1));
    assertThat(result.score).isEqualTo(HardSoftDoubleScore.of(0.0, Math.nextUp(1.0)));
    assertThat(new DoubleCalculator().calculateScore(result)).isEqualTo(result.score);
  }

  private static List<TestdataValue> values() {
    return List.of(new TestdataValue("0"), new TestdataValue("1"), new TestdataValue("2"));
  }

  private static SolverConfig config(Class<?> solutionClass, String threads) {
    return new SolverConfig()
        .withSolutionClass(solutionClass)
        .withEntityClasses(TestdataEntity.class)
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withRandomSeed(0L)
        .withMoveThreadCount(threads)
        .withPhases(
            new IteratedLocalSearchPhaseConfig()
                .withLocalSearch(
                    new LocalSearchPhaseConfig()
                        .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                        .withMoveSelectorConfig(selector())
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(3)))
                .withPerturbationMoveSelectorConfig(selector())
                .withPerturbationStrengths(1)
                .withPerturbationAttemptLimit(4)
                .withEpisodeCandidateAttemptLimit(12)
                .withIterationCountLimit(3));
  }

  private static MoveIteratorFactoryConfig selector() {
    return new MoveIteratorFactoryConfig()
        .withMoveIteratorFactoryClass(AllValuesMoves.class)
        .withSelectionOrder(SelectionOrder.ORIGINAL);
  }

  private static HardSoftScore integralScore(TestdataEntity entity) {
    return switch (entity.getValue().getCode()) {
      case "0" -> HardSoftScore.of(0, ABOVE_DOUBLE_INTEGER_PRECISION);
      case "1" -> HardSoftScore.of(0, ABOVE_DOUBLE_INTEGER_PRECISION + 1);
      case "2" -> HardSoftScore.of(-1, Long.MAX_VALUE);
      default -> throw new IllegalStateException();
    };
  }

  public static final class IntegralCalculator
      implements EasyScoreCalculator<TestdataHardSoftScoreSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(TestdataHardSoftScoreSolution solution) {
      return integralScore(solution.getEntityList().getFirst());
    }
  }

  public static final class DecimalCalculator
      implements EasyScoreCalculator<
          TestdataHardSoftBigDecimalScoreSolution, HardSoftBigDecimalScore> {
    @Override
    public HardSoftBigDecimalScore calculateScore(
        TestdataHardSoftBigDecimalScoreSolution solution) {
      return switch (solution.getEntityList().getFirst().getValue().getCode()) {
        case "0" -> HardSoftBigDecimalScore.of(BigDecimal.ZERO, DECIMAL_BASE);
        case "1" -> HardSoftBigDecimalScore.of(BigDecimal.ZERO, DECIMAL_BETTER);
        case "2" ->
            HardSoftBigDecimalScore.of(
                new BigDecimal("-0.000000000000000000000000000001"), new BigDecimal("1E100"));
        default -> throw new IllegalStateException();
      };
    }
  }

  @PlanningSolution
  public static final class DoubleSolution {
    @ValueRangeProvider(id = "valueRange")
    @ProblemFactCollectionProperty
    private List<TestdataValue> values;

    @PlanningEntityCollectionProperty private List<TestdataEntity> entities;
    @PlanningScore private HardSoftDoubleScore score;

    public DoubleSolution() {}

    public List<TestdataValue> getValues() {
      return values;
    }

    public List<TestdataEntity> getEntities() {
      return entities;
    }

    public HardSoftDoubleScore getScore() {
      return score;
    }

    public void setScore(HardSoftDoubleScore score) {
      this.score = score;
    }
  }

  public static final class DoubleCalculator
      implements EasyScoreCalculator<DoubleSolution, HardSoftDoubleScore> {
    @Override
    public HardSoftDoubleScore calculateScore(DoubleSolution solution) {
      return switch (solution.entities.getFirst().getValue().getCode()) {
        case "0" -> HardSoftDoubleScore.of(0.0, 1.0);
        case "1" -> HardSoftDoubleScore.of(0.0, Math.nextUp(1.0));
        case "2" -> HardSoftDoubleScore.of(-Double.MIN_VALUE, Double.MAX_VALUE);
        default -> throw new IllegalStateException();
      };
    }
  }

  public static final class IntegralIncrementalCalculator
      implements IncrementalScoreCalculator<TestdataHardSoftScoreSolution, HardSoftScore> {
    private TestdataHardSoftScoreSolution solution;
    private HardSoftScore score;
    private boolean changing;

    @Override
    public void resetWorkingSolution(TestdataHardSoftScoreSolution workingSolution) {
      solution = workingSolution;
      score = integralScore(workingSolution.getEntityList().getFirst());
      changing = false;
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {
      assertThat(changing).isFalse();
      assertThat(variableName).isEqualTo("value");
      assertThat(entity).isSameAs(solution.getEntityList().getFirst());
      score = score.subtract(integralScore((TestdataEntity) entity));
      changing = true;
    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
      assertThat(changing).isTrue();
      score = score.add(integralScore((TestdataEntity) entity));
      changing = false;
    }

    @Override
    public HardSoftScore calculateScore() {
      assertThat(changing).isFalse();
      assertThat(score).isEqualTo(integralScore(solution.getEntityList().getFirst()));
      return score;
    }
  }

  public static final class AllValuesMoves implements MoveIteratorFactory<Object, Move<Object>> {
    @Override
    public long getSize(ScoreDirector<Object> director) {
      return 3;
    }

    @Override
    public Iterator<Move<Object>> createOriginalMoveIterator(ScoreDirector<Object> director) {
      var solution = director.getWorkingSolution();
      var entities =
          switch (solution) {
            case TestdataHardSoftScoreSolution integral -> integral.getEntityList();
            case TestdataHardSoftBigDecimalScoreSolution decimal -> decimal.getEntityList();
            case DoubleSolution floating -> floating.entities;
            default -> throw new IllegalArgumentException();
          };
      var values =
          switch (solution) {
            case TestdataHardSoftScoreSolution integral -> integral.getValueList();
            case TestdataHardSoftBigDecimalScoreSolution decimal -> decimal.getValueList();
            case DoubleSolution floating -> floating.values;
            default -> throw new IllegalArgumentException();
          };
      var descriptor =
          ((InnerScoreDirector<Object, ?>) director)
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataEntity.class)
              .getGenuineVariableDescriptor("value");
      return values.stream()
          .<Move<Object>>map(value -> new ChangeMove<>(descriptor, entities.getFirst(), value))
          .iterator();
    }

    @Override
    public Iterator<Move<Object>> createRandomMoveIterator(
        ScoreDirector<Object> director, RandomGenerator random) {
      return createOriginalMoveIterator(director);
    }
  }
}
