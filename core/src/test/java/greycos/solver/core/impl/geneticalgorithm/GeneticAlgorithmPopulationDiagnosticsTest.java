package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmInvalidTrialTest.CycleConstraints;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmWorkspaceTest.CycleEntity;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmWorkspaceTest.CycleSolution;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmPopulationDiagnosticsTest {

  @ParameterizedTest
  @ValueSource(ints = {128, 512})
  void actualSeedingKeepsTotalValueComparisonsQuadratic(int populationSize) {
    var comparisons = new AtomicLong();
    var input = problem(1, 1);
    var values =
        IntStream.range(0, 4 * populationSize)
            .mapToObj(id -> (TestdataValue) new CountedValue(id, comparisons))
            .toList();
    input.setValueList(new ArrayList<>(values));
    input.getEntityList().getFirst().setValue(values.getFirst());
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(populationSize)
                    .withTerminationConfig(
                        new TerminationConfig().withStepCountLimit(populationSize - 1))));
    var measuredComparisons = new AtomicLong();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            comparisons.set(0);
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            measuredComparisons.set(comparisons.get());
            var phase = (GeneticAlgorithmPhaseScope<TestdataSolution>) scope;
            assertThat(phase.getPopulationSize()).isEqualTo(populationSize);
            assertThat(phase.getDistinctPopulationSize()).isGreaterThan(populationSize / 2);
            assertThat(phase.getGeneration()).isZero();
          }
        });

    assertReplay(solver.solve(input));

    assertThat(solver.getMoveEvaluationCount()).isEqualTo(populationSize - 1);
    // Includes normal fitness-cache lookups and range checks, with room for incidental equality
    // work. Recounting the growing population after every seed exceeds this bound.
    assertThat(measuredComparisons.get())
        .isPositive()
        .isLessThan(4L * populationSize * populationSize);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2, 4, 5, 9, 11, 14})
  void countsFollowCommittedPopulationAcrossSeedingGenerationsAndReuse(int limit) {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withPBestRate(1.0)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(limit))));
    var observer = new PopulationObserver();
    solver.addPhaseLifecycleListener(observer);

    // With one entity, its numeric score uniquely identifies its genome, even on a cache hit.
    assertReplay(solver.solve(problem(3, 1)));
    assertReplay(solver.solve(problem(3, 1)));

    assertThat(observer.completedRuns).isEqualTo(2);
    assertThat(solver.getMoveEvaluationCount()).isEqualTo(limit);
  }

  @Test
  void cancellationInsideGenerationKeepsCommittedPopulationDiagnostics() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withPBestRate(1.0)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(30))));
    var observer = new PopulationObserver();
    solver.addPhaseLifecycleListener(observer);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            // Four seeds, one full generation, then two pending winners.
            if (step.getStepIndex() == 10) {
              solver.terminateEarly();
            }
          }
        });

    assertReplay(solver.solve(problem(3, 1)));

    assertThat(observer.completedRuns).isEqualTo(1);
    assertThat(observer.generation).isEqualTo(1);
    assertThat(observer.pending).hasSize(2);
    assertThat(solver.getMoveEvaluationCount()).isEqualTo(11);
  }

  @Test
  void invalidSeedsAddFallbackEntriesWithoutInventingDiversity() {
    var config =
        new SolverConfig()
            .withSolutionClass(CycleSolution.class)
            .withEntityClasses(CycleEntity.class)
            .withConstraintProviderClass(CycleConstraints.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withRandomSeed(37L)
            .withPhases(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(4)));
    var solver =
        (DefaultSolver<CycleSolution>) SolverFactory.<CycleSolution>create(config).buildSolver();
    var invalid = new AtomicInteger();
    var phaseEnds = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<CycleSolution> scope) {
            var step = (GeneticAlgorithmStepScope<CycleSolution>) scope;
            assertThat(step.isSeeding()).isTrue();
            assertThat(step.getOutcome())
                .isIn(GeneticAlgorithmOutcome.INVALID, GeneticAlgorithmOutcome.NO_CHANGE);
            if (step.getOutcome() == GeneticAlgorithmOutcome.INVALID) {
              invalid.incrementAndGet();
              assertThat(step.isAdmitted()).isFalse();
            }
            // Publication of counts follows completion of the step callback, as before.
            assertThat(step.getPhaseScope().getPopulationSize()).isEqualTo(step.getStepIndex() + 1);
            assertThat(step.getPhaseScope().getDistinctPopulationSize()).isEqualTo(1);
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<CycleSolution> scope) {
            var phase = (GeneticAlgorithmPhaseScope<CycleSolution>) scope;
            assertThat(phase.getPopulationSize()).isEqualTo(5);
            assertThat(phase.getDistinctPopulationSize()).isEqualTo(1);
            assertThat(phase.getGeneration()).isZero();
            phaseEnds.incrementAndGet();
          }
        });
    var input = new CycleSolution();
    input.entities = new ArrayList<>(List.of(new CycleEntity()));

    var result = solver.solve(input);

    assertThat(invalid.get()).isPositive();
    assertThat(phaseEnds.get()).isEqualTo(1);
    assertThat(result.entities.getFirst().previous).isNull();
    assertThat(result.entities.getFirst().depth).isZero();
    assertThat(result.score).isEqualTo(SimpleScore.ZERO);
    assertThat(solver.getMoveEvaluationCount()).isEqualTo(4);
  }

  private static final class CountedValue extends TestdataValue {
    private final int id;
    private final AtomicLong comparisons;

    private CountedValue(int id, AtomicLong comparisons) {
      super(Integer.toString(id));
      this.id = id;
      this.comparisons = comparisons;
    }

    @Override
    public boolean equals(Object other) {
      comparisons.incrementAndGet();
      return other instanceof CountedValue value && id == value.id;
    }

    @Override
    public int hashCode() {
      return Integer.hashCode(id);
    }
  }

  private static final class PopulationObserver
      extends PhaseLifecycleListenerAdapter<TestdataSolution> {
    private final Map<Long, SimpleScore> discovered = new HashMap<>();
    private final List<SimpleScore> population = new ArrayList<>();
    private final List<SimpleScore> pending = new ArrayList<>();
    private long generation;
    private int completedRuns;

    @Override
    public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
      discovered.clear();
      population.clear();
      pending.clear();
      discovered.put(0L, SimpleScore.ZERO);
      population.add(SimpleScore.ZERO);
      generation = 0;
    }

    @Override
    public void stepStarted(AbstractStepScope<TestdataSolution> step) {
      assertCounts(step.getPhaseScope());
    }

    @Override
    public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
      assertCounts(scope.getPhaseScope());
      var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
      var candidate = (SimpleScore) step.getCandidateScore().raw();
      discovered.put(step.getCandidateId(), candidate);
      if (step.isSeeding()) {
        population.add(candidate);
      } else {
        pending.add(step.isAdmitted() ? candidate : discovered.get(step.getNativeId()));
        if (pending.size() == 5) {
          population.clear();
          population.addAll(pending);
          pending.clear();
          generation++;
        }
      }
    }

    @Override
    public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
      assertCounts(scope);
      completedRuns++;
    }

    private void assertCounts(AbstractPhaseScope<TestdataSolution> scope) {
      var phase = (GeneticAlgorithmPhaseScope<TestdataSolution>) scope;
      assertThat(phase.getPopulationSize()).isEqualTo(population.size());
      assertThat(phase.getDistinctPopulationSize())
          .isEqualTo(population.stream().distinct().count());
      assertThat(phase.getGeneration()).isEqualTo(generation);
    }
  }
}
