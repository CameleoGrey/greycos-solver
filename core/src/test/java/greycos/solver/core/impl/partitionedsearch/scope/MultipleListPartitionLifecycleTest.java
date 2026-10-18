package greycos.solver.core.impl.partitionedsearch.scope;

import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.assertState;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.config;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.rearrange;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.recompute;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.scope;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultipleListPartitionLifecycleTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void customPartitionsInitializeBothListsAndBasicVariables(boolean nestedIsland) {
    List<PhaseConfig<?>> children =
        List.of(
            basicConstruction(TestdataMultipleListEntity.class),
            listConstruction("firstValueList"),
            listConstruction("secondValueList"),
            basicConstruction(TestdataMultipleListSecondValue.class));
    if (nestedIsland) {
      children =
          List.of(new IslandModelPhaseConfig().withIslandCount(1).withPhaseConfigList(children));
    }
    var solver =
        SolverFactory.<TestdataMultipleListSolution>create(
                config()
                    .withPhases(
                        new PartitionedSearchPhaseConfig()
                            .withSolutionPartitionerClass(TwoParts.class)
                            .withPhaseConfigList(new ArrayList<PhaseConfig>(children))))
            .buildSolver();
    var result = solver.solve(TestdataMultipleListSolution.generateUninitializedSolution(4, 6, 2));
    assertState(result);
    assertThat(result.getScore()).isEqualTo(recompute(result));
    for (int index = 0; index < 2; index++) {
      var owner = result.getEntityList().get(index);
      var ownerIndex = index;
      assertThat(owner.getFirstValueList())
          .hasSize(2)
          .allSatisfy(value -> assertThat(partIndex(value)).isEqualTo(ownerIndex));
      assertThat(owner.getSecondValueList())
          .hasSize(3)
          .allSatisfy(value -> assertThat(partIndex(value)).isEqualTo(ownerIndex));
    }
  }

  @Test
  void omittedChildPhasesInitializeEveryListAndBasicVariable() {
    var solver =
        SolverFactory.<TestdataMultipleListSolution>create(
                config()
                    .withPhases(
                        new PartitionedSearchPhaseConfig()
                            .withSolutionPartitionerClass(TwoParts.class)
                            .withTerminationConfig(new TerminationConfig().withStepCountLimit(12))))
            .buildSolver();
    var result = solver.solve(TestdataMultipleListSolution.generateUninitializedSolution(4, 6, 2));
    assertState(result);
    assertThat(result.getScore()).isEqualTo(recompute(result));
    for (int index = 0; index < 2; index++) {
      var owner = result.getEntityList().get(index);
      var ownerIndex = index;
      assertThat(owner.getFirstValueList())
          .hasSize(2)
          .allSatisfy(value -> assertThat(partIndex(value)).isEqualTo(ownerIndex));
      assertThat(owner.getSecondValueList())
          .hasSize(3)
          .allSatisfy(value -> assertThat(partIndex(value)).isEqualTo(ownerIndex));
    }
  }

  @Test
  void publicationRebasesEachDescriptorAndUndoPreservesTheOtherPartition() {
    var parentScope = scope();
    var childScope = scope();
    parentScope.setInitialSolution(
        TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2));
    childScope.setInitialSolution(
        part(TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2), 0));
    try (var parent = parentScope.<SimpleScore>getScoreDirector();
        var child = childScope.<SimpleScore>getScoreDirector()) {
      var beforeScore = parent.calculateScore();
      child.calculateScore();
      var before = snapshot(parent.getWorkingSolution());
      var untouchedPart = snapshot(part(parent.getWorkingSolution(), 1));
      var ownership =
          PartitionOwnership.validate(
              parent,
              List.of(part(parent.getWorkingSolution(), 0), part(parent.getWorkingSolution(), 1)));
      var target = child.cloneWorkingSolution();
      rearrange(target, 1);
      var move = PartitionChangeMove.createMove(child, target, 0).rebase(parent);
      for (var variable : move.getAssignments().getListChanges().keySet()) {
        assertThat(parent.getSolutionDescriptor().getListVariableDescriptorList())
            .anyMatch(candidate -> candidate == variable);
        assertThat(child.getSolutionDescriptor().getListVariableDescriptorList())
            .noneMatch(candidate -> candidate == variable);
      }
      ownership.validateMove(move, parent);
      var undo =
          parent
              .getMoveDirector()
              .executeTemporaryProducingUndoMove(
                  move,
                  score -> {
                    assertState(parent.getWorkingSolution());
                    assertThat(snapshot(part(parent.getWorkingSolution(), 0)))
                        .isEqualTo(snapshot(target));
                    assertThat(snapshot(part(parent.getWorkingSolution(), 1)))
                        .isEqualTo(untouchedPart);
                    assertThat(score.raw()).isEqualTo(recompute(parent.getWorkingSolution()));
                  });
      assertThat(snapshot(parent.getWorkingSolution())).isEqualTo(before);
      assertThat(parent.calculateScore()).isEqualTo(beforeScore);
      parent.executeMove(move);
      assertState(parent.getWorkingSolution());
      parent.executeMove(undo);
      assertState(parent.getWorkingSolution());
      assertThat(snapshot(parent.getWorkingSolution())).isEqualTo(before);
      assertThat(parent.calculateScore()).isEqualTo(beforeScore);
    }
  }

  @Test
  void secondListCannotPublishAnotherPartitionsValues() {
    var scope = scope();
    scope.setInitialSolution(TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var beforeScore = director.calculateScore();
      var before = snapshot(director.getWorkingSolution());
      var ownership =
          PartitionOwnership.validate(
              director,
              List.of(
                  part(director.getWorkingSolution(), 0), part(director.getWorkingSolution(), 1)));
      var source = director.cloneWorkingSolution();
      var foreignValue = source.getSecondValueList().get(1);
      var publication = part(source, 0);
      rearrange(publication, 1);
      publication.getEntityList().getFirst().getSecondValueList().set(0, foreignValue);
      var move = PartitionChangeMove.createMove(director, publication, 0).rebase(director);
      assertThatThrownBy(() -> ownership.validateMove(move, director))
          .hasMessageContaining("secondValueList")
          .hasMessageContaining("outside the range");
      assertState(director.getWorkingSolution());
      assertThat(snapshot(director.getWorkingSolution())).isEqualTo(before);
      assertThat(director.calculateScore()).isEqualTo(beforeScore);
    }
  }

  private static ConstructionHeuristicPhaseConfig basicConstruction(Class<?> entityClass) {
    return new ConstructionHeuristicPhaseConfig()
        .withEntityPlacerConfig(
            new QueuedEntityPlacerConfig()
                .withEntitySelectorConfig(new EntitySelectorConfig(entityClass)));
  }

  private static ConstructionHeuristicPhaseConfig listConstruction(String variableName) {
    return new ConstructionHeuristicPhaseConfig()
        .withEntityPlacerConfig(
            new QueuedValuePlacerConfig()
                .withEntityClass(TestdataMultipleListEntity.class)
                .withValueSelectorConfig(new ValueSelectorConfig(variableName).withId(variableName))
                .withMoveSelectorConfig(
                    new ListChangeMoveSelectorConfig()
                        .withValueSelectorConfig(
                            new ValueSelectorConfig(variableName)
                                .withMimicSelectorRef(variableName))
                        .withDestinationSelectorConfig(
                            new DestinationSelectorConfig()
                                .withEntitySelectorConfig(
                                    new EntitySelectorConfig(TestdataMultipleListEntity.class))
                                .withValueSelectorConfig(new ValueSelectorConfig(variableName)))));
  }

  private static int partIndex(TestdataObject value) {
    return Integer.parseInt(value.getCode().substring(value.getCode().lastIndexOf('-') + 1)) % 2;
  }

  private static TestdataMultipleListSolution part(
      TestdataMultipleListSolution solution, int index) {
    var part = new TestdataMultipleListSolution();
    part.setEntityList(List.of(solution.getEntityList().get(index)));
    part.setFirstValueList(
        new ArrayList<>(
            solution.getFirstValueList().stream()
                .filter(value -> partIndex(value) == index)
                .toList()));
    part.setSecondValueList(
        new ArrayList<>(
            solution.getSecondValueList().stream()
                .filter(value -> partIndex(value) == index)
                .toList()));
    part.setBasicValueRange(solution.getBasicValueRange());
    return part;
  }

  public static final class TwoParts implements SolutionPartitioner<TestdataMultipleListSolution> {
    @Override
    public List<TestdataMultipleListSolution> splitWorkingSolution(
        ScoreDirector<TestdataMultipleListSolution> director, Integer limit) {
      return List.of(
          part(director.getWorkingSolution(), 0), part(director.getWorkingSolution(), 1));
    }
  }
}
