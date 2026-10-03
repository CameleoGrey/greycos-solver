package greycos.solver.core.impl.localsearch.decider.acceptor.tabu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;

class PlanningValueSnapshotTest {

  @Test
  void immutableCopyPreservesNullAndOrder() {
    var original = new ArrayList<Object>(Arrays.asList("a", null, "b", "a"));
    var snapshot = new PlanningValueSnapshot(original);
    original.clear();

    assertThat(snapshot.values()).containsExactly("a", null, "b", "a");
    assertThatThrownBy(() -> snapshot.values().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void structurallyInvalidCandidateDoesNotRequireMoveIntrospection() {
    var solution = TestdataSolution.generateSolution(2, 2);
    solution.setScore(new SimpleScore(-1, 0));
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    when(director.getSolutionDescriptor()).thenReturn(TestdataSolution.buildSolutionDescriptor());
    when(director.getWorkingSolution()).thenReturn(solution);
    Move<TestdataSolution> move = mock(Move.class);

    assertThat(PlanningValueSnapshot.capture(director, move)).isNull();
    verifyNoInteractions(move);
  }

  @Test
  @SuppressWarnings("unchecked")
  void sharedInstancesArePreservedAndClonesUseLookup() {
    var solution = TestdataSolution.generateSolution(2, 2);
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    when(director.getSolutionDescriptor()).thenReturn(TestdataSolution.buildSolutionDescriptor());
    when(director.getWorkingSolution()).thenReturn(solution);
    var shared = solution.getValueList().getFirst();
    var coordinatorValue = solution.getValueList().getLast();
    var workerClone = new TestdataValue(coordinatorValue.getCode());
    when(director.lookUpWorkingObject(workerClone)).thenReturn(coordinatorValue);
    var snapshot = new PlanningValueSnapshot(Arrays.asList(shared, null, workerClone));

    var rebased = new PlanningValueSnapshot.Rebaser<>(director).rebase(snapshot);

    assertThat(rebased.values()).containsExactly(shared, null, coordinatorValue);
    assertThat(rebased.values().getFirst()).isSameAs(shared);
    assertThat(rebased.values().getLast()).isSameAs(coordinatorValue);
    verify(director).lookUpWorkingObject(workerClone);
  }

  @Test
  @SuppressWarnings("unchecked")
  void unknownCloneIsNotSilentlyRetained() {
    var solution = TestdataSolution.generateSolution(2, 2);
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    when(director.getSolutionDescriptor()).thenReturn(TestdataSolution.buildSolutionDescriptor());
    when(director.getWorkingSolution()).thenReturn(solution);
    var unknown = new Object();
    when(director.lookUpWorkingObject(unknown))
        .thenThrow(new IllegalArgumentException("Unknown worker value."));

    var rebaser = new PlanningValueSnapshot.Rebaser<>(director);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> rebaser.rebase(new PlanningValueSnapshot(List.of(unknown))))
        .withMessage("Unknown worker value.");
  }
}
