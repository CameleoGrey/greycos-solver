package greycos.solver.core.api.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SolverRunApiCompatibilityTest {
  @Test
  void legacySolverAndJobImplementationsRetainTheirExistingMethods() throws Exception {
    var problem = new Object();
    var solver = new LegacySolverApiFixture.LegacySolver();
    var job = new LegacySolverApiFixture.LegacyJob(problem);
    assertThat(solver.solve(problem)).isSameAs(problem);
    assertThat(job.getFinalBestSolution()).isSameAs(problem);
    assertThatThrownBy(() -> solver.solveAndSummarize(problem))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(job::getResult).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void finalEventRemainsAFunctionalInterfaceWithoutInventingStatistics() {
    var problem = new Object();
    var event = LegacySolverApiFixture.legacyEvent(problem);
    assertThat(event.solution()).isSameAs(problem);
    assertThatThrownBy(event::solvingDuration).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(event::scoreCalculationCount)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(event::scoreCalculationSpeed)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(event::moveEvaluationCount)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(event::moveEvaluationSpeed)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(event::isSolutionInitialized)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(event::problemSizeStatistics)
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
