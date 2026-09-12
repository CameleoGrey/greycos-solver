package greycos.solver.core.impl.solver.termination;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.alns.DefaultAlnsPhase;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class TerminationBestScoreImprovementTest {
  @Test
  @SuppressWarnings("unchecked")
  void nestedCompositesAndBridgeNotifyEachHistoryWithoutEndingAStep() {
    var solverChild = mock(MockablePhaseTermination.class);
    var phaseChild = mock(MockablePhaseTermination.class);
    var otherPhaseChild = mock(MockablePhaseTermination.class);
    var solverTermination = UniversalTermination.<TestdataSolution>or(solverChild);
    var phaseTermination =
        UniversalTermination.and(
            PhaseTermination.bridge(solverTermination),
            UniversalTermination.or(phaseChild, otherPhaseChild));
    var step = new AlnsStepScope<>(new AlnsPhaseScope<>(new SolverScope<TestdataSolution>(), 0));
    step.setBestScoreImproved(true);

    phaseTermination.bestScoreImproved(step);

    for (var child : new PhaseTermination[] {solverChild, phaseChild, otherPhaseChild}) {
      verify(child).bestScoreImproved(step);
      verify(child, never()).stepStarted(step);
      verify(child, never()).stepEnded(step);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void localSolvingLifecycleResetsOnReuseWithoutRepeatingGlobalLifecycle() {
    var global =
        mock(
            MockableSolverTermination.class,
            withSettings().extraInterfaces(MockablePhaseTermination.class));
    var local =
        mock(
            MockableSolverTermination.class,
            withSettings().extraInterfaces(MockablePhaseTermination.class));
    var globalTermination = UniversalTermination.<TestdataSolution>or(global);
    var phaseTermination =
        UniversalTermination.or(PhaseTermination.bridge(globalTermination), local);
    var phase =
        new DefaultAlnsPhase.Builder<TestdataSolution>(
                0, "", phaseTermination, new AlnsPhaseConfig(), mock(BestSolutionRecaller.class))
            .build();
    var scope = new SolverScope<TestdataSolution>();

    for (int solve = 0; solve < 2; solve++) {
      globalTermination.solvingStarted(scope);
      phase.solvingStarted(scope);
      phase.solvingEnded(scope);
      globalTermination.solvingEnded(scope);
    }

    verify(global, times(2)).solvingStarted(scope);
    verify(global, times(2)).solvingEnded(scope);
    verify(local, times(2)).solvingStarted(scope);
    verify(local, times(2)).solvingEnded(scope);
  }
}
