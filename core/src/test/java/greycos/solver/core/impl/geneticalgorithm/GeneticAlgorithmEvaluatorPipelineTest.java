package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmEvaluatorPipelineTest {

  @Test
  void committedHeadRefillsItsWorkerWhileTheNextLogicalAttemptIsStillBlocked() throws Exception {
    var solver = pipelineSolver(2, 40);
    var run = observe(solver, false);
    try (var control = new PipelineControl(false);
        var executor = Executors.newSingleThreadExecutor()) {
      countCommittedOffspring(solver, control);
      var future = executor.submit(() -> solver.solve(problem(20, 64)));
      try {
        assertThat(control.secondEntered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(control.refillEntered.await(10, TimeUnit.SECONDS))
            .as("Worker zero must receive its next job before worker one's first job completes")
            .isTrue();
        assertThat(control.offspringAtRefill).hasValue(1);
        assertThat(control.committedOffspring).hasValue(1);
        assertThat(control.release.getCount()).isEqualTo(1);
        var diagnostics = diagnostics(solver);
        assertThat(diagnostics.consumedCount()).isEqualTo(1);

        control.release.countDown();
        assertReplay(future.get(10, TimeUnit.SECONDS));
        assertThat(run.steps).hasSize(40);
        assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(40);
        assertThat(solver.getSolverScope().getScoreCalculationCount())
            .isEqualTo(control.successfulCalls.get());
        control.assertWorkersStopped();
      } finally {
        control.release.countDown();
        solver.terminateEarly();
      }
    }

    var baselineSolver = pipelineSolver(1, 40);
    var baseline = observe(baselineSolver, false);
    assertReplay(baselineSolver.solve(problem(20, 64)));
    assertSameRun(run, baseline);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancellationAfterRefillDiscardsBothOutstandingAttempts(boolean interrupt) throws Exception {
    var solver = pipelineSolver(2, 100);
    var run = observe(solver, false);
    var coordinator = new AtomicReference<Thread>();
    try (var control = new PipelineControl(true);
        var executor = Executors.newSingleThreadExecutor()) {
      countCommittedOffspring(solver, control);
      var future =
          executor.submit(
              () -> {
                coordinator.set(Thread.currentThread());
                return solver.solve(problem(20, 64));
              });
      try {
        assertThat(control.secondEntered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(control.refillEntered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(control.offspringAtRefill).hasValue(1);
        var published = assignments(solver.getSolverScope().getBestSolution());
        if (interrupt) coordinator.get().interrupt();
        else assertThat(solver.terminateEarly()).isTrue();
        assertThat(control.interrupted.await(10, TimeUnit.SECONDS)).isTrue();

        var result = future.get(10, TimeUnit.SECONDS);
        assertReplay(result);
        assertThat(assignments(result)).isEqualTo(published);
        assertThat(run.steps).hasSize(7);
        assertThat(run.steps.stream().filter(step -> !step.seeding())).hasSize(1);
        assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(7);
        assertThat(solver.getSolverScope().getScoreCalculationCount())
            .isEqualTo(control.successfulCalls.get());
        assertThat(diagnostics(solver).submittedCount()).isEqualTo(3);
        assertThat(diagnostics(solver).consumedCount()).isEqualTo(1);
        assertThat(diagnostics(solver).closedWorkerCount()).isEqualTo(2);
        assertThat(diagnostics(solver).calculationCount())
            .isEqualTo(diagnostics(solver).transferredCalculationCount());
        assertThat(solver.isSolving()).isFalse();
        control.assertWorkersStopped();
      } finally {
        control.release.countDown();
        solver.terminateEarly();
      }
    }
  }

  @Test
  void nonAdjacentPendingDuplicateWaitsForTheFirstFreshCandidateToBeAdmitted() {
    Run baseline = null;
    for (int workers : new int[] {1, 3, 4}) {
      var solver = scriptedSolver(workers, false);
      scriptOffspring(solver, false, 0, 1, 2, 0, 3);
      var run = observe(solver, false);
      assertThat(solver.solve(problem(1000, 1)).getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(run.steps.stream().filter(step -> !step.seeding()))
          .extracting(Step::outcome)
          .containsExactly(
              GeneticAlgorithmOutcome.EVALUATED,
              GeneticAlgorithmOutcome.EVALUATED,
              GeneticAlgorithmOutcome.EVALUATED,
              GeneticAlgorithmOutcome.DUPLICATE,
              GeneticAlgorithmOutcome.EVALUATED);
      assertThat(diagnostics(solver).submittedCount()).isEqualTo(4);
      if (baseline == null) baseline = run;
      else assertSameRun(run, baseline);
    }
  }

  @Test
  void rejectedWorkspaceBehindFreshCandidatesDefersPreparationUntilItsOwnCommit() {
    Run baseline = null;
    for (int workers : new int[] {1, 3, 4}) {
      var solver = scriptedSolver(workers, true);
      scriptOffspring(solver, true, 0, 1, 2, 0, 0);
      var run = observe(solver, false);
      var result = solver.solve(problem(1000, 1));
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(assignments(result)).containsExactly("0");
      var offspring = run.steps.stream().filter(step -> !step.seeding()).toList();
      assertThat(offspring).allSatisfy(step -> assertThat(step.admitted()).isFalse());
      assertThat(offspring)
          .extracting(Step::outcome)
          .containsExactly(
              GeneticAlgorithmOutcome.EVALUATED,
              GeneticAlgorithmOutcome.EVALUATED,
              GeneticAlgorithmOutcome.EVALUATED,
              GeneticAlgorithmOutcome.EVALUATED,
              GeneticAlgorithmOutcome.NO_CHANGE);
      assertThat(diagnostics(solver).submittedCount()).isEqualTo(4);
      if (baseline == null) baseline = run;
      else assertSameRun(run, baseline);
    }
  }

  @Test
  void callbackRandomCallsCannotChangeProposalsWhenThePipelineRefills() {
    var baselineSolver = pipelineSolver(1, 125);
    var baseline = observe(baselineSolver, false);
    assertReplay(baselineSolver.solve(problem(20, 64)));
    List<Long> baselineCallbacks = null;
    for (int workers : new int[] {1, 2, 4}) {
      var solver = pipelineSolver(workers, 125);
      var run = observe(solver, true);
      assertReplay(solver.solve(problem(20, 64)));
      assertSameRun(run, baseline);
      assertThat(run.callbackRandomValues).hasSizeGreaterThan(125);
      if (baselineCallbacks == null) baselineCallbacks = run.callbackRandomValues;
      else assertThat(run.callbackRandomValues).containsExactlyElementsOf(baselineCallbacks);
    }
  }

  private static DefaultSolver<TestdataSolution> pipelineSolver(int workers, int steps) {
    return solver(
        config(phase(workers, 7, steps).withMutationRateMultiplier(64.0))
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(PipelineScoreCalculator.class))
            .withThreadFactoryClass(IndexedThreadFactory.class));
  }

  private static DefaultSolver<TestdataSolution> scriptedSolver(int workers, boolean reject) {
    return solver(
        config(phase(workers, 5, 9).withPBestRate(1.0).withTabuEntityRate(0.0))
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(
                        reject ? NegativeScoreCalculator.class : ConstantScoreCalculator.class)));
  }

  private static GeneticAlgorithmPhaseConfig phase(int workers, int population, int steps) {
    return new GeneticAlgorithmPhaseConfig()
        .withEvaluatorThreadCount(workers)
        .withPopulationSize(population)
        .withPBestRate(0.8)
        .withTabuEntityRate(0.25)
        .withMutationOperators(
            new GeneticAlgorithmMutationOperatorConfig()
                .withType(GeneticAlgorithmMutationType.CHANGE)
                .withProbability(1.0))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  /** Change only the proposal stream, once seeding has supplied an ordinary population. */
  private static void scriptOffspring(
      DefaultSolver<TestdataSolution> solver, boolean selectBestNative, int... candidates) {
    var seededValues = new HashSet<Long>();
    seededValues.add(0L);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
            if (!step.isSeeding()) return;
            seededValues.add(Long.parseLong(assignments(step.getWorkingSolution()).getFirst()));
            if (step.getStepIndex() != 3) return;
            var freshValues = new ArrayList<Long>();
            for (long value = 1; freshValues.size() < 4; value++) {
              if (!seededValues.contains(value)) freshValues.add(value);
            }
            var proposals =
                mock(
                    RandomGenerator.SplittableGenerator.class,
                    delegatesTo(new SplittableRandom(17L)));
            var nextCandidate = new AtomicInteger();
            doAnswer(
                    ignored -> {
                      int index = nextCandidate.getAndIncrement();
                      assertThat(index).isLessThan(candidates.length);
                      return freshValues.get(candidates[index]);
                    })
                .when(proposals)
                .nextLong(anyLong());
            if (selectBestNative) {
              // A full ranked prefix and its first index select the initial zero-score member;
              // every positive-valued proposal therefore loses against its native member.
              doAnswer(call -> Math.nextDown((double) call.getArgument(1)))
                  .when(proposals)
                  .nextDouble(anyDouble(), anyDouble());
              doAnswer(call -> call.getArgument(0)).when(proposals).nextInt(anyInt(), anyInt());
            }
            var random = new SplittableRandom(23L);
            var coordinator = mock(RandomGenerator.SplittableGenerator.class, delegatesTo(random));
            var firstSplit = new AtomicBoolean(true);
            doAnswer(ignored -> firstSplit.getAndSet(false) ? proposals : random.split())
                .when(coordinator)
                .split();
            ((DefaultRandomSource) scope.getWorkingRandom()).moveRandom().setDelegate(coordinator);
          }
        });
  }

  private static void countCommittedOffspring(
      DefaultSolver<TestdataSolution> solver, PipelineControl control) {
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            if (!((GeneticAlgorithmStepScope<?>) scope).isSeeding())
              control.committedOffspring.incrementAndGet();
          }
        });
  }

  private static Run observe(DefaultSolver<TestdataSolution> solver, boolean callbackRandom) {
    var run = new Run();
    var offspringStarted = new AtomicBoolean();
    solver.addEventListener(
        event -> {
          run.publications.add(assignments(event.getNewBestSolution()));
          if (callbackRandom && offspringStarted.get())
            run.callbackRandomValues.add(
                solver.getSolverScope().getWorkingRandom().moveIteratorUsage().nextLong());
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            if (!((GeneticAlgorithmStepScope<?>) scope).isSeeding()) {
              offspringStarted.set(true);
              consumeRandom(scope);
            }
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
            run.steps.add(
                new Step(
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
                    assignments(step.getWorkingSolution())));
            if (!step.isSeeding()) consumeRandom(scope);
          }

          private void consumeRandom(AbstractStepScope<TestdataSolution> scope) {
            if (!callbackRandom) return;
            var random = scope.getWorkingRandom();
            run.callbackRandomValues.add(random.moveIteratorUsage().nextLong());
            run.callbackRandomValues.add(random.factoryUsage().nextLong());
            run.callbackRandomValues.add(random.acceptorUsage().nextLong());
          }
        });
    return run;
  }

  private static GeneticAlgorithmEvaluatorPool.Diagnostics diagnostics(DefaultSolver<?> solver) {
    return ((DefaultGeneticAlgorithmPhase<?>) solver.getPhaseList().getFirst())
        .getEvaluatorDiagnostics();
  }

  private static void assertSameRun(Run actual, Run expected) {
    assertThat(actual.steps).containsExactlyElementsOf(expected.steps);
    assertThat(actual.publications).containsExactlyElementsOf(expected.publications);
  }

  private static final class Run {
    private final List<Step> steps = new ArrayList<>();
    private final List<List<String>> publications = new ArrayList<>();
    private final List<Long> callbackRandomValues = new ArrayList<>();
  }

  private record Step(
      int index,
      boolean seeding,
      long generation,
      long candidate,
      long firstParent,
      long secondParent,
      long nativeMember,
      boolean crossed,
      GeneticAlgorithmMutationType mutation,
      String mutationGroup,
      GeneticAlgorithmOutcome outcome,
      boolean admitted,
      int changed,
      InnerScore<?> beforeScore,
      InnerScore<?> bestBefore,
      InnerScore<?> candidateScore,
      InnerScore<?> workspaceScore,
      boolean bestImproved,
      List<String> assignments) {}

  private static final class PipelineControl implements AutoCloseable {
    private final boolean blockRefill;
    private final CountDownLatch secondEntered = new CountDownLatch(1);
    private final CountDownLatch refillEntered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch interrupted = new CountDownLatch(2);
    private final AtomicInteger committedOffspring = new AtomicInteger();
    private final AtomicInteger offspringAtRefill = new AtomicInteger(-1);
    private final AtomicLong successfulCalls = new AtomicLong();
    private final Map<Thread, AtomicInteger> workerCalls = new ConcurrentHashMap<>();

    private PipelineControl(boolean blockRefill) {
      this.blockRefill = blockRefill;
      assertThat(PipelineScoreCalculator.CONTROL.compareAndSet(null, this)).isTrue();
    }

    private void score() {
      var thread = Thread.currentThread();
      if (!thread.getName().startsWith("ga-pipeline-")) return;
      int call =
          workerCalls.computeIfAbsent(thread, ignored -> new AtomicInteger()).incrementAndGet();
      // Each retained worker calculates once when its initial graph is installed.
      if (thread.getName().equals("ga-pipeline-1") && call == 2) {
        secondEntered.countDown();
        await(release);
      } else if (thread.getName().equals("ga-pipeline-0") && call == 2) {
        await(secondEntered);
      } else if (thread.getName().equals("ga-pipeline-0") && call == 3) {
        offspringAtRefill.set(committedOffspring.get());
        refillEntered.countDown();
        if (blockRefill) await(release);
      }
    }

    private void await(CountDownLatch latch) {
      try {
        if (!latch.await(10, TimeUnit.SECONDS))
          throw new IllegalStateException("Evaluator pipeline test timed out.");
      } catch (InterruptedException expected) {
        interrupted.countDown();
        Thread.currentThread().interrupt();
      }
    }

    private void assertWorkersStopped() throws InterruptedException {
      assertThat(workerCalls).hasSize(2);
      for (var thread : workerCalls.keySet()) {
        thread.join(1000);
        assertThat(thread.isAlive()).as("Evaluator thread %s", thread.getName()).isFalse();
      }
    }

    @Override
    public void close() {
      assertThat(PipelineScoreCalculator.CONTROL.compareAndSet(this, null)).isTrue();
    }
  }

  public static final class IndexedThreadFactory implements ThreadFactory {
    private final AtomicInteger index = new AtomicInteger();

    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, "ga-pipeline-" + index.getAndIncrement());
    }
  }

  public static final class PipelineScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    private static final AtomicReference<PipelineControl> CONTROL = new AtomicReference<>();

    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      var control = CONTROL.get();
      if (control != null) control.score();
      int score =
          solution.getEntityList().stream()
              .mapToInt(entity -> Integer.parseInt(entity.getValue().getCode()))
              .sum();
      if (control != null) control.successfulCalls.incrementAndGet();
      return SimpleScore.of(score);
    }
  }

  public static final class ConstantScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class NegativeScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(
          -Integer.parseInt(solution.getEntityList().getFirst().getValue().getCode()));
    }
  }
}
