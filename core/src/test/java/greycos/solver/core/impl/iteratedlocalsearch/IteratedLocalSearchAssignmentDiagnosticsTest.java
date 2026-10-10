package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.impl.alns.AlnsMoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.move.SolutionAssignmentDiagnostics;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Isolated("Changes the opt-in assignment diagnostic system property.")
@Timeout(30)
class IteratedLocalSearchAssignmentDiagnosticsTest {
  private static final String PROPERTY = "greycos.solver.iteratedLocalSearchDiagnostics";

  @ParameterizedTest
  @CsvSource({"basic,NONE", "basic,2", "list,NONE", "list,2", "mixed,NONE", "mixed,2"})
  void assignmentDiagnosticsPreserveNativeTraceAndRestoreEnclosingCollector(
      String shape, String threads) {
    var workload = AlnsMoveThreadingWorkload.named(shape);
    var disabled = run(workload, shape, threads, false);
    var enabled = run(workload, shape, threads, true);
    assertThat(enabled.score()).isEqualTo(disabled.score());
    assertThat(enabled.assignments()).isEqualTo(disabled.assignments());
    assertThat(enabled.steps()).isEqualTo(disabled.steps());
    assertThat(enabled.diagnostics().episodeAttempts())
        .isEqualTo(disabled.diagnostics().episodeAttempts());
    assertThat(enabled.diagnostics().perturbationAttempts())
        .isEqualTo(disabled.diagnostics().perturbationAttempts());
    assertThat(disabled.diagnostics().fullAssignmentCaptures()).isZero();
    assertThat(disabled.diagnostics().assignmentComparisons()).isZero();
    assertThat(disabled.diagnostics().assignmentValidations()).isZero();
    assertThat(enabled.diagnostics().fullAssignmentCaptures()).isPositive();
    assertThat(enabled.diagnostics().copiedAssignmentBindings()).isPositive();
    assertThat(enabled.diagnostics().assignmentComparisons()).isPositive();
    assertThat(enabled.diagnostics().assignmentValidations()).isPositive();
    if (!shape.equals("basic")) assertThat(enabled.diagnostics().copiedListElements()).isPositive();
  }

  private static <S> Run run(Workload<S> workload, String shape, String threads, boolean enabled) {
    var previous = System.getProperty(PROPERTY);
    System.setProperty(PROPERTY, Boolean.toString(enabled));
    try (var enclosing = enabled ? SolutionAssignmentDiagnostics.open() : null) {
      var solver =
          (DefaultSolver<S>)
              SolverFactory.<S>create(
                      IteratedLocalSearchIntegrationTest.config(workload, shape, threads))
                  .buildSolver();
      var steps = new ArrayList<String>();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<S> step) {
              assertThat(workload.recompute(step.getWorkingSolution()))
                  .isEqualTo(step.getScore().raw());
              steps.add(step.getScore() + ":" + workload.state(step.getWorkingSolution()));
            }
          });
      var result = solver.solve(workload.createProblem(16));
      assertThat(workload.recompute(result)).isEqualTo(workload.score(result));
      if (enclosing != null) {
        long before = enclosing.getCaptureCount();
        SolutionAssignments.captureComplete(
            solver.getSolverScope().getSolutionDescriptor(), result);
        assertThat(enclosing.getCaptureCount()).isEqualTo(before + 1);
      }
      return new Run(
          workload.score(result).toString(),
          workload.state(result),
          List.copyOf(steps),
          ((DefaultIteratedLocalSearchPhase<S>) solver.getPhaseList().getFirst()).getDiagnostics());
    } finally {
      if (previous == null) System.clearProperty(PROPERTY);
      else System.setProperty(PROPERTY, previous);
    }
  }

  private record Run(
      String score,
      String assignments,
      List<String> steps,
      DefaultIteratedLocalSearchPhase.Diagnostics diagnostics) {}
}
