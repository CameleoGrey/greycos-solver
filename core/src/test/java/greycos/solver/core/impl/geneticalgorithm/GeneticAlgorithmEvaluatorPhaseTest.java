package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.MixedConstraints;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.MixedSolution;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.Owner;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.Task;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmEvaluatorPhaseTest {

  @ParameterizedTest
  @ValueSource(longs = {0, 37, 997})
  void basicLogicalTraceAndPublicationsAreIndependentOfWorkerCount(long seed) {
    Run baseline = null;
    for (int workers : new int[] {1, 2, 4}) {
      var solver =
          GeneticAlgorithmIntegrationTest.solver(
              GeneticAlgorithmIntegrationTest.config(phase(workers, 7, 125)).withRandomSeed(seed));
      var run = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
      var result = solver.solve(GeneticAlgorithmIntegrationTest.problem(5, 12));
      GeneticAlgorithmIntegrationTest.assertReplay(result);
      assertThat(run.steps).hasSize(125);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(125);
      assertWorkersClosed(solver, workers);
      if (baseline == null) baseline = run;
      else assertSameLogicalRun(run, baseline);
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 37, 997})
  void listLogicalTraceRetainsOneCoordinatorSessionAcrossWorkerCounts(long seed) {
    List<GeneticAlgorithmListIntegrationTest.Step> baseline = null;
    for (int workers : new int[] {1, 2, 4}) {
      var solver =
          GeneticAlgorithmListIntegrationTest.solver(
              GeneticAlgorithmListIntegrationTest.config(phase(workers, 7, 125))
                  .withRandomSeed(seed)
                  .withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
      var trace = GeneticAlgorithmListIntegrationTest.trace(solver);
      var result = solver.solve(GeneticAlgorithmListIntegrationTest.problem(12, 3));
      GeneticAlgorithmListIntegrationTest.assertFreshReplay(result);
      assertThat(trace).hasSize(125);
      assertWorkersClosed(solver, workers);
      if (baseline == null) baseline = trace;
      else assertThat(trace).containsExactlyElementsOf(baseline);
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 37, 997})
  void mixedEntityReferencesAndStructuralRejectionKeepTheSameLogicalTrace(long seed) {
    Run baseline = null;
    for (int workers : new int[] {1, 2, 4}) {
      var config =
          new SolverConfig()
              .withSolutionClass(MixedSolution.class)
              .withEntityClasses(Owner.class, Task.class)
              .withConstraintProviderClass(MixedConstraints.class)
              .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
              .withRandomSeed(seed)
              .withPhases(phase(workers, 7, 125));
      var solver =
          (DefaultSolver<MixedSolution>) SolverFactory.<MixedSolution>create(config).buildSolver();
      var run = observe(solver, GeneticAlgorithmEvaluatorPhaseTest::mixedSnapshot);
      var result = solver.solve(GeneticAlgorithmMixedIntegrationTest.problem());
      assertThat(result.score)
          .isEqualTo(SimpleScore.of(-result.tasks.stream().mapToInt(task -> task.depth).sum()));
      assertThat(run.steps).hasSize(125);
      assertThat(run.steps)
          .anySatisfy(
              step -> assertThat(step.outcome()).isEqualTo(GeneticAlgorithmOutcome.INVALID));
      assertWorkersClosed(solver, workers);
      if (baseline == null) baseline = run;
      else assertSameLogicalRun(run, baseline);
    }
  }

  @Test
  void tinySearchSpacePreservesDuplicateOutcomesAndTieAdmissions() {
    Run baseline = null;
    for (int workers : new int[] {1, 2, 4}) {
      var phase =
          phase(workers, 3, 100)
              .withNoProgressAttemptLimit(1000L)
              .withMutationOperators(mutation(GeneticAlgorithmMutationType.CHANGE));
      var solver =
          GeneticAlgorithmIntegrationTest.solver(
              GeneticAlgorithmIntegrationTest.config(phase)
                  .withConstraintProviderClass(
                      GeneticAlgorithmGenerationPolicyTest.ConstantConstraints.class));
      var run = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
      var result = solver.solve(GeneticAlgorithmIntegrationTest.problem(2, 2));
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(run.steps).hasSize(100).allSatisfy(step -> assertThat(step.admitted()).isTrue());
      assertThat(run.steps)
          .extracting(Trace::outcome)
          .contains(GeneticAlgorithmOutcome.NO_CHANGE, GeneticAlgorithmOutcome.DUPLICATE);
      assertWorkersClosed(solver, workers);
      if (baseline == null) baseline = run;
      else assertSameLogicalRun(run, baseline);
    }
  }

  @Test
  void rangeInvalidCandidatesKeepTheCoordinatorBaselineAndLogicalCount() {
    Run baseline = null;
    for (int workers : new int[] {1, 2, 4}) {
      var config =
          new SolverConfig()
              .withSolutionClass(RangeSolution.class)
              .withEntityClasses(RangeEntity.class)
              .withConstraintProviderClass(RangeConstraints.class)
              .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
              .withRandomSeed(37L)
              .withPhases(
                  phase(workers, 1, 13)
                      .withMutationOperators(mutation(GeneticAlgorithmMutationType.SWAP)));
      var solver =
          (DefaultSolver<RangeSolution>) SolverFactory.<RangeSolution>create(config).buildSolver();
      var run =
          observe(
              solver, solution -> solution.entities.stream().map(entity -> entity.value).toList());
      var first = new RangeEntity();
      first.id = "first";
      first.range = List.of(0L, 1L);
      first.value = 0L;
      var second = new RangeEntity();
      second.id = "second";
      second.range = List.of(10L, 11L);
      second.value = 10L;
      var input = new RangeSolution();
      input.entities = new ArrayList<>(List.of(first, second));
      assertThat(solver.solve(input).score).isEqualTo(SimpleScore.of(10));
      assertThat(run.steps)
          .hasSize(13)
          .allSatisfy(
              step -> {
                assertThat(step.outcome()).isEqualTo(GeneticAlgorithmOutcome.INVALID);
                assertThat(step.changed()).isZero();
                assertThat(step.candidateScore()).isNull();
              });
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(13);
      assertWorkersClosed(solver, workers);
      if (baseline == null) baseline = run;
      else assertSameLogicalRun(run, baseline);
    }
  }

  @Test
  void laterWorkerCompletionCannotOvertakeTheFirstLogicalAttempt() throws Exception {
    var control = new CompletionControl();
    CompletionConstraints.control = control;
    try (var executor = Executors.newSingleThreadExecutor()) {
      var solver =
          GeneticAlgorithmIntegrationTest.solver(
              GeneticAlgorithmIntegrationTest.config(
                      phase(2, 7, 40)
                          .withMutationRateMultiplier(64.0)
                          .withMutationOperators(mutation(GeneticAlgorithmMutationType.CHANGE)))
                  .withConstraintProviderClass(CompletionConstraints.class)
                  .withThreadFactoryClass(IndexedThreadFactory.class));
      var run = observe(solver, GeneticAlgorithmIntegrationTest::assignments);
      var future =
          executor.submit(() -> solver.solve(GeneticAlgorithmIntegrationTest.problem(20, 64)));
      assertThat(control.firstScoring.await(10, TimeUnit.SECONDS)).isTrue();
      var phase = (DefaultGeneticAlgorithmPhase<?>) solver.getPhaseList().getFirst();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while ((phase.getEvaluatorDiagnostics() == null
              || phase.getEvaluatorDiagnostics().evaluatedCount() == 0)
          && System.nanoTime() < deadline) {
        Thread.sleep(5);
      }
      assertThat(phase.getEvaluatorDiagnostics().evaluatedCount()).isPositive();
      // Worker zero still owns the earliest result; only the six serial seeds have completed.
      assertThat(run.steps).hasSize(6);
      control.releaseFirst.countDown();
      var result = future.get(10, TimeUnit.SECONDS);
      GeneticAlgorithmIntegrationTest.assertReplay(result);
      assertThat(run.steps).hasSize(40);

      CompletionConstraints.control = null;
      var baselineSolver =
          GeneticAlgorithmIntegrationTest.solver(
              GeneticAlgorithmIntegrationTest.config(
                  phase(1, 7, 40)
                      .withMutationRateMultiplier(64.0)
                      .withMutationOperators(mutation(GeneticAlgorithmMutationType.CHANGE))));
      var baseline = observe(baselineSolver, GeneticAlgorithmIntegrationTest::assignments);
      GeneticAlgorithmIntegrationTest.assertReplay(
          baselineSolver.solve(GeneticAlgorithmIntegrationTest.problem(20, 64)));
      assertSameLogicalRun(run, baseline);
    } finally {
      control.releaseFirst.countDown();
      CompletionConstraints.control = null;
    }
  }

  private static GeneticAlgorithmPhaseConfig phase(int workers, int population, int steps) {
    return new GeneticAlgorithmPhaseConfig()
        .withEvaluatorThreadCount(workers)
        .withPopulationSize(population)
        .withPBestRate(0.8)
        .withTabuEntityRate(0.25)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static GeneticAlgorithmMutationOperatorConfig mutation(
      GeneticAlgorithmMutationType type) {
    return new GeneticAlgorithmMutationOperatorConfig().withType(type).withProbability(1.0);
  }

  private static Object mixedSnapshot(MixedSolution solution) {
    return List.of(
        solution.owners.stream().map(owner -> owner.start).toList(),
        solution.owners.stream()
            .map(owner -> owner.tasks.stream().map(Task::getCode).toList())
            .toList(),
        solution.tasks.stream().map(task -> task.duration).toList(),
        solution.tasks.stream()
            .map(task -> task.dependency == null ? "-" : task.dependency.getCode())
            .toList(),
        solution.tasks.stream().map(task -> task.depth).toList());
  }

  private static <Solution_> Run observe(
      DefaultSolver<Solution_> solver, Function<Solution_, Object> snapshot) {
    var run = new Run();
    solver.addEventListener(
        event -> run.publications.add(snapshot.apply(event.getNewBestSolution())));
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          private Object session;

          @Override
          public void phaseStarted(AbstractPhaseScope<Solution_> scope) {
            session =
                ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession();
          }

          @Override
          public void stepEnded(AbstractStepScope<Solution_> scope) {
            var step = (GeneticAlgorithmStepScope<Solution_>) scope;
            assertThat(
                    ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector())
                        .getSession())
                .isSameAs(session);
            run.steps.add(
                new Trace(
                    step.getStepIndex(),
                    step.isSeeding(),
                    step.getGeneration(),
                    step.getCandidateId(),
                    step.getFirstParentId(),
                    step.getSecondParentId(),
                    step.getNativeId(),
                    step.isCrossed(),
                    step.getMutationType(),
                    step.getMutationGroup(),
                    step.getOutcome(),
                    step.isAdmitted(),
                    step.getChangedAssignmentCount(),
                    step.getBeforeScore(),
                    step.getBestBeforeScore(),
                    step.getCandidateScore(),
                    step.getScore(),
                    step.getBestScoreImproved(),
                    snapshot.apply(step.getWorkingSolution())));
          }
        });
    return run;
  }

  private static void assertSameLogicalRun(Run actual, Run expected) {
    assertThat(actual.steps).containsExactlyElementsOf(expected.steps);
    assertThat(actual.publications).containsExactlyElementsOf(expected.publications);
  }

  private static void assertWorkersClosed(DefaultSolver<?> solver, int workers) {
    var phase = (DefaultGeneticAlgorithmPhase<?>) solver.getPhaseList().getFirst();
    var diagnostics = phase.getEvaluatorDiagnostics();
    assertThat(diagnostics.initializedWorkerCount()).isEqualTo(workers);
    assertThat(diagnostics.closedWorkerCount()).isEqualTo(workers);
    assertThat(diagnostics.sessionCount()).isEqualTo(workers);
    assertThat(diagnostics.calculationCount()).isEqualTo(diagnostics.transferredCalculationCount());
  }

  private static final class Run {
    private final List<Trace> steps = new ArrayList<>();
    private final List<Object> publications = new ArrayList<>();
  }

  private record Trace(
      int index,
      boolean seeding,
      long generation,
      long candidate,
      long firstParent,
      long secondParent,
      long nativeMember,
      boolean crossed,
      GeneticAlgorithmMutationType mutation,
      String group,
      GeneticAlgorithmOutcome outcome,
      boolean admitted,
      int changed,
      InnerScore<?> beforeScore,
      InnerScore<?> bestBefore,
      InnerScore<?> candidateScore,
      InnerScore<?> workspaceScore,
      boolean bestImproved,
      Object assignments) {}

  private static final class CompletionControl {
    private final CountDownLatch firstScoring = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);
    private final AtomicBoolean blocked = new AtomicBoolean();
    private final ThreadLocal<Integer> visits = ThreadLocal.withInitial(() -> 0);

    private void score() {
      if (!Thread.currentThread().getName().equals("ga-evaluator-order-0")) return;
      int count = visits.get() + 1;
      visits.set(count);
      // The first 64 visits belong to the initialized worker graph.
      if (count <= 64 || !blocked.compareAndSet(false, true)) return;
      firstScoring.countDown();
      try {
        if (!releaseFirst.await(10, TimeUnit.SECONDS))
          throw new IllegalStateException("Evaluator completion test timed out.");
      } catch (InterruptedException interruption) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Evaluator completion test was interrupted.", interruption);
      }
    }
  }

  public static final class IndexedThreadFactory implements ThreadFactory {
    private final AtomicInteger index = new AtomicInteger();

    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, "ga-evaluator-order-" + index.getAndIncrement());
    }
  }

  @PlanningSolution
  public static class RangeSolution {
    @PlanningEntityCollectionProperty public List<RangeEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class RangeEntity {
    @PlanningId public String id;

    @ValueRangeProvider(id = "range")
    public List<Long> range;

    @PlanningVariable(valueRangeProviderRefs = "range")
    public Long value;
  }

  public static class RangeConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(RangeEntity.class)
            .reward(SimpleScore.ONE, entity -> entity.value)
            .asConstraint("value")
      };
    }
  }

  public static final class CompletionConstraints implements ConstraintProvider {
    private static volatile CompletionControl control;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .reward(
                SimpleScore.ONE,
                entity -> {
                  var current = control;
                  if (current != null) current.score();
                  return Integer.parseInt(entity.getValue().getCode());
                })
            .asConstraint("value")
      };
    }
  }
}
