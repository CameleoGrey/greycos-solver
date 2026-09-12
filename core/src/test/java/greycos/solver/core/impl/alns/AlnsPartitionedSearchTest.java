package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.MockClock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Permit handoffs are coordinated by checkpoints; timeouts only detect a stuck test. */
@Timeout(30)
class AlnsPartitionedSearchTest {
  private static final Map<String, Harness> HARNESSES = new ConcurrentHashMap<>();

  @Test
  void threePartitionsMakeProgressInsideRepairWithOneRunnablePermit() throws Exception {
    try (var run = new SolverRun(Checkpoint.COOPERATIVE_REPAIR)) {
      run.start();
      awaitCheckpoint(run.harness.allRepairsEntered);
      // Each repair remains active until the other partitions have entered their own repair.
      assertThat(run.harness.partitionsInRepair).containsExactlyInAnyOrder("0", "1", "2");
      assertThat(run.harness.repairThreads).hasSize(3);
      assertThat(run.harness.repairsCompleted).hasValue(0);
      assertThat(run.future).isNotDone();
      run.harness.finishRepairs.set(true);

      var solution = run.future.get(10, TimeUnit.SECONDS);
      assertThat(solution.getScore()).isEqualTo(SimpleScore.of(3));
      assertThat(solution.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue().getCode()).isEqualTo("1"));
      assertThat(run.harness.repairs).hasValue(3);
      assertThat(run.harness.repairsCompleted).hasValue(3);
      assertThat(run.harness.closed).hasValue(3);
    }
  }

  @ParameterizedTest
  @EnumSource(Stop.class)
  void repairChecksTerminationAfterWaitingForItsPermit(Stop stop) throws Exception {
    try (var run = new SolverRun(Checkpoint.DURING_REPAIR)) {
      run.start();
      run.handOffPermit();
      // The repair has assigned 1, but is suspended inside its explicit termination checkpoint.
      assertThat(run.harness.speculativeValues).containsExactly("1");
      if (stop == Stop.EARLY) {
        assertThat(run.solver.terminateEarly()).isTrue();
      } else {
        run.clock.tick(Duration.ofSeconds(1));
      }
      run.harness.releaseContender.countDown();

      var solution = run.future.get(10, TimeUnit.SECONDS);
      assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(solution.getEntityList().getFirst().getValue().getCode()).isEqualTo("0");
      assertThat(run.harness.repairs).hasValue(1);
      assertThat(run.harness.repairsCompleted).hasValue(0);
      assertThat(run.harness.closed).hasValue(1);
      assertThat(run.harness.phaseSnapshots)
          .containsExactly(new Snapshot("0", SimpleScore.ZERO, true));
      assertThat(run.solver.isSolving()).isFalse();
    }
  }

  @Test
  void iterationGuardYieldsBeforeCheckingTheCompletedStepLimit() throws Exception {
    try (var run = new SolverRun(Checkpoint.AFTER_STEP)) {
      run.start();
      // The only permitted step has completed. The next iteration guard must still hand off
      // its permit before evaluating termination, even though no repair checkpoint follows.
      run.handOffPermit();
      assertThat(run.harness.repairsCompleted).hasValue(1);
      assertThat(run.harness.phaseSnapshots).isEmpty();
      run.harness.releaseContender.countDown();

      var solution = run.future.get(10, TimeUnit.SECONDS);
      assertThat(solution.getScore()).isEqualTo(SimpleScore.ONE);
      assertThat(run.harness.repairs).hasValue(1);
      assertThat(run.harness.closed).hasValue(1);
      assertThat(run.harness.phaseSnapshots)
          .containsExactly(new Snapshot("1", SimpleScore.ONE, true));
    }
  }

  private static void awaitCheckpoint(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(10, TimeUnit.SECONDS)).as("Expected scheduler checkpoint").isTrue();
  }

  private static void pause(Harness harness) {
    harness.checkpointReached.countDown();
    try {
      awaitCheckpoint(harness.proceedToYield);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating a scheduler checkpoint.", interrupted);
    }
  }

  private enum Checkpoint {
    COOPERATIVE_REPAIR,
    DURING_REPAIR,
    AFTER_STEP
  }

  private enum Stop {
    EARLY,
    DEADLINE
  }

  private record Snapshot(String value, SimpleScore score, boolean complete) {}

  private static final class Harness {
    final Checkpoint checkpoint;
    final CountDownLatch allRepairsEntered = new CountDownLatch(3);
    final CountDownLatch checkpointReached = new CountDownLatch(1);
    final CountDownLatch proceedToYield = new CountDownLatch(1);
    final CountDownLatch contenderAcquired = new CountDownLatch(1);
    final CountDownLatch releaseContender = new CountDownLatch(1);
    final AtomicBoolean finishRepairs = new AtomicBoolean();
    final AtomicInteger repairs = new AtomicInteger();
    final AtomicInteger repairsCompleted = new AtomicInteger();
    final AtomicInteger closed = new AtomicInteger();
    final Set<String> partitionsInRepair = ConcurrentHashMap.newKeySet();
    final Set<Thread> repairThreads = ConcurrentHashMap.newKeySet();
    final List<String> speculativeValues = new CopyOnWriteArrayList<>();
    final List<Snapshot> phaseSnapshots = new CopyOnWriteArrayList<>();

    Harness(Checkpoint checkpoint) {
      this.checkpoint = checkpoint;
    }
  }

  private static final class SolverRun implements AutoCloseable {
    final String key = UUID.randomUUID().toString();
    final MockClock clock = new MockClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    final Harness harness;
    final DefaultSolver<TestdataSolution> solver;
    final ExecutorService executor = Executors.newFixedThreadPool(2);
    final Semaphore semaphore = new Semaphore(1, true);
    Future<TestdataSolution> future;
    Future<?> contender;

    SolverRun(Checkpoint checkpoint) {
      harness = new Harness(checkpoint);
      HARNESSES.put(key, harness);
      var alns =
          new AlnsPhaseConfig()
              .withAcceptanceType(AlnsAcceptanceType.HILL_CLIMBING)
              .withDestroyOperators(
                  new AlnsDestroyOperatorConfig()
                      .withId("one")
                      .withType(AlnsDestroyOperatorType.RANDOM)
                      .withMinimumDestroyedCount(1)
                      .withMaximumDestroyedCount(1))
              .withRepairOperators(
                  new AlnsRepairOperatorConfig()
                      .withId("checkpoint")
                      .withCustomClass(CheckpointRepair.class)
                      .withCustomProperties(Map.of("key", key)))
              .withTerminationConfig(
                  new TerminationConfig()
                      .withStepCountLimit(1)
                      .withSpentLimit(Duration.ofSeconds(1)));
      var config =
          new SolverConfig(clock)
              .withSolutionClass(TestdataSolution.class)
              .withEntityClasses(TestdataEntity.class)
              .withConstraintProviderClass(NumberConstraints.class)
              .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
              .withMoveThreadCount("NONE");
      if (checkpoint == Checkpoint.COOPERATIVE_REPAIR) {
        config.withPhases(
            new PartitionedSearchPhaseConfig()
                .withSolutionPartitionerClass(ThreePartitions.class)
                .withRunnablePartThreadLimit("1")
                .withPhaseConfigs(alns));
      } else {
        config.withPhases(alns);
      }
      solver =
          (DefaultSolver<TestdataSolution>)
              SolverFactory.<TestdataSolution>create(config).buildSolver();
      if (checkpoint != Checkpoint.COOPERATIVE_REPAIR) {
        // Use the same public scope/semaphore contract as PartitionSolver, with a controlled
        // competing permit holder so cancellation can be requested during a known yield wait.
        solver.getSolverScope().setRunnableThreadSemaphore(semaphore);
      }
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              if (scope instanceof AlnsStepScope<?> && checkpoint == Checkpoint.AFTER_STEP) {
                pause(harness);
              }
            }

            @Override
            public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
              if (scope instanceof AlnsPhaseScope<?>) {
                var score = scope.calculateScore();
                harness.phaseSnapshots.add(
                    new Snapshot(
                        scope.getWorkingSolution().getEntityList().getFirst().getValue().getCode(),
                        (SimpleScore) score.raw(),
                        score.isFullyAssigned()));
              }
            }
          });
    }

    void start() {
      var values = List.of(new TestdataValue("0"), new TestdataValue("1"));
      var problem = new TestdataSolution();
      problem.setValueList(new ArrayList<>(values));
      var entities = new ArrayList<TestdataEntity>();
      int count = harness.checkpoint == Checkpoint.COOPERATIVE_REPAIR ? 3 : 1;
      for (int i = 0; i < count; i++) {
        entities.add(new TestdataEntity(Integer.toString(i), values.getFirst()));
      }
      problem.setEntityList(entities);
      future =
          executor.submit(
              () -> {
                var scope = solver.getSolverScope();
                scope.initializeYielding();
                try {
                  return solver.solve(problem);
                } finally {
                  scope.destroyYielding();
                }
              });
    }

    void handOffPermit() throws InterruptedException {
      awaitCheckpoint(harness.checkpointReached);
      contender =
          executor.submit(
              () -> {
                semaphore.acquire();
                try {
                  harness.contenderAcquired.countDown();
                  awaitCheckpoint(harness.releaseContender);
                } finally {
                  semaphore.release();
                }
                return null;
              });
      await().atMost(Duration.ofSeconds(10)).until(() -> semaphore.getQueueLength() == 1);
      harness.proceedToYield.countDown();
      awaitCheckpoint(harness.contenderAcquired);
      await().atMost(Duration.ofSeconds(10)).until(() -> semaphore.getQueueLength() == 1);
      assertThat(future).as("Solver is waiting to reacquire the permit").isNotDone();
    }

    @Override
    public void close() throws Exception {
      harness.finishRepairs.set(true);
      harness.proceedToYield.countDown();
      harness.releaseContender.countDown();
      solver.terminateEarly();
      executor.shutdown();
      try {
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        if (contender != null) contender.get(10, TimeUnit.SECONDS);
      } finally {
        executor.shutdownNow();
        HARNESSES.remove(key);
      }
    }
  }

  public static final class CheckpointRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore>, AutoCloseable {
    private Harness harness;

    public void setKey(String key) {
      harness = Objects.requireNonNull(HARNESSES.get(key));
    }

    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      harness.repairs.incrementAndGet();
      harness.repairThreads.add(Thread.currentThread());
      var assignment =
          context.assignments(pending.getFirst()).stream()
              .filter(candidate -> "1".equals(((TestdataValue) candidate.value()).getCode()))
              .findFirst()
              .orElseThrow();
      context.assign(assignment);
      harness.speculativeValues.add("1");
      if (harness.checkpoint == Checkpoint.COOPERATIVE_REPAIR) {
        assertThat(
                harness.partitionsInRepair.add(
                    context.workingSolution().getEntityList().getFirst().getCode()))
            .isTrue();
        harness.allRepairsEntered.countDown();
        while (!harness.finishRepairs.get()) {
          context.checkTermination();
        }
      } else if (harness.checkpoint == Checkpoint.DURING_REPAIR) {
        pause(harness);
        context.checkTermination();
      }
      harness.repairsCompleted.incrementAndGet();
      return true;
    }

    @Override
    public void close() {
      harness.closed.incrementAndGet();
    }
  }

  public static final class ThreePartitions implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> scoreDirector, Integer runnablePartThreadLimit) {
      assertThat(runnablePartThreadLimit).isEqualTo(1);
      var solution = scoreDirector.getWorkingSolution();
      var parts = new ArrayList<TestdataSolution>();
      for (var entity : solution.getEntityList()) {
        var part = new TestdataSolution();
        part.setValueList(new ArrayList<>(solution.getValueList()));
        part.setEntityList(
            new ArrayList<>(List.of(new TestdataEntity(entity.getCode(), entity.getValue()))));
        parts.add(part);
      }
      assertThat(parts).hasSize(3);
      return parts;
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
}
