package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the factory wiring and real list state, beyond isolated mocked selector tests. */
public class NearbyListSelectionRegressionTest {

  @Test
  void pinnedCappedDestinationsTerminate(@TempDir Path temporaryDirectory) throws Exception {
    // A regression used to loop inside iterator.hasNext(), ignoring both interruption and solver
    // termination. A disposable JVM makes the regression test itself reliably terminable.
    Path output = temporaryDirectory.resolve("pinned-nearby.log");
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                getClass().getName(),
                "pinned")
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(process.waitFor(30, TimeUnit.SECONDS))
          .as("Pinned nearby selection must terminate; child output: %s", Files.readString(output))
          .isTrue();
      assertThat(process.exitValue()).as(Files.readString(output)).isZero();
    } finally {
      process.destroyForcibly();
      process.waitFor(10, TimeUnit.SECONDS);
    }
  }

  public static void main(String[] args) {
    var p0 = new TestdataPinnedWithIndexListValue("p0");
    var p1 = new TestdataPinnedWithIndexListValue("p1");
    var x = new TestdataPinnedWithIndexListValue("x");
    var y = new TestdataPinnedWithIndexListValue("y");
    var entity = TestdataPinnedWithIndexListEntity.createWithValues("entity", p0, p1, x, y);
    entity.setPinIndex(2);
    var solution = new TestdataPinnedWithIndexListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(p0, p1, x, y));
    var move = listChange(PinnedDistanceMeter.class);
    move.getValueSelectorConfig().setFilterClass(OnlyYFilter.class);
    var config =
        solverConfig(
            solution.getClass(), entity.getClass(), x.getClass(), PreferYFirstScore.class, move);
    var result =
        SolverFactory.<TestdataPinnedWithIndexListSolution>create(config)
            .buildSolver()
            .solve(solution);
    assertThat(result.getEntityList().getFirst().getValueList())
        .extracting(TestdataPinnedWithIndexListValue::getCode)
        .containsExactly("p0", "p1", "y", "x");
  }

  @Test
  void unassignedValuesAreFilteredAndAnUnassignmentMoveRemainsAvailable() {
    var assigned = new TestdataAllowsUnassignedValuesListValue("a");
    var unassigned = new TestdataAllowsUnassignedValuesListValue("u");
    var entity = new TestdataAllowsUnassignedValuesListEntity("entity", assigned);
    var solution = new TestdataAllowsUnassignedValuesListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(assigned, unassigned));
    var config =
        solverConfig(
            solution.getClass(),
            entity.getClass(),
            assigned.getClass(),
            TestdataAllowsUnassignedValuesListEasyScoreCalculator.class,
            listChange(UnassignedDistanceMeter.class));
    var result =
        SolverFactory.<TestdataAllowsUnassignedValuesListSolution>create(config)
            .buildSolver()
            .solve(solution);
    assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(result.getEntityList().getFirst().getValueList()).isEmpty();
  }

  @Test
  void subListChangesAcceptTheDocumentedTypedValueDistanceMeter() {
    var solution = TestdataListSolution.generateInitializedSolution(4, 1);
    var move =
        new SubListChangeMoveSelectorConfig()
            .withSubListSelectorConfig(new SubListSelectorConfig().withId("origin"))
            .withDestinationSelectorConfig(
                new DestinationSelectorConfig()
                    .withNearbySelectionConfig(
                        nearby(TypedValueDistanceMeter.class)
                            .withOriginSubListSelectorConfig(
                                new SubListSelectorConfig().withMimicSelectorRef("origin"))));
    var config =
        solverConfig(
            solution.getClass(),
            TestdataListEntity.class,
            TestdataListValue.class,
            ZeroScore.class,
            move);
    var result = SolverFactory.<TestdataListSolution>create(config).buildSolver().solve(solution);
    assertThat(result.getEntityList().getFirst().getValueList()).hasSize(4);
  }

  @Test
  void nearbySubListSwapsAcceptPinFilteredDestinationSizes() {
    var pinned = new TestdataPinnedWithIndexListValue("p0");
    var x = new TestdataPinnedWithIndexListValue("x");
    var y = new TestdataPinnedWithIndexListValue("y");
    var entity = TestdataPinnedWithIndexListEntity.createWithValues("entity", pinned, x, y);
    entity.setPinIndex(1);
    var solution = new TestdataPinnedWithIndexListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(pinned, x, y));
    var move =
        new SubListSwapMoveSelectorConfig()
            .withSubListSelectorConfig(
                new SubListSelectorConfig().withId("origin").withMinimumSubListSize(1))
            .withSecondarySubListSelectorConfig(
                new SubListSelectorConfig()
                    .withMinimumSubListSize(1)
                    .withNearbySelectionConfig(
                        nearby(PinnedDistanceMeter.class)
                            .withOriginSubListSelectorConfig(
                                new SubListSelectorConfig().withMimicSelectorRef("origin"))));
    var config =
        solverConfig(solution.getClass(), entity.getClass(), x.getClass(), ZeroScore.class, move);
    var result =
        SolverFactory.<TestdataPinnedWithIndexListSolution>create(config)
            .buildSolver()
            .solve(solution);
    assertThat(result.getEntityList().getFirst().getValueList().getFirst().getCode())
        .isEqualTo("p0");
  }

  private static NearbySelectionConfig nearby(Class<? extends NearbyDistanceMeter> meterClass) {
    return new NearbySelectionConfig()
        .withNearbyDistanceMeterClass(meterClass)
        .withNearbySelectionDistributionType(NearbySelectionDistributionType.BLOCK_DISTRIBUTION)
        .withBlockDistributionSizeMinimum(1)
        .withBlockDistributionSizeMaximum(1)
        .withBlockDistributionUniformDistributionProbability(0.0);
  }

  private static ListChangeMoveSelectorConfig listChange(
      Class<? extends NearbyDistanceMeter> meterClass) {
    return new ListChangeMoveSelectorConfig()
        .withValueSelectorConfig(new ValueSelectorConfig().withId("origin"))
        .withDestinationSelectorConfig(
            new DestinationSelectorConfig()
                .withNearbySelectionConfig(
                    nearby(meterClass)
                        .withOriginValueSelectorConfig(
                            new ValueSelectorConfig().withMimicSelectorRef("origin"))));
  }

  private static SolverConfig solverConfig(
      Class<?> solutionClass,
      Class<?> entityClass,
      Class<?> valueClass,
      Class<? extends EasyScoreCalculator> scoreClass,
      MoveSelectorConfig<?> move) {
    move.setSelectedCountLimit(100L);
    return new SolverConfig()
        .withSolutionClass(solutionClass)
        .withEntityClasses(entityClass, valueClass)
        .withRandomSeed(0L)
        .withScoreDirectorFactory(
            new ScoreDirectorFactoryConfig().withEasyScoreCalculatorClass(scoreClass))
        .withPhases(
            new LocalSearchPhaseConfig()
                .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                .withMoveSelectorConfig(move))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
  }

  public static final class PinnedDistanceMeter
      implements NearbyDistanceMeter<TestdataPinnedWithIndexListValue, Object> {
    @Override
    public double getNearbyDistance(TestdataPinnedWithIndexListValue origin, Object destination) {
      if (destination instanceof TestdataPinnedWithIndexListEntity) {
        return 1;
      }
      return ((TestdataPinnedWithIndexListValue) destination).getCode().equals("p0") ? 0 : 100;
    }
  }

  public static final class UnassignedDistanceMeter
      implements NearbyDistanceMeter<TestdataAllowsUnassignedValuesListValue, Object> {
    @Override
    public double getNearbyDistance(
        TestdataAllowsUnassignedValuesListValue origin, Object destination) {
      return destination instanceof TestdataAllowsUnassignedValuesListValue value
              && value.getCode().equals("u")
          ? 0
          : 100;
    }
  }

  public static final class TypedValueDistanceMeter
      implements NearbyDistanceMeter<TestdataListValue, Object> {
    @Override
    public double getNearbyDistance(TestdataListValue origin, Object destination) {
      return origin == destination ? 0 : 1;
    }
  }

  public static final class OnlyYFilter
      implements SelectionFilter<TestdataPinnedWithIndexListSolution, Object> {
    @Override
    public boolean accept(
        ScoreDirector<TestdataPinnedWithIndexListSolution> scoreDirector, Object value) {
      return ((TestdataPinnedWithIndexListValue) value).getCode().equals("y");
    }
  }

  public static final class PreferYFirstScore
      implements EasyScoreCalculator<TestdataPinnedWithIndexListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataPinnedWithIndexListSolution solution) {
      return SimpleScore.of(
          solution.getEntityList().getFirst().getValueList().get(2).getCode().equals("y") ? 1 : 0);
    }
  }

  public static final class ZeroScore implements EasyScoreCalculator<Object, SimpleScore> {
    @Override
    public SimpleScore calculateScore(Object solution) {
      return SimpleScore.ZERO;
    }
  }
}
