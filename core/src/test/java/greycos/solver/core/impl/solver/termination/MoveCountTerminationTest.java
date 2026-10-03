package greycos.solver.core.impl.solver.termination;

import static greycos.solver.core.testutil.PlannerTestUtils.mockSolverScope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.when;

import java.time.Clock;

import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MoveCountTerminationTest {

  @Test
  void phaseTermination() {
    var termination = new MoveCountTermination<TestdataSolution>(4);
    SolverScope<TestdataSolution> moveScope = mockSolverScope();
    when(moveScope.getClock()).thenReturn(Clock.systemUTC());
    org.mockito.Mockito.doReturn(Mockito.mock(InnerScoreDirector.class))
        .when(moveScope)
        .getScoreDirector();
    when(moveScope.getMoveEvaluationCount()).thenReturn(100L);
    var phaseScope = new LocalSearchPhaseScope<>(moveScope, 1);
    phaseScope.startingNow();

    when(moveScope.getMoveEvaluationCount()).thenReturn(100L);
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope)).isEqualTo(0.0, offset(0.0));
    when(moveScope.getMoveEvaluationCount()).thenReturn(101L);
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope)).isEqualTo(0.25, offset(0.0));
    when(moveScope.getMoveEvaluationCount()).thenReturn(102L);
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope)).isEqualTo(0.5, offset(0.0));
    when(moveScope.getMoveEvaluationCount()).thenReturn(103L);
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope)).isEqualTo(0.75, offset(0.0));
    when(moveScope.getMoveEvaluationCount()).thenReturn(104L);
    assertThat(termination.isPhaseTerminated(phaseScope)).isTrue();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope)).isEqualTo(1.0, offset(0.0));
    when(moveScope.getMoveEvaluationCount()).thenReturn(105L);
    assertThat(termination.isPhaseTerminated(phaseScope)).isTrue();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope)).isEqualTo(1.0, offset(0.0));
  }

  @Test
  void solverTermination() {
    var termination = new MoveCountTermination<TestdataSolution>(4);
    SolverScope<TestdataSolution> solverScope = mockSolverScope();

    when(solverScope.getMoveEvaluationCount()).thenReturn(0L);
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    assertThat(termination.calculateSolverTimeGradient(solverScope)).isEqualTo(0.0, offset(0.0));
    when(solverScope.getMoveEvaluationCount()).thenReturn(1L);
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    assertThat(termination.calculateSolverTimeGradient(solverScope)).isEqualTo(0.25, offset(0.0));
    when(solverScope.getMoveEvaluationCount()).thenReturn(2L);
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    assertThat(termination.calculateSolverTimeGradient(solverScope)).isEqualTo(0.5, offset(0.0));
    when(solverScope.getMoveEvaluationCount()).thenReturn(3L);
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    assertThat(termination.calculateSolverTimeGradient(solverScope)).isEqualTo(0.75, offset(0.0));
    when(solverScope.getMoveEvaluationCount()).thenReturn(4L);
    assertThat(termination.isSolverTerminated(solverScope)).isTrue();
    assertThat(termination.calculateSolverTimeGradient(solverScope)).isEqualTo(1.0, offset(0.0));
    when(solverScope.getMoveEvaluationCount()).thenReturn(5L);
    assertThat(termination.isSolverTerminated(solverScope)).isTrue();
    assertThat(termination.calculateSolverTimeGradient(solverScope)).isEqualTo(1.0, offset(0.0));
  }

  @Test
  void invalidTermination() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MoveCountTermination<TestdataSolution>(-1L));
  }
}
