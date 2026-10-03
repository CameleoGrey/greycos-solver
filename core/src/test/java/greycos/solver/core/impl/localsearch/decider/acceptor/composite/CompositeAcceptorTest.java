package greycos.solver.core.impl.localsearch.decider.acceptor.composite;

import static greycos.solver.core.testutil.PlannerAssert.verifyPhaseLifecycle;
import static greycos.solver.core.testutil.PlannerTestUtils.mockSolverScope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.CompositeAcceptor;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class CompositeAcceptorTest {

  @Test
  void phaseLifecycle() {
    SolverScope<TestdataSolution> solverScope = mockSolverScope();
    LocalSearchPhaseScope<TestdataSolution> phaseScope = mock(LocalSearchPhaseScope.class);
    LocalSearchStepScope<TestdataSolution> stepScope = mock(LocalSearchStepScope.class);

    Acceptor<TestdataSolution> acceptor1 = mock(Acceptor.class);
    Acceptor<TestdataSolution> acceptor2 = mock(Acceptor.class);
    Acceptor<TestdataSolution> acceptor3 = mock(Acceptor.class);
    var compositeAcceptor = new CompositeAcceptor<>(acceptor1, acceptor2, acceptor3);

    compositeAcceptor.solvingStarted(solverScope);
    compositeAcceptor.phaseStarted(phaseScope);
    compositeAcceptor.stepStarted(stepScope);
    compositeAcceptor.stepEnded(stepScope);
    compositeAcceptor.stepStarted(stepScope);
    compositeAcceptor.stepEnded(stepScope);
    compositeAcceptor.phaseEnded(phaseScope);
    compositeAcceptor.phaseStarted(phaseScope);
    compositeAcceptor.stepStarted(stepScope);
    compositeAcceptor.stepEnded(stepScope);
    compositeAcceptor.phaseEnded(phaseScope);
    compositeAcceptor.solvingEnded(solverScope);

    verifyPhaseLifecycle(acceptor1, 1, 2, 3);
    verifyPhaseLifecycle(acceptor2, 1, 2, 3);
    verifyPhaseLifecycle(acceptor3, 1, 2, 3);
  }

  @Test
  void isAccepted() {
    assertThat(isCompositeAccepted(true, true, true)).isTrue();
    assertThat(isCompositeAccepted(false, true, true)).isFalse();
    assertThat(isCompositeAccepted(true, false, true)).isFalse();
    assertThat(isCompositeAccepted(true, true, false)).isFalse();
    assertThat(isCompositeAccepted(false, false, false)).isFalse();
  }

  @Test
  void finalDecisionReachesChildrenSkippedByShortCircuit() {
    Acceptor<TestdataSolution> first = mock(Acceptor.class);
    Acceptor<TestdataSolution> skipped = mock(Acceptor.class);
    var composite = new CompositeAcceptor<>(first, new CompositeAcceptor<>(skipped));
    var moveScope = new LocalSearchMoveScope<TestdataSolution>(null, 0, null);
    moveScope.setInitializedScore(SimpleScore.ZERO);

    moveScope.setAccepted(composite.isAccepted(moveScope));
    composite.moveEvaluated(moveScope);

    assertThat(moveScope.getAccepted()).isFalse();
    verify(skipped, never()).isAccepted(any());
    verify(first).moveEvaluated(moveScope);
    verify(skipped).moveEvaluated(moveScope);
  }

  @Test
  void planningValueRequirementIncludesNestedChildren() {
    Acceptor<TestdataSolution> ordinary = mock(Acceptor.class);
    Acceptor<TestdataSolution> valueSensitive = mock(Acceptor.class);
    var composite =
        new CompositeAcceptor<>(ordinary, new CompositeAcceptor<>(ordinary, valueSensitive));

    assertThat(composite.requiresPlanningValues()).isFalse();
    when(valueSensitive.requiresPlanningValues()).thenReturn(true);
    assertThat(composite.requiresPlanningValues()).isTrue();
  }

  private boolean isCompositeAccepted(boolean... childAccepts) {
    var acceptorList = new ArrayList<Acceptor<TestdataSolution>>(childAccepts.length);
    for (var childAccept : childAccepts) {
      Acceptor<TestdataSolution> acceptor = mock(Acceptor.class);
      when(acceptor.isAccepted(any(LocalSearchMoveScope.class))).thenReturn(childAccept);
      acceptorList.add(acceptor);
    }
    var acceptor = new CompositeAcceptor<>(acceptorList);
    var moveScope = mock(LocalSearchMoveScope.class);
    when(moveScope.getScore()).thenReturn(InnerScore.fullyAssigned(new SimpleScore(0)));
    return acceptor.isAccepted(moveScope);
  }
}
