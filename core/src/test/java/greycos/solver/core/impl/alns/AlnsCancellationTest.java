package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Real solver lifecycle tests; every concurrent transition is coordinated by a latch. */
@Timeout(30)
class AlnsCancellationTest {
  private static final Map<String, Harness> HARNESSES = new ConcurrentHashMap<>();

  @Test
  void terminateEarlyDuringRepairRestoresIncumbentAndCompletesTheFutureNormally() throws Exception {
    try (var run = new SolverRun(Mode.BLOCK_FIRST, "NONE")) {
      run.start();
      await(run.harness.repairEntered);
      assertThat(run.harness.appliedValues).containsExactly("2");
      assertThat(run.solver.terminateEarly()).isTrue();
      run.harness.releaseRepair.countDown();
      var result = run.future.get(10, TimeUnit.SECONDS);
      assertThat(result.getEntityList().getFirst().getValue().getCode()).isEqualTo("0");
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(run.harness.phaseSnapshots)
          .containsExactly(new Snapshot(List.of("0"), SimpleScore.ZERO, true));
      assertThat(run.harness.closed).hasValue(1);
      assertThat(run.harness.phaseStarts).hasValue(1);
      assertThat(run.solver.isSolving()).isFalse();
    }
  }

  @Test
  void problemChangeQueuedDuringRepairRollsBackThenRestartsWithTheChangedProblem()
      throws Exception {
    try (var run = new SolverRun(Mode.BLOCK_FIRST, "NONE")) {
      run.start();
      await(run.harness.repairEntered);
      run.solver.addProblemChange(
          (workingSolution, changeDirector) -> {
            // The speculative value 2 must not escape into the problem-change callback.
            assertThat(workingSolution.getEntityList().getFirst().getValue().getCode())
                .isEqualTo("0");
            var value = workingSolution.getValueList().get(1);
            changeDirector.changeVariable(
                workingSolution.getEntityList().getFirst(),
                "value",
                entity -> entity.setValue(value));
            run.harness.problemChangeApplied.countDown();
          });
      run.harness.releaseRepair.countDown();
      await(run.harness.problemChangeApplied);
      var result = run.future.get(10, TimeUnit.SECONDS);
      assertThat(run.solver.isEveryProblemChangeProcessed()).isTrue();
      assertThat(result.getEntityList().getFirst().getValue().getCode()).isEqualTo("2");
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(2));
      assertThat(run.harness.phaseStarts).hasValue(2);
      assertThat(run.harness.repairs).hasValue(2);
      assertThat(run.harness.closed).hasValue(2);
      assertThat(run.harness.phaseSnapshots)
          .containsExactly(
              new Snapshot(List.of("0"), SimpleScore.ZERO, true),
              new Snapshot(List.of("2"), SimpleScore.of(2), true));
      assertThat(run.harness.incumbentsAtRepairEntry)
          .containsExactly(SimpleScore.ZERO, SimpleScore.ONE);
    }
  }

  @Test
  void repairFailureIsPreservedWhileCandidateRollsBackAndCleanupFailureIsSuppressed()
      throws Exception {
    try (var run = new SolverRun(Mode.FAIL_REPAIR, "NONE")) {
      run.harness.cleanupFailure =
          new IllegalStateException("Intentional operator cleanup failure.");
      run.start();
      assertThatThrownBy(() -> run.future.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .satisfies(
              failure -> {
                assertThat(failure.getCause()).isSameAs(run.harness.primaryFailure);
                assertThat(failure.getCause().getSuppressed())
                    .anySatisfy(
                        suppressed ->
                            assertThat(suppressed.getCause()).isSameAs(run.harness.cleanupFailure));
              });
      assertThat(run.harness.closed).hasValue(1);
      assertThat(run.harness.phaseSnapshots)
          .containsExactly(new Snapshot(List.of("0"), SimpleScore.ZERO, true));
    }
  }

  @Test
  void bestSolutionObserverFailurePreservesCommittedCandidateAndClosesTheOperator()
      throws Exception {
    try (var run = new SolverRun(Mode.COMPLETE, "NONE")) {
      run.harness.cleanupFailure =
          new IllegalStateException("Intentional observer-path cleanup failure.");
      run.solver.addEventListener(
          event -> {
            if (event.getProducerId().equals(EventProducerId.alns(0))) {
              assertThat(event.getNewBestSolution().getScore()).isEqualTo(SimpleScore.of(2));
              throw run.harness.primaryFailure;
            }
          });
      run.start();
      assertThatThrownBy(() -> run.future.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .satisfies(
              failure -> {
                assertThat(failure.getCause()).isSameAs(run.harness.primaryFailure);
                assertThat(failure.getCause().getSuppressed())
                    .anySatisfy(
                        suppressed ->
                            assertThat(suppressed.getCause()).isSameAs(run.harness.cleanupFailure));
              });
      assertThat(run.harness.closed).hasValue(1);
      assertThat(run.harness.phaseSnapshots)
          .containsExactly(new Snapshot(List.of("2"), SimpleScore.of(2), true));
    }
  }

  @Test
  void phaseEndedObserverFailureDoesNotReplaceAnExistingRepairFailure() throws Exception {
    try (var run = new SolverRun(Mode.FAIL_REPAIR, "NONE")) {
      var phaseFailure = new IllegalStateException("Intentional phase-ended observer failure.");
      run.solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
              if (scope instanceof AlnsPhaseScope<?>) throw phaseFailure;
            }
          });
      run.start();
      assertThatThrownBy(() -> run.future.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .satisfies(
              failure -> {
                assertThat(failure.getCause()).isSameAs(run.harness.primaryFailure);
                assertThat(failure.getCause().getSuppressed()).contains(phaseFailure);
              });
      assertThat(run.harness.closed).hasValue(1);
      assertThat(run.harness.phaseSnapshots)
          .containsExactly(new Snapshot(List.of("0"), SimpleScore.ZERO, true));
    }
  }

  @Test
  void legacyCustomRepairWithoutBatchesDoesNotCreateMoveWorkers() throws Exception {
    CountingThreadFactory.created.set(0);
    try (var run = new SolverRun(Mode.COMPLETE, "2")) {
      run.start();
      var result = run.future.get(10, TimeUnit.SECONDS);
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(2));
      assertThat(CountingThreadFactory.created).hasValue(0);
      assertThat(run.harness.repairThreads).hasSize(1).containsExactly(run.harness.solverThread);
      assertThat(run.harness.repairs).hasValue(1);
      assertThat(run.harness.closed).hasValue(1);
    }
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(10, TimeUnit.SECONDS)).as("Expected lifecycle checkpoint").isTrue();
  }

  private enum Mode {
    BLOCK_FIRST,
    COMPLETE,
    FAIL_REPAIR
  }

  private record Snapshot(List<String> values, SimpleScore score, boolean complete) {}

  private static final class Harness {
    final Mode mode;
    final CountDownLatch repairEntered = new CountDownLatch(1);
    final CountDownLatch releaseRepair = new CountDownLatch(1);
    final CountDownLatch problemChangeApplied = new CountDownLatch(1);
    final AtomicInteger repairs = new AtomicInteger();
    final AtomicInteger closed = new AtomicInteger();
    final AtomicInteger phaseStarts = new AtomicInteger();
    final Set<Thread> repairThreads = ConcurrentHashMap.newKeySet();
    final List<String> appliedValues = new CopyOnWriteArrayList<>();
    final List<SimpleScore> incumbentsAtRepairEntry = new CopyOnWriteArrayList<>();
    final List<Snapshot> phaseSnapshots = new CopyOnWriteArrayList<>();
    final IllegalStateException primaryFailure =
        new IllegalStateException("Intentional callback failure.");
    volatile IllegalStateException cleanupFailure;
    volatile Thread solverThread;

    Harness(Mode mode) {
      this.mode = mode;
    }
  }

  private static final class SolverRun implements AutoCloseable {
    final String key = UUID.randomUUID().toString();
    final Harness harness;
    final DefaultSolver<TestdataSolution> solver;
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<TestdataSolution> future;

    SolverRun(Mode mode, String moveThreads) {
      harness = new Harness(mode);
      HARNESSES.put(key, harness);
      var config =
          new SolverConfig()
              .withSolutionClass(TestdataSolution.class)
              .withEntityClasses(TestdataEntity.class)
              .withConstraintProviderClass(NumberConstraints.class)
              .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
              .withMoveThreadCount(moveThreads)
              .withThreadFactoryClass(CountingThreadFactory.class)
              .withPhases(
                  new AlnsPhaseConfig()
                      .withAcceptanceType(AlnsAcceptanceType.HILL_CLIMBING)
                      .withDestroyOperators(
                          new AlnsDestroyOperatorConfig()
                              .withId("one")
                              .withCustomClass(FirstDestroy.class)
                              .withMinimumDestroyedCount(1)
                              .withMaximumDestroyedCount(1))
                      .withRepairOperators(
                          new AlnsRepairOperatorConfig()
                              .withId("controlled")
                              .withCustomClass(ControlledRepair.class)
                              .withCustomProperties(Map.of("key", key)))
                      .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
      solver =
          (DefaultSolver<TestdataSolution>)
              SolverFactory.<TestdataSolution>create(config).buildSolver();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
              if (scope instanceof AlnsPhaseScope<?>) harness.phaseStarts.incrementAndGet();
            }

            @Override
            public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
              if (scope instanceof AlnsPhaseScope<?>) {
                var score = scope.getScoreDirector().calculateScore();
                var values =
                    scope.getWorkingSolution().getEntityList().stream()
                        .map(
                            entity ->
                                entity.getValue() == null
                                    ? "unassigned"
                                    : entity.getValue().getCode())
                        .toList();
                harness.phaseSnapshots.add(
                    new Snapshot(values, (SimpleScore) score.raw(), score.isFullyAssigned()));
              }
            }
          });
    }

    void start() {
      var values = List.of(new TestdataValue("0"), new TestdataValue("1"), new TestdataValue("2"));
      var problem = new TestdataSolution();
      problem.setValueList(new ArrayList<>(values));
      problem.setEntityList(
          new ArrayList<>(List.of(new TestdataEntity("entity", values.getFirst()))));
      future =
          executor.submit(
              () -> {
                harness.solverThread = Thread.currentThread();
                return solver.solve(problem);
              });
    }

    @Override
    public void close() throws InterruptedException {
      harness.releaseRepair.countDown();
      solver.terminateEarly();
      executor.shutdown();
      try {
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
            .as("Solver thread stopped")
            .isTrue();
      } finally {
        executor.shutdownNow();
        HARNESSES.remove(key);
      }
    }
  }

  public static final class ControlledRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore>, AutoCloseable {
    private Harness harness;

    public void setKey(String key) {
      harness = Objects.requireNonNull(HARNESSES.get(key));
    }

    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      harness.repairThreads.add(Thread.currentThread());
      int call = harness.repairs.incrementAndGet();
      harness.incumbentsAtRepairEntry.add(context.incumbentScore());
      var assignment =
          context.assignments(pending.getFirst()).stream()
              .filter(candidate -> "2".equals(((TestdataValue) candidate.value()).getCode()))
              .findFirst()
              .orElseThrow();
      context.assign(assignment);
      harness.appliedValues.add("2");
      if (harness.mode == Mode.FAIL_REPAIR) throw harness.primaryFailure;
      if (harness.mode == Mode.BLOCK_FIRST && call == 1) {
        harness.repairEntered.countDown();
        try {
          if (!harness.releaseRepair.await(10, TimeUnit.SECONDS))
            throw new AssertionError("Repair release timed out.");
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
        context.checkTermination();
      }
      return true;
    }

    @Override
    public void close() {
      harness.closed.incrementAndGet();
      if (harness.cleanupFailure != null) throw harness.cleanupFailure;
    }
  }

  public static final class FirstDestroy
      implements AlnsDestroyOperator<TestdataSolution, SimpleScore> {
    @Override
    public List<AlnsTarget<TestdataSolution>> select(
        AlnsContext<TestdataSolution, SimpleScore> context, int size) {
      return List.of(context.targets().getFirst());
    }
  }

  public static final class NumberConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .reward(SimpleScore.ONE, entity -> Long.parseLong(entity.getValue().getCode()))
            .asConstraint("Numeric value")
      };
    }
  }

  public static final class CountingThreadFactory implements ThreadFactory {
    static final AtomicInteger created = new AtomicInteger();

    @Override
    public Thread newThread(Runnable task) {
      created.incrementAndGet();
      return Thread.ofPlatform().name("unexpected-alns-worker").unstarted(task);
    }
  }
}
