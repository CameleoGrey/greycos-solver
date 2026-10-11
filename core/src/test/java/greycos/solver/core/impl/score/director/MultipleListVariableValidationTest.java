package greycos.solver.core.impl.score.director;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class MultipleListVariableValidationTest {

  @Test
  void validatesDuplicateMembershipEvenWithAllFourShadowsExternalized() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(1, 1, 2);
    var value = solution.getFirstValueList().getFirst();
    solution.getEntityList().forEach(owner -> owner.getFirstValueList().add(value));
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> director.setWorkingSolution(solution))
          .withMessageContaining("assigned more than once", "firstValueList");
      assertThat(value.getEntity()).isNull();
    }
  }

  @Test
  void rejectsDuplicateMembershipWithinOneOwner() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(1, 0, 1);
    var value = solution.getFirstValueList().getFirst();
    solution.getEntityList().getFirst().getFirstValueList().addAll(List.of(value, value));
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> director.setWorkingSolution(solution))
          .withMessageContaining("assigned more than once");
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void rejectsAliasedEmptyContainersBeforeAnyMove() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(0, 0, 1);
    var owner = solution.getEntityList().getFirst();
    var shared = new ArrayList<>();
    owner.setFirstValueList((List) shared);
    owner.setSecondValueList((List) shared);
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> director.setWorkingSolution(solution))
          .withMessageContaining("list container", "firstValueList", "secondValueList");
    }
  }

  @Test
  void rejectsForeignInstanceEvenIfItHasTheSamePlanningId() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(1, 0, 1);
    solution
        .getEntityList()
        .getFirst()
        .getFirstValueList()
        .add(new TestdataMultipleListFirstValue(solution.getFirstValueList().getFirst().getCode()));
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> director.setWorkingSolution(solution))
          .withMessageContaining("not an object from its value range", "firstValueList");
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void rejectsWrongRuntimeTypeInRange() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(1, 1, 1);
    ((List) solution.getSecondValueList()).add(solution.getFirstValueList().getFirst());
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> director.setWorkingSolution(solution))
          .withMessageContaining("declared element type", "secondValueList");
    }
  }

  @Test
  void revalidatesAfterStructuralRefreshAndCanRecoverWithAValidSolution() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(1, 1, 2);
    var value = solution.getFirstValueList().getFirst();
    solution.getEntityList().getFirst().getFirstValueList().add(value);
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var other = solution.getEntityList().getLast();
      director.beforeProblemPropertyChanged(other);
      other.getFirstValueList().add(value);
      director.afterProblemPropertyChanged(other);
      assertThatIllegalArgumentException()
          .isThrownBy(director::calculateScore)
          .withMessageContaining("assigned more than once");
      other.getFirstValueList().clear();
      director.setWorkingSolution(solution);
      assertThat(value.getEntity()).isSameAs(solution.getEntityList().getFirst());
      assertThat(director.calculateScore()).isNotNull();
    }
  }

  @Test
  void rejectsNotificationsForTheWrongListFamily() {
    var solution = TestdataMultipleListSolution.generateUninitializedSolution(1, 1, 1);
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var second =
          director.getSolutionDescriptor().resolveListVariableDescriptor(null, "secondValueList");
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  director.beforeListVariableElementAssigned(
                      second, solution.getFirstValueList().getFirst()))
          .withMessageContaining("not compatible", "secondValueList");
    }
  }
}
