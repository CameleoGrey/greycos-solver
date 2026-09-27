package greycos.solver.core.impl.partitionedsearch.scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementEntity;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementSolution;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class PartitionListIntegrationTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void twoPartitionsInitializeListsAndPreserveShadows(boolean nestedIsland) {
    PhaseConfig child = new ConstructionHeuristicPhaseConfig();
    if (nestedIsland) {
      child = new IslandModelPhaseConfig().withIslandCount(1).withPhaseConfigList(List.of(child));
    }
    var config =
        PartitionChangeMoveTest.listConfig()
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                new PartitionedSearchPhaseConfig()
                    .withSolutionPartitionerClass(TwoListParts.class)
                    .withPhaseConfigList(List.of(child)));
    var result =
        SolverFactory.<TestdataListSolution>create(config)
            .buildSolver()
            .solve(TestdataListSolution.generateUninitializedSolution(4, 2));
    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValueList()).hasSize(2));
    for (var entity : result.getEntityList()) {
      for (int index = 0; index < entity.getValueList().size(); index++) {
        var value = entity.getValueList().get(index);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(index);
      }
    }
    assertThat(result.getScore())
        .isEqualTo(new TestdataListVarEasyScoreCalculator().calculateScore(result));
  }

  @Test
  void mixedPartitionsInitializeListsAndDeclarativeAggregates() {
    var config =
        PlannerTestUtils.buildSolverConfig(
                TestdataMixedListElementSolution.class,
                TestdataMixedListElementEntity.class,
                TestdataMixedListElementValue.class)
            .withEasyScoreCalculatorClass(MixedCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                new PartitionedSearchPhaseConfig()
                    .withSolutionPartitionerClass(TwoMixedParts.class)
                    .withPhaseConfigList(List.of(new ConstructionHeuristicPhaseConfig())));
    var initial = new TestdataMixedListElementSolution();
    initial.setEntities(
        List.of(
            new TestdataMixedListElementEntity("first"),
            new TestdataMixedListElementEntity("last")));
    var firstValue = new TestdataMixedListElementValue("firstValue");
    firstValue.setDuration(1);
    var lastValue = new TestdataMixedListElementValue("lastValue");
    lastValue.setDuration(2);
    initial.setValues(List.of(firstValue, lastValue));
    var result =
        SolverFactory.<TestdataMixedListElementSolution>create(config).buildSolver().solve(initial);
    assertThat(result.getEntities())
        .allSatisfy(
            entity -> {
              assertThat(entity.getValues()).hasSize(1);
              assertThat(entity.getTotalDuration())
                  .isEqualTo(entity.getValues().getFirst().getDuration() + 1);
            });
    assertThat(result.getScore()).isEqualTo(new MixedCalculator().calculateScore(result));
  }

  @Test
  void stepLimitedConstructionPublishesItsPartiallyInitializedAssignments() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withPhases(
                new PartitionedSearchPhaseConfig()
                    .withSolutionPartitionerClass(RawSinglePart.class)
                    .withPhaseConfigList(
                        List.of(
                            new ConstructionHeuristicPhaseConfig()
                                .withTerminationConfig(
                                    new TerminationConfig().withStepCountLimit(1)))));
    var result =
        SolverFactory.<TestdataSolution>create(config)
            .buildSolver()
            .solve(TestdataSolution.generateUninitializedSolution(2, 4));
    assertThat(result.getEntityList().stream().filter(entity -> entity.getValue() != null).count())
        .isEqualTo(1);
  }

  public static class TwoListParts implements SolutionPartitioner<TestdataListSolution> {
    @Override
    public List<TestdataListSolution> splitWorkingSolution(
        ScoreDirector<TestdataListSolution> director, Integer limit) {
      return List.of(
          PartitionOwnershipTest.part(director.getWorkingSolution(), 0),
          PartitionOwnershipTest.part(director.getWorkingSolution(), 1));
    }
  }

  public static class TwoMixedParts
      implements SolutionPartitioner<TestdataMixedListElementSolution> {
    @Override
    public List<TestdataMixedListElementSolution> splitWorkingSolution(
        ScoreDirector<TestdataMixedListElementSolution> director, Integer limit) {
      var initial = director.getWorkingSolution();
      var first = new TestdataMixedListElementSolution();
      first.setEntities(List.of(initial.getEntities().getFirst()));
      first.setValues(List.of(initial.getValues().getFirst()));
      var last = new TestdataMixedListElementSolution();
      last.setEntities(List.of(initial.getEntities().getLast()));
      last.setValues(List.of(initial.getValues().getLast()));
      return List.of(first, last);
    }
  }

  public static class RawSinglePart implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> director, Integer limit) {
      return List.of(director.getWorkingSolution());
    }
  }

  public static class MixedCalculator
      implements EasyScoreCalculator<TestdataMixedListElementSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataMixedListElementSolution solution) {
      return SimpleScore.of(
          solution.getEntities().stream()
              .flatMap(entity -> entity.getValues().stream())
              .mapToInt(value -> value.getDuration() == null ? 0 : value.getDuration() + 1)
              .sum());
    }
  }
}
