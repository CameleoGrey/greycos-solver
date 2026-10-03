package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverJob;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.api.solver.SolverStatus;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.SolverManagerConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class SolverLifecycleRegressionTest {

  @Test
  void reusedSolverStartsANewIdleBudgetAfterPhaseInitialization() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withMoveThreadCount("NONE")
            .withPhases(
                new greycos.solver.core.config.localsearch.LocalSearchPhaseConfig()
                    .withTerminationConfig(
                        new greycos.solver.core.config.solver.termination.TerminationConfig()
                            .withUnimprovedSpentLimit(java.time.Duration.ofMillis(100))));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(
              greycos.solver.core.impl.phase.scope.AbstractPhaseScope<TestdataSolution> scope) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
          }
        });
    var input = PlannerTestUtils.generateTestdataSolution("idle", 4);
    input.getEntityList().forEach(entity -> entity.setValue(input.getValueList().getFirst()));
    for (int run = 0; run < 2; run++) {
      solver.solve(input);
      assertThat(solver.getMoveEvaluationCount())
          .as("search after initialization on run %s", run)
          .isPositive();
    }
  }

  @Test
  void addingEntityMustUpdateItsDeclarativeShadowBeforeNextProblemChange() {
    var solver = shadowSolver();
    var queued = new java.util.concurrent.atomic.AtomicBoolean();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<ShadowSolution> scope) {
            if (queued.getAndSet(true)) return;
            solver.addProblemChanges(
                List.of(
                    (solution, director) ->
                        director.addEntity(new ShadowEntity("new", "aa"), solution.entities::add),
                    (solution, director) ->
                        assertThat(solution.entities.getLast().length)
                            .as("automatic shadow update promised between problem changes")
                            .isEqualTo(2)));
          }
        });
    solver.solve(shadowProblem());
  }

  @Test
  void addingInitializedEntityMustNotPublishStaleDeclarativeShadowAndScore() {
    var solver = shadowSolver();
    var queued = new java.util.concurrent.atomic.AtomicBoolean();
    var events = new ArrayList<ShadowSolution>();
    var publishedScores = new ArrayList<SimpleScore>();
    solver.addEventListener(
        event -> {
          if (event.getProducerId().equals(EventProducerId.problemChange())) {
            events.add(event.getNewBestSolution());
            publishedScores.add(event.getNewBestSolution().score);
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<ShadowSolution> scope) {
            if (!queued.getAndSet(true)) {
              solver.addProblemChange(
                  (solution, director) ->
                      director.addEntity(new ShadowEntity("new", "aa"), solution.entities::add));
            }
          }
        });
    var result = solver.solve(shadowProblem());
    assertThat(result.entities.getLast().length).isEqualTo(2);
    assertThat(events).hasSize(1);
    assertThat(publishedScores).containsExactly(SimpleScore.of(4));
    assertThat(events.getFirst().score)
        .as("score replay from two initialized aa assignments")
        .isEqualTo(SimpleScore.of(4));
    assertThat(events.getFirst().entities.getLast().length).isEqualTo(2);
  }

  private static DefaultSolver<ShadowSolution> shadowSolver() {
    return (DefaultSolver<ShadowSolution>)
        SolverFactory.<ShadowSolution>create(
                new SolverConfig()
                    .withSolutionClass(ShadowSolution.class)
                    .withEntityClasses(ShadowEntity.class)
                    .withEasyScoreCalculatorClass(ShadowScore.class)
                    .withMoveThreadCount("NONE")
                    .withPhases(new ConstructionHeuristicPhaseConfig()))
            .buildSolver();
  }

  private static ShadowSolution shadowProblem() {
    var solution = new ShadowSolution();
    solution.entities.add(new ShadowEntity("old", "aa"));
    return solution;
  }

  @PlanningSolution
  public static class ShadowSolution {
    @PlanningEntityCollectionProperty public List<ShadowEntity> entities = new ArrayList<>();

    @ValueRangeProvider(id = "values")
    public List<String> values = List.of("aa", "bbb");

    @PlanningScore public SimpleScore score;

    public ShadowSolution() {}
  }

  @PlanningEntity
  public static class ShadowEntity {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public String value;

    @ShadowVariable(supplierName = "length")
    public Integer length;

    public ShadowEntity() {}

    ShadowEntity(String id, String value) {
      this.id = id;
      this.value = value;
    }

    @ShadowSources("value")
    public Integer length() {
      return value == null ? 0 : value.length();
    }
  }

  public static class ShadowScore implements EasyScoreCalculator<ShadowSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(ShadowSolution solution) {
      return SimpleScore.of(
          solution.entities.stream()
              .mapToInt(entity -> entity.length == null ? 0 : entity.length)
              .sum());
    }
  }

  @Test
  void problemFinderCanCancelItsDaemonJobBeforePhaseWorkStarts() throws Exception {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withDaemon(true)
            .withMoveThreadCount("NONE");
    var jobAssigned = new CountDownLatch(1);
    var terminationReturned = new CountDownLatch(1);
    var jobFinished = new CountDownLatch(1);
    var jobReference = new AtomicReference<SolverJob<TestdataSolution>>();
    var solverThread = new AtomicReference<Thread>();
    var result = new AtomicReference<TestdataSolution>();
    var failure = new AtomicReference<Throwable>();
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    try (var manager =
        SolverManager.<TestdataSolution>create(
            config, new SolverManagerConfig().withParallelSolverCount("1"))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("finder-cancellation")
              .withProblemFinder(
                  id -> {
                    solverThread.set(Thread.currentThread());
                    await(jobAssigned);
                    jobReference.get().terminateEarly();
                    terminationReturned.countDown();
                    return PlannerTestUtils.generateTestdataSolution("finder-cancellation", 4);
                  })
              .withExceptionHandler((id, error) -> errors.add(error))
              .run();
      jobReference.set(job);
      jobAssigned.countDown();
      var waiter =
          Thread.ofPlatform()
              .start(
                  () -> {
                    try {
                      result.set(job.getFinalBestSolution());
                    } catch (Throwable throwable) {
                      failure.set(throwable);
                    } finally {
                      jobFinished.countDown();
                    }
                  });
      try {
        assertThat(terminationReturned.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(jobFinished.await(2, TimeUnit.SECONDS))
            .as("startup must preserve cancellation requested by the problem finder")
            .isTrue();
        assertThat(failure.get()).isNull();
        assertThat(errors).isEmpty();
        assertThat(job.isTerminatedEarly()).isTrue();
        assertThat(job.getMoveEvaluationCount()).isZero();
        assertThat(result.get().getEntityList())
            .allSatisfy(entity -> assertThat(entity.getValue()).isNull());
      } finally {
        // On regression, daemon waiting must not outlive this bounded test.
        if (jobFinished.getCount() != 0L && solverThread.get() != null) {
          solverThread.get().interrupt();
        }
        waiter.join(5000);
      }
    }
  }

  @Test
  void problemChangeCanTerminateItsOwnJobWithoutWaitingForItself() throws Exception {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withDaemon(true)
            .withMoveThreadCount("NONE");
    var changeEntered = new CountDownLatch(1);
    var terminationReturned = new CountDownLatch(1);
    var solverThread = new AtomicReference<Thread>();
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    try (var manager =
        SolverManager.<TestdataSolution>create(
            config, new SolverManagerConfig().withParallelSolverCount("1"))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("self-termination")
              .withProblem(PlannerTestUtils.generateTestdataSolution("self-termination", 4))
              .withExceptionHandler((id, error) -> errors.add(error))
              .run();
      try {
        manager.addProblemChange(
            "self-termination",
            (solution, director) -> {
              solverThread.set(Thread.currentThread());
              changeEntered.countDown();
              job.terminateEarly();
              terminationReturned.countDown();
            });
        assertThat(changeEntered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(terminationReturned.await(2, TimeUnit.SECONDS))
            .as("the solver thread must signal termination without awaiting its own completion")
            .isTrue();
        assertThat(job.getFinalBestSolution()).isNotNull();
        assertThat(job.isTerminatedEarly()).isTrue();
        assertThat(errors).isEmpty();
      } finally {
        // On regression, interrupt the self-wait so this test never waits for the one-minute limit.
        if (terminationReturned.getCount() != 0L && solverThread.get() != null) {
          solverThread.get().interrupt();
        }
      }
    }
  }

  @Test
  void terminateDuringProblemChangeMustNotMakeRestartUnlockAnotherThreadLock() throws Exception {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withDaemon(true)
            .withMoveThreadCount("NONE");
    var changeEntered = new CountDownLatch(1);
    var releaseChange = new CountDownLatch(1);
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    try (var manager =
        SolverManager.<TestdataSolution>create(
            config, new SolverManagerConfig().withParallelSolverCount("1"))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("restart")
              .withProblem(PlannerTestUtils.generateTestdataSolution("restart", 4))
              .withExceptionHandler((id, error) -> errors.add(error))
              .run();
      manager.addProblemChange(
          "restart",
          (solution, director) -> {
            changeEntered.countDown();
            await(releaseChange);
            director.addProblemFact(new TestdataValue("added"), solution.getValueList()::add);
          });
      assertThat(changeEntered.await(5, TimeUnit.SECONDS)).isTrue();
      var terminator = Thread.ofPlatform().start(job::terminateEarly);
      try {
        awaitCondition(job::isTerminatedEarly);
        releaseChange.countDown();
        terminator.join(5000);
        assertThat(terminator.isAlive()).isFalse();
        assertThat(errors)
            .as("termination should return a solution rather than fail the solver")
            .isEmpty();
        assertThat(job.getFinalBestSolution().getValueList()).hasSize(5);
      } finally {
        releaseChange.countDown();
        terminator.join(5000);
      }
    }
  }

  @Test
  void changeRegisteredAfterProcessedCheckMustNotCompleteAgainstOldSolution() throws Exception {
    var solver =
        SolverFactory.<TestdataSolution>create(
                PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
                    .withMoveThreadCount("NONE"))
            .buildSolver();
    var holder = new BestSolutionHolder<TestdataSolution>();
    var oldSolution = PlannerTestUtils.generateTestdataSolution("old", 2);
    var processedChecked = new CountDownLatch(1);
    var releasePublication = new CountDownLatch(1);
    var publisher =
        Thread.ofPlatform()
            .start(
                () ->
                    holder.set(
                        oldSolution,
                        EventProducerId.solvingStarted(),
                        () -> {
                          var processed = solver.isEveryProblemChangeProcessed();
                          processedChecked.countDown();
                          await(releasePublication);
                          return processed;
                        }));
    var registeredChange = new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
    Thread registrar = null;
    try {
      assertThat(processedChecked.await(5, TimeUnit.SECONDS)).isTrue();
      registrar =
          Thread.ofPlatform()
              .start(
                  () ->
                      registeredChange.set(
                          holder.addProblemChange(
                              solver,
                              List.of(
                                  (solution, director) ->
                                      director.addProblemFact(
                                          new TestdataValue("new"),
                                          solution.getValueList()::add)))));
      var registrationThread = registrar;
      awaitCondition(
          () ->
              registrationThread.getState() == Thread.State.BLOCKED
                  || registeredChange.get() != null);
      assertThat(registeredChange.get())
          .as("registration must wait for atomic publication")
          .isNull();
      releasePublication.countDown();
      publisher.join(5000);
      registrar.join(5000);
      assertThat(publisher.isAlive()).isFalse();
      assertThat(registrar.isAlive()).isFalse();
      assertThat(solver.isEveryProblemChangeProcessed()).isFalse();
      var delivered = holder.take();
      assertThat(delivered.getBestSolution().getValueList()).hasSize(2);
      delivered.completeProblemChanges();
      assertThat(registeredChange.get())
          .as("the change is still queued and absent from the delivered solution")
          .isNotCompleted();
    } finally {
      releasePublication.countDown();
      publisher.join(5000);
      if (registrar != null) registrar.join(5000);
      ((DefaultSolver<TestdataSolution>) solver).getSolverScope().getScoreDirector().close();
    }
  }

  @Test
  void eventConsumerCanTerminateDuringProblemChangeWithoutWaitingForItsOwnReturn()
      throws Exception {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withDaemon(true)
            .withMoveThreadCount("NONE");
    var changeEntered = new CountDownLatch(1);
    var releaseChange = new CountDownLatch(1);
    var jobAssigned = new CountDownLatch(1);
    var callbackReturned = new CountDownLatch(1);
    var jobRef = new AtomicReference<SolverJob<TestdataSolution>>();
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    try (var manager =
        SolverManager.<TestdataSolution>create(
            config, new SolverManagerConfig().withParallelSolverCount("1"))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("consumer-restart")
              .withProblem(PlannerTestUtils.generateTestdataSolution("consumer-restart", 4))
              .withSolverJobStartedEventConsumer(
                  event -> {
                    await(jobAssigned);
                    await(changeEntered);
                    jobRef.get().terminateEarly();
                    callbackReturned.countDown();
                  })
              .withExceptionHandler((id, error) -> errors.add(error))
              .run();
      jobRef.set(job);
      jobAssigned.countDown();
      try {
        manager.addProblemChange(
            "consumer-restart",
            (solution, director) -> {
              changeEntered.countDown();
              await(releaseChange);
            });
        assertThat(callbackReturned.await(5, TimeUnit.SECONDS)).isTrue();
        releaseChange.countDown();
        assertThat(job.getFinalBestSolution()).isNotNull();
        assertThat(errors).isEmpty();
      } finally {
        releaseChange.countDown();
      }
    }
  }

  @Test
  void jobStartedConsumerMustReceiveInitialAssignmentsEvenWhenSlow() throws Exception {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withMoveThreadCount("NONE");
    var input = PlannerTestUtils.generateTestdataSolution("start", 4);
    var jobRef = new AtomicReference<SolverJob<TestdataSolution>>();
    var jobAssigned = new CountDownLatch(1);
    var initialEventSolution = new AtomicReference<TestdataSolution>();
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    try (var manager =
        SolverManager.<TestdataSolution>create(
            config, new SolverManagerConfig().withParallelSolverCount("1"))) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("start")
              .withProblem(input)
              .withSolverJobStartedEventConsumer(
                  event -> {
                    await(jobAssigned);
                    awaitCondition(
                        () -> jobRef.get().getSolverStatus() == SolverStatus.NOT_SOLVING);
                    initialEventSolution.set(event.solution());
                  })
              .withExceptionHandler((id, error) -> errors.add(error))
              .run();
      jobRef.set(job);
      jobAssigned.countDown();
      job.getFinalBestSolution();
      assertThat(errors).isEmpty();
      assertThat(input.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue()).isNull());
      assertThat(initialEventSolution.get().getEntityList())
          .as("SolverJobStartedEvent promises the initial solution")
          .allSatisfy(entity -> assertThat(entity.getValue()).isNull());
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static void awaitCondition(java.util.function.BooleanSupplier condition) {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) throw new AssertionError("Condition timed out");
      Thread.onSpinWait();
    }
  }
}
