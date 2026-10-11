package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;

import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class SolutionAssignmentsMultipleListTest {

  @Test
  void rebaseUsesDestinationDescriptorsForBothListsAndBasicVariables() {
    var source = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    var destination = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    source.getEntityList().getFirst().setBasicValue(3);
    Collections.reverse(source.getEntityList().getFirst().getFirstValueList());
    Collections.reverse(source.getEntityList().getFirst().getSecondValueList());
    try (var sourceDirector = TestdataMultipleListSolution.buildScoreDirector();
        var destinationDirector = TestdataMultipleListSolution.buildScoreDirector()) {
      sourceDirector.setWorkingSolution(source);
      destinationDirector.setWorkingSolution(destination);
      var assignments =
          SolutionAssignments.capture(sourceDirector.getSolutionDescriptor(), source)
              .rebase(destinationDirector);
      var entityDescriptor =
          destinationDirector
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataMultipleListEntity.class);
      assertThat(assignments.getListChanges().keySet())
          .containsExactlyInAnyOrderElementsOf(entityDescriptor.getListVariableDescriptorList());
      assignments
          .getBasicChanges()
          .keySet()
          .forEach(
              descriptor ->
                  assertThat(descriptor)
                      .isSameAs(
                          destinationDirector
                              .getSolutionDescriptor()
                              .findEntityDescriptorOrFail(
                                  descriptor.getEntityDescriptor().getEntityClass())
                              .getVariableDescriptor(descriptor.getVariableName())));
      var firstListIdentity = destination.getEntityList().getFirst().getFirstValueList();
      var secondListIdentity = destination.getEntityList().getFirst().getSecondValueList();
      assignments.apply(destinationDirector);
      destinationDirector.updateShadowVariables();
      assertThat(destination.getEntityList().getFirst().getFirstValueList())
          .isSameAs(firstListIdentity)
          .containsExactly(
              destination.getFirstValueList().get(2), destination.getFirstValueList().get(0));
      assertThat(destination.getEntityList().getFirst().getSecondValueList())
          .isSameAs(secondListIdentity)
          .containsExactly(
              destination.getSecondValueList().get(2), destination.getSecondValueList().get(0));
      assertThat(destination.getFirstValueList().get(2).getIndex()).isZero();
      assertThat(destination.getSecondValueList().get(2).getIndex()).isZero();
      assertThat(destination.getEntityList().getFirst().getBasicValue()).isEqualTo(3);
      assertThat(destinationDirector.calculateScore()).isEqualTo(sourceDirector.calculateScore());
    }
  }

  @Test
  void laterFieldPinMismatchRejectsBeforeAnyAssignmentChanges() {
    var source = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    var destination = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    source.getEntityList().getFirst().setBasicValue(3);
    Collections.reverse(source.getEntityList().getFirst().getFirstValueList());
    Collections.reverse(source.getEntityList().getFirst().getSecondValueList());
    destination.getEntityList().getFirst().setSecondPinnedIndex(1);
    try (var sourceDirector = TestdataMultipleListSolution.buildScoreDirector();
        var destinationDirector = TestdataMultipleListSolution.buildScoreDirector()) {
      sourceDirector.setWorkingSolution(source);
      destinationDirector.setWorkingSolution(destination);
      var assignments =
          SolutionAssignments.capture(sourceDirector.getSolutionDescriptor(), source)
              .rebase(destinationDirector);
      assertThatThrownBy(() -> assignments.apply(destinationDirector))
          .hasMessageContaining("Pinned list segment differs");
      assertThat(destination.getEntityList().getFirst().getBasicValue()).isZero();
      assertThat(destination.getEntityList().getFirst().getFirstValueList())
          .containsExactly(
              destination.getFirstValueList().get(0), destination.getFirstValueList().get(2));
      assertThat(destination.getEntityList().getFirst().getSecondValueList())
          .containsExactly(
              destination.getSecondValueList().get(0), destination.getSecondValueList().get(2));
    }
  }
}
