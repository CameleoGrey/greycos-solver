package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.IslandWorkAccounting;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.termination.IslandSequenceTermination;
import greycos.solver.core.impl.solver.termination.IslandTerminationBudget;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GAIslandCommittedStepTest {

  @ParameterizedTest
  @ValueSource(longs = {0L, 3L})
  void lateCompletionFailureNeverPublishesTheProvisionalOuterCredit(long probes) {
    var fixture = new Fixture(probes);
    var phase = recordPhase(fixture.solver);
    var failure = new IllegalStateException("Late completion listener failed.");
    fixture
        .solver
        .getPhaseList()
        .getFirst()
        .addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void stepEnded(AbstractStepScope<TestdataSolution> context) {
                var step = (GeneticAlgorithmStepScope<TestdataSolution>) context;
                var scope = step.getPhaseScope();
                long committedMoves = scope.getCommittedMoveEvaluationCount();
                assertThat(scope.getSolverScope().getMoveEvaluationCount())
                    .isEqualTo(committedMoves + 1L);
                assertThat(fixture.termination.calculatePhaseTimeGradient(scope))
                    .isEqualTo(committedMoves / 100.0);
                assertThat(fixture.samples).hasSize(scope.getNextStepIndex());
                if (probes == 0L || step.getLocalImprovementProbeCount() > 0L) {
                  throw failure;
                }
              }
            });

    assertThatThrownBy(() -> fixture.solver.solve(problem(21, 8))).isSameAs(failure);

    var scope = phase.get();
    assertThat(fixture.solver.getSolverScope().getMoveEvaluationCount())
        .isEqualTo(scope.getCommittedMoveEvaluationCount());
    assertThat(fixture.samples).hasSize(scope.getNextStepIndex());
    if (probes > 0L) assertThat(scope.getLocalImprovementProbeCount()).isPositive();
    fixture.publishFinalWork();
    assertThat(fixture.accounting.snapshot().moveEvaluationCount())
        .isEqualTo(scope.getCommittedMoveEvaluationCount());
  }

  @Test
  void successfulCompletionPublishesExactlyOneCommittedStepSample() {
    var fixture = new Fixture(0L);
    var phase = recordPhase(fixture.solver);
    fixture
        .solver
        .getPhaseList()
        .getFirst()
        .addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void stepEnded(AbstractStepScope<TestdataSolution> step) {
                assertThat(fixture.samples).hasSize(step.getStepIndex());
              }
            });

    fixture.solver.solve(problem(21, 8));

    assertThat(fixture.samples).hasSize(phase.get().getNextStepIndex());
    assertThat(fixture.samples).hasSize(20);
    for (int i = 0; i < fixture.samples.size(); i++) {
      assertThat(fixture.samples.get(i).work().moveEvaluationCount()).isEqualTo(i + 1L);
    }
    assertThat(fixture.accounting.snapshot().moveEvaluationCount()).isEqualTo(20L);
  }

  @Test
  void metricPublicationFailureAfterCommitDoesNotRollBackCompletedWork() {
    var fixture = new Fixture(0L);
    var phase = recordPhase(fixture.solver);
    var failure = new IllegalStateException("Committed metric publication failed.");
    fixture
        .solver
        .getSolverScope()
        .setMetricSamplePublisher(
            sample -> {
              fixture.accept(sample);
              if (sample.kind() == SolverMetricSample.Kind.STEP) throw failure;
            });

    assertThatThrownBy(() -> fixture.solver.solve(problem(21, 8))).isSameAs(failure);

    assertThat(phase.get().getNextStepIndex()).isEqualTo(1);
    assertThat(phase.get().getCommittedMoveEvaluationCount()).isEqualTo(1L);
    assertThat(fixture.solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(1L);
    assertThat(fixture.samples).hasSize(1);
    fixture.publishFinalWork();
    assertThat(fixture.accounting.snapshot().moveEvaluationCount()).isEqualTo(1L);
  }

  private static final class Fixture {
    private static final String SOURCE = "ga-island";
    private final DefaultSolver<TestdataSolution> solver;
    private final IslandSequenceTermination<TestdataSolution> termination;
    private final IslandWorkAccounting accounting = new IslandWorkAccounting();
    private final List<SolverMetricSample> samples = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private Fixture(long probes) {
      solver =
          solver(
              config(
                  new GeneticAlgorithmPhaseConfig()
                      .withPopulationSize(1)
                      .withLocalImprovementMoveCountLimit(probes)
                      .withMutationOperators(
                          new GeneticAlgorithmMutationOperatorConfig()
                              .withType(GeneticAlgorithmMutationType.CHANGE)
                              .withProbability(1.0))
                      .withTerminationConfig(new TerminationConfig().withStepCountLimit(20))));
      var scope = solver.getSolverScope();
      var policy = (HeuristicConfigPolicy<TestdataSolution>) mock(HeuristicConfigPolicy.class);
      when(policy.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
      var budget =
          new IslandTerminationBudget<>(
              new TerminationConfig().withMoveCountLimit(100L),
              policy,
              scope.getClock(),
              scope.getClock().millis());
      termination = budget.createIslandTermination(scope);
      // Exercise the same termination lifecycle as an island without creating extra worker threads.
      solver.getPhaseList().getFirst().addPhaseLifecycleListener(termination);
      scope.setMetricSource(SOURCE);
      accounting.register(SOURCE);
      scope.setMetricSamplePublisher(this::accept);
    }

    private void accept(SolverMetricSample sample) {
      if (SOURCE.equals(sample.source())) {
        accounting.publish(SOURCE, sample.work());
        if (sample.kind() == SolverMetricSample.Kind.STEP) samples.add(sample);
      }
    }

    private void publishFinalWork() {
      accounting.publish(SOURCE, SolverMetricSamples.localWork(solver.getSolverScope()));
    }
  }
}
