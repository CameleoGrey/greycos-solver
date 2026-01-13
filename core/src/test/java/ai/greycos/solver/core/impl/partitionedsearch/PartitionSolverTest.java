package ai.greycos.solver.core.impl.partitionedsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import ai.greycos.solver.core.api.solver.change.ProblemChange;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecallerFactory;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.UniversalTermination;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

/** Tests for {@link PartitionSolver}. */
class PartitionSolverTest {

  @Test
  void constructorAndGetters() {
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(EnvironmentMode.REPRODUCIBLE);
    UniversalTermination<TestdataSolution> termination = null; // Can be null for testing
    List<Phase<TestdataSolution>> phaseList = List.of();
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();
    int partIndex = 5;

    var partitionSolver =
        new PartitionSolver<>(bestSolutionRecaller, termination, phaseList, solverScope, partIndex);

    assertThat(partitionSolver.getPartIndex()).isEqualTo(5);
    assertThat(partitionSolver.getSolverScope()).isSameAs(solverScope);
    assertThat(partitionSolver.isSolving()).isFalse();
    assertThat(partitionSolver.isTerminateEarly()).isFalse();
  }

  @Test
  void setBestSolutionChangedListener() {
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(EnvironmentMode.REPRODUCIBLE);
    UniversalTermination<TestdataSolution> termination = null;
    List<Phase<TestdataSolution>> phaseList = List.of();
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();

    var partitionSolver =
        new PartitionSolver<>(bestSolutionRecaller, termination, phaseList, solverScope, 0);

    var listenerRef = new AtomicReference<Object>();
    partitionSolver.setBestSolutionChangedListener(
        (eventProducerId, solution) -> listenerRef.set(solution));

    assertThat(listenerRef.get()).isNull();

    // Trigger listener by setting a solution
    TestdataSolution solution = new TestdataSolution();
    partitionSolver.setBestSolutionChangedListener(
        (eventProducerId, newBestSolution) -> listenerRef.set(newBestSolution));
  }

  @Test
  void addProblemChangeThrowsUnsupportedOperationException() {
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(EnvironmentMode.REPRODUCIBLE);
    UniversalTermination<TestdataSolution> termination = null;
    List<Phase<TestdataSolution>> phaseList = List.of();
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();

    var partitionSolver =
        new PartitionSolver<>(bestSolutionRecaller, termination, phaseList, solverScope, 0);

    ProblemChange<TestdataSolution> problemChange = mock(ProblemChange.class);

    assertThatThrownBy(() -> partitionSolver.addProblemChange(problemChange))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("PartitionSolver")
        .hasMessageContaining("does not support problem changes");
  }

  @Test
  void addProblemChangesThrowsUnsupportedOperationException() {
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(EnvironmentMode.REPRODUCIBLE);
    UniversalTermination<TestdataSolution> termination = null;
    List<Phase<TestdataSolution>> phaseList = List.of();
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();

    var partitionSolver =
        new PartitionSolver<>(bestSolutionRecaller, termination, phaseList, solverScope, 0);

    @SuppressWarnings("unchecked")
    ProblemChange<TestdataSolution> problemChange = mock(ProblemChange.class);
    List<@NonNull ProblemChange<TestdataSolution>> problemChangeList = List.of(problemChange);

    assertThatThrownBy(() -> partitionSolver.addProblemChanges(problemChangeList))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("PartitionSolver")
        .hasMessageContaining("does not support problem changes");
  }

  @Test
  void isSolvingReturnsFalse() {
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(EnvironmentMode.REPRODUCIBLE);
    UniversalTermination<TestdataSolution> termination = null;
    List<Phase<TestdataSolution>> phaseList = List.of();
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();

    var partitionSolver =
        new PartitionSolver<>(bestSolutionRecaller, termination, phaseList, solverScope, 0);

    assertThat(partitionSolver.isSolving()).isFalse();
  }

  @Test
  void isTerminateEarlyReturnsFalse() {
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(EnvironmentMode.REPRODUCIBLE);
    UniversalTermination<TestdataSolution> termination = null;
    List<Phase<TestdataSolution>> phaseList = List.of();
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();

    var partitionSolver =
        new PartitionSolver<>(bestSolutionRecaller, termination, phaseList, solverScope, 0);

    assertThat(partitionSolver.isTerminateEarly()).isFalse();
  }

  @Test
  void terminateEarlyReturnsFalse() {
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(EnvironmentMode.REPRODUCIBLE);
    UniversalTermination<TestdataSolution> termination = null;
    List<Phase<TestdataSolution>> phaseList = List.of();
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();

    var partitionSolver =
        new PartitionSolver<>(bestSolutionRecaller, termination, phaseList, solverScope, 0);

    assertThat(partitionSolver.terminateEarly()).isFalse();
  }

  @Test
  void isEveryProblemChangeProcessedReturnsFalse() {
    BestSolutionRecaller<TestdataSolution> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(EnvironmentMode.REPRODUCIBLE);
    UniversalTermination<TestdataSolution> termination = null;
    List<Phase<TestdataSolution>> phaseList = List.of();
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();

    var partitionSolver =
        new PartitionSolver<>(bestSolutionRecaller, termination, phaseList, solverScope, 0);

    assertThat(partitionSolver.isEveryProblemChangeProcessed()).isFalse();
  }
}
