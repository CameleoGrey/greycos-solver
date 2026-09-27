package greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class NearbySwapEmptyOriginTest {

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"ORIGINAL", "RANDOM"})
  void isolatedOriginsDoNotHideAnImprovingSwap(SelectionOrder sourceOrder) {
    var solution = isolatedSolution();
    var x = new TestdataValue("x");
    var y = new TestdataValue("y");
    solution.getEntityList().add(new TestdataEntityProvidingEntity("target", List.of(x, y), x));
    solution.getEntityList().add(new TestdataEntityProvidingEntity("other", List.of(x, y), y));

    var result = solve(solution, sourceOrder);

    assertThat(result.getScore()).isEqualTo(SimpleScore.ONE);
    assertThat(result.getEntityList().get(0).getValue().getCode()).isEqualTo("isolated0");
    assertThat(result.getEntityList().get(1).getValue().getCode()).isEqualTo("isolated1");
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"ORIGINAL", "RANDOM"})
  void allOriginsIneligibleTerminateThroughFactoryFiltering(SelectionOrder sourceOrder) {
    // Only a step limit is configured. Infinite non-doable retries would never reach this limit.
    var result = solve(isolatedSolution(), sourceOrder);
    assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(result.getEntityList())
        .extracting(entity -> entity.getValue().getCode())
        .containsExactly("isolated0", "isolated1");
  }

  @Test
  void globallyEmptySecondaryTerminates() {
    var solution = isolatedSolution();
    solution.getEntityList().removeLast();
    var result = solve(solution, SelectionOrder.RANDOM);
    assertThat(result.getEntityList()).hasSize(1);
    assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
  }

  private static TestdataEntityProvidingSolution isolatedSolution() {
    var solution = new TestdataEntityProvidingSolution();
    var entities = new ArrayList<TestdataEntityProvidingEntity>();
    for (int i = 0; i < 2; i++) {
      var value = new TestdataValue("isolated" + i);
      entities.add(new TestdataEntityProvidingEntity(value.getCode(), List.of(value), value));
    }
    solution.setEntityList(entities);
    return solution;
  }

  private static TestdataEntityProvidingSolution solve(
      TestdataEntityProvidingSolution solution, SelectionOrder sourceOrder) {
    var move =
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig().withId("origin").withSelectionOrder(sourceOrder))
            .withSecondaryEntitySelectorConfig(
                new EntitySelectorConfig()
                    .withNearbySelectionConfig(
                        new NearbySelectionConfig()
                            .withOriginEntitySelectorConfig(
                                new EntitySelectorConfig().withMimicSelectorRef("origin"))
                            .withNearbyDistanceMeterClass(DistanceMeter.class)
                            .withMaxNearbySortSize(1)));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataEntityProvidingSolution.class)
            .withEntityClasses(TestdataEntityProvidingEntity.class)
            .withEasyScoreCalculatorClass(ScoreCalculator.class)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(move)
                    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)));
    return SolverFactory.<TestdataEntityProvidingSolution>create(config)
        .buildSolver()
        .solve(solution);
  }

  public static class DistanceMeter
      implements NearbyDistanceMeter<TestdataEntityProvidingEntity, TestdataEntityProvidingEntity> {
    @Override
    public double getNearbyDistance(
        TestdataEntityProvidingEntity origin, TestdataEntityProvidingEntity destination) {
      return 0;
    }
  }

  public static class ScoreCalculator
      implements EasyScoreCalculator<TestdataEntityProvidingSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataEntityProvidingSolution solution) {
      return SimpleScore.of(
          solution.getEntityList().stream()
                  .anyMatch(
                      entity ->
                          entity.getCode().equals("target")
                              && entity.getValue().getCode().equals("y"))
              ? 1
              : 0);
    }
  }
}
