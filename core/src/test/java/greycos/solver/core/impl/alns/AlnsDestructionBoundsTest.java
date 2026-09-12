package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlnsDestructionBoundsTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void explicitMaximumCountLowersDefaultMinimum(boolean list) {
    for (int maximum = 1; maximum <= 4; maximum++) {
      assertThat(
              destroyedCounts(
                  list, 10, new AlnsDestroyOperatorConfig().withMaximumDestroyedCount(maximum)))
          .containsOnly(maximum);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void explicitMaximumPercentageLowersDefaultMinimum(boolean list) {
    for (int maximum = 1; maximum <= 4; maximum++) {
      assertThat(
              destroyedCounts(
                  list,
                  10,
                  new AlnsDestroyOperatorConfig()
                      .withMaximumDestroyedPercentage((maximum + 0.5) / 10)))
          .containsOnly(maximum);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void subTargetPercentageRetainsOneTargetMinimum(boolean list) {
    assertThat(
            destroyedCounts(
                list, 10, new AlnsDestroyOperatorConfig().withMaximumDestroyedPercentage(0.01)))
        .containsOnly(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void explicitMinimumCanRaiseUnspecifiedMaximum(boolean list) {
    int minimum = list ? 45 : 25;
    assertThat(
            destroyedCounts(
                list, 50, new AlnsDestroyOperatorConfig().withMinimumDestroyedCount(minimum)))
        .containsOnly(minimum);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void boundsAreClampedToEligiblePopulation(boolean list) {
    assertThat(destroyedCounts(list, 3, new AlnsDestroyOperatorConfig())).containsOnly(3);
    assertThat(
            destroyedCounts(
                list,
                3,
                new AlnsDestroyOperatorConfig()
                    .withMinimumDestroyedCount(10)
                    .withMaximumDestroyedCount(20)))
        .containsOnly(3);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void defaultBoundsRemainWithinDocumentedRange(boolean list) {
    assertThat(destroyedCounts(list, 50, new AlnsDestroyOperatorConfig()))
        .allSatisfy(count -> assertThat(count).isBetween(5, list ? 40 : 20));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void incompatibleExplicitMixedBoundsFailClearly(boolean list) {
    for (var config :
        List.of(
            new AlnsDestroyOperatorConfig()
                .withMinimumDestroyedCount(5)
                .withMaximumDestroyedPercentage(0.2),
            new AlnsDestroyOperatorConfig()
                .withMinimumDestroyedPercentage(0.5)
                .withMaximumDestroyedCount(2))) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> destroyedCounts(list, 10, config.withId("bounded")))
          .withMessageContaining("bounded")
          .withMessageContaining("5..2")
          .withMessageContaining("10 eligible targets");
    }
  }

  private static List<Integer> destroyedCounts(
      boolean list, int population, AlnsDestroyOperatorConfig destroy) {
    var config =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                new AlnsPhaseConfig()
                    .withDestroyOperators(destroy)
                    .withRepairOperators(
                        new AlnsRepairOperatorConfig().withCustomClass(FailedRepair.class))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(8)));
    if (list) {
      return solve(
          config
              .withSolutionClass(TestdataListSolution.class)
              .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
              .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class),
          TestdataListSolution.generateInitializedSolution(population, 2));
    }
    return solve(
        config
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class),
        TestdataSolution.generateSolution(2, population));
  }

  private static <Solution_> List<Integer> solve(SolverConfig config, Solution_ problem) {
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    var counts = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> stepScope) {
            var step = (AlnsStepScope<Solution_>) stepScope;
            counts.add(step.getDestroyedCount());
            assertThat(step.getScore().isFullyAssigned()).isTrue();
          }
        });
    solver.solve(problem);
    assertThat(counts).hasSize(8);
    return counts;
  }

  public static final class FailedRepair implements AlnsRepairOperator<Object, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<Object, SimpleScore> context, List<AlnsTarget<Object>> pendingTargets) {
      return false;
    }
  }
}
