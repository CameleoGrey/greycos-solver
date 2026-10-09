package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.solver.event.SolverEventListener;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class GeneticAlgorithmObservationCapabilityTest {

  @Test
  void lifecycleObservationTracksRegistrationAndRemovalAtBothLevels() {
    var solver =
        GeneticAlgorithmIntegrationTest.solver(
            GeneticAlgorithmIntegrationTest.config(new GeneticAlgorithmPhaseConfig()));
    var phase = (DefaultGeneticAlgorithmPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    var solverListener = new PhaseLifecycleListenerAdapter<TestdataSolution>() {};

    assertThat(solver.isTerminationWorkingSolutionIndependent()).isTrue();
    assertThat(solver.hasPhaseLifecycleListeners()).isFalse();
    assertThat(phase.hasPhaseLifecycleListeners()).isFalse();
    solver.addPhaseLifecycleListener(solverListener);
    assertThat(solver.hasPhaseLifecycleListeners()).isTrue();
    assertThat(phase.hasPhaseLifecycleListeners()).isFalse();
    solver.removePhaseLifecycleListener(solverListener);
    assertThat(solver.hasPhaseLifecycleListeners()).isFalse();

    var phaseListener = new PhaseLifecycleListenerAdapter<TestdataSolution>() {};
    phase.addPhaseLifecycleListener(phaseListener);
    assertThat(phase.hasPhaseLifecycleListeners()).isTrue();
    assertThat(solver.hasPhaseLifecycleListeners()).isFalse();
    phase.removePhaseLifecycleListener(phaseListener);
    assertThat(phase.hasPhaseLifecycleListeners()).isFalse();
  }

  @Test
  void bestSolutionEventsDoNotImplyWorkingGraphObservationBetweenPublications() {
    var solver =
        GeneticAlgorithmIntegrationTest.solver(
            GeneticAlgorithmIntegrationTest.config(new GeneticAlgorithmPhaseConfig()));
    SolverEventListener<TestdataSolution> listener = event -> {};
    solver.addEventListener(listener);

    assertThat(solver.hasPhaseLifecycleListeners()).isFalse();

    solver.removeEventListener(listener);
    var scope = new GeneticAlgorithmPhaseScope<>(solver.getSolverScope(), 0);
    assertThat(scope.hasCommittedStepListeners()).isFalse();
    scope.addCommittedStepListener(step -> {});
    assertThat(scope.hasCommittedStepListeners()).isTrue();
  }
}
