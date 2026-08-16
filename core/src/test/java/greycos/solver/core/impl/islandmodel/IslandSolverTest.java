package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class IslandSolverTest {

  @Test
  void exposesAgentPhasesForEventProducerIdentification() {
    @SuppressWarnings("unchecked")
    Phase<TestdataSolution> customPhase = mock(Phase.class);
    when(customPhase.getEventProducerIdSupplier()).thenReturn(EventProducerId::customPhase);
    @SuppressWarnings("unchecked")
    Phase<TestdataSolution> localSearchPhase = mock(Phase.class);
    when(localSearchPhase.getEventProducerIdSupplier()).thenReturn(EventProducerId::localSearch);

    @SuppressWarnings("unchecked")
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller = mock(BestSolutionRecaller.class);
    var solver =
        new IslandSolver<>(
            bestSolutionRecaller,
            new BasicPlumbingTermination<>(false),
            List.of(customPhase, localSearchPhase));
    @SuppressWarnings("unchecked")
    SolverScope<TestdataSolution> solverScope = mock(SolverScope.class);
    when(solverScope.getSolver()).thenReturn(solver);

    assertThat(new CustomPhaseScope<>(solverScope, 0).getPhaseId())
        .isEqualTo(EventProducerId.customPhase(0));
    assertThat(new LocalSearchPhaseScope<>(solverScope, 1).getPhaseId())
        .isEqualTo(EventProducerId.localSearch(1));
  }

  @Test
  void solvingErrorCleansAgentPhaseAndScoreDirector() {
    @SuppressWarnings("unchecked")
    Phase<TestdataSolution> phase = mock(Phase.class);
    @SuppressWarnings("unchecked")
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller = mock(BestSolutionRecaller.class);
    var solver =
        new IslandSolver<>(
            bestSolutionRecaller, new BasicPlumbingTermination<>(false), List.of(phase));
    var solverScope = new SolverScope<TestdataSolution>();
    @SuppressWarnings("unchecked")
    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    solverScope.setScoreDirector(scoreDirector);
    var failure = new IllegalStateException("Expected test failure");

    solver.solvingError(solverScope, failure);

    verify(phase).solvingError(solverScope, failure);
    verify(scoreDirector).close();
  }
}
