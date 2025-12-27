package ai.greycos.solver.core.api.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import ai.greycos.solver.core.api.solver.event.FinalBestSolutionEvent;
import ai.greycos.solver.core.api.solver.event.NewBestSolutionEvent;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.testdomain.TestdataConstraintProvider;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for the best solution throttling feature.
 *
 * <p>Tests verify:
 *
 * <ul>
 *   <li>End-to-end throttling with SolverManager
 *   <li>Rapid improvement scenarios
 *   <li>Final solution delivery to both consumers
 *   <li>Multiple concurrent jobs with different throttle rates
 *   <li>Throttling with problem changes
 * </ul>
 */
class SolverManagerThrottlingTest {

  private static final Function<Long, TestdataSolution> DEFAULT_PROBLEM_FINDER =
      problemId -> PlannerTestUtils.generateTestdataSolution("Generated solution " + problemId);

  @Test
  @Timeout(60)
  void endToEnd_fullSolverJobWithThrottling() throws ExecutionException, InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var intermediateEvents =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());
      var finalEvents =
          Collections.synchronizedList(new ArrayList<FinalBestSolutionEvent<TestdataSolution>>());

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(DEFAULT_PROBLEM_FINDER)
              .withThrottledBestSolutionEventConsumer(
                  event -> intermediateEvents.add(event), Duration.ofMillis(500))
              .withFinalBestSolutionEventConsumer(event -> finalEvents.add(event))
              .run();

      var finalSolution = solverJob.getFinalBestSolution();

      // Verify throttling worked - should have fewer intermediate events than without throttling
      assertThat(intermediateEvents).isNotEmpty();
      assertThat(intermediateEvents.size()).isLessThan(10); // Reasonable upper bound

      // Verify final solution was delivered to both consumers
      assertThat(finalEvents).hasSize(1);
      assertThat(finalEvents.get(0).solution()).isNotNull();
      assertThat(finalSolution).isNotNull();
    }
  }

  @Test
  @Timeout(60)
  void rapidImprovement_multipleEventsWithinInterval_onlyLastDelivered()
      throws ExecutionException, InterruptedException {
    // Create a phase that generates many best solution events rapidly
    var rapidImprovementPhase =
        new CustomPhaseConfig()
            .withCustomPhaseCommands(
                (scoreDirector, isPhaseTerminated) -> {
                  var solution = (TestdataSolution) scoreDirector.getWorkingSolution();
                  var entities = solution.getEntityList();
                  var values = solution.getValueList();

                  // Generate rapid improvements by assigning values
                  for (int i = 0; i < Math.min(entities.size(), values.size()); i++) {
                    var entity = entities.get(i);
                    scoreDirector.beforeVariableChanged(entity, "value");
                    entity.setValue(values.get(i));
                    scoreDirector.afterVariableChanged(entity, "value");
                    scoreDirector.triggerVariableListeners();
                  }
                });

    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(TestdataConstraintProvider.class)
            .withPhases(rapidImprovementPhase)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(100)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var intermediateEvents =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());
      var finalEvent = new AtomicReference<FinalBestSolutionEvent<TestdataSolution>>();

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(
                  id -> PlannerTestUtils.generateTestdataSolution("s1", 10)) // Larger problem
              .withThrottledBestSolutionEventConsumer(
                  event -> intermediateEvents.add(event), Duration.ofMillis(200))
              .withFinalBestSolutionEventConsumer(finalEvent::set)
              .run();

      var finalSolution = solverJob.getFinalBestSolution();

      // Should have very few intermediate events due to throttling
      assertThat(intermediateEvents).hasSizeLessThan(5);

      // Final solution should be delivered
      assertThat(finalEvent.get()).isNotNull();
      assertThat(finalEvent.get().solution()).isNotNull();
      assertThat(finalSolution).isNotNull();
    }
  }

  @Test
  @Timeout(60)
  void finalSolution_bothConsumersReceiveFinalSolution()
      throws ExecutionException, InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withPhases(new ConstructionHeuristicPhaseConfig())
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(500)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var intermediateEvents =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());
      var finalEvents =
          Collections.synchronizedList(new ArrayList<FinalBestSolutionEvent<TestdataSolution>>());
      var finalSolutionRef = new AtomicReference<TestdataSolution>();

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(DEFAULT_PROBLEM_FINDER)
              .withThrottledBestSolutionEventConsumer(
                  event -> {
                    intermediateEvents.add(event);
                    finalSolutionRef.set(event.solution());
                  },
                  Duration.ofMillis(100))
              .withFinalBestSolutionEventConsumer(event -> finalEvents.add(event))
              .run();

      var finalSolution = solverJob.getFinalBestSolution();

      // Both consumers should receive the final solution
      assertThat(intermediateEvents).isNotEmpty();
      assertThat(finalEvents).hasSize(1);

      // The last intermediate event should match the final solution
      var lastIntermediate = intermediateEvents.get(intermediateEvents.size() - 1);
      assertThat(lastIntermediate.solution()).isEqualTo(finalSolution);
      assertThat(finalEvents.get(0).solution()).isEqualTo(finalSolution);
    }
  }

  @Test
  @Timeout(60)
  void multipleJobs_concurrentJobsWithDifferentThrottleRates()
      throws ExecutionException, InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(500)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var job1Events =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());
      var job2Events =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());
      var job3Events =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());

      var job1 =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(id -> PlannerTestUtils.generateTestdataSolution("s1"))
              .withThrottledBestSolutionEventConsumer(
                  event -> job1Events.add(event), Duration.ofMillis(100)) // Fast throttle
              .run();

      var job2 =
          solverManager
              .solveBuilder()
              .withProblemId(2L)
              .withProblemFinder(id -> PlannerTestUtils.generateTestdataSolution("s2"))
              .withThrottledBestSolutionEventConsumer(
                  event -> job2Events.add(event), Duration.ofMillis(300)) // Medium throttle
              .run();

      var job3 =
          solverManager
              .solveBuilder()
              .withProblemId(3L)
              .withProblemFinder(id -> PlannerTestUtils.generateTestdataSolution("s3"))
              .withThrottledBestSolutionEventConsumer(
                  event -> job3Events.add(event), Duration.ofMillis(500)) // Slow throttle
              .run();

      // Wait for all jobs to complete
      var solution1 = job1.getFinalBestSolution();
      var solution2 = job2.getFinalBestSolution();
      var solution3 = job3.getFinalBestSolution();

      // All jobs should complete successfully
      assertThat(solution1).isNotNull();
      assertThat(solution2).isNotNull();
      assertThat(solution3).isNotNull();

      // Different throttle rates should result in different numbers of events
      // (though this is probabilistic, the trend should hold)
      assertThat(job1Events.size()).isGreaterThanOrEqualTo(job2Events.size());
      assertThat(job2Events.size()).isGreaterThanOrEqualTo(job3Events.size());
    }
  }

  @Test
  @Timeout(60)
  void problemChanges_throttlingWithProblemChanges()
      throws ExecutionException, InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(2)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var intermediateEvents =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());
      var finalEvent = new AtomicReference<FinalBestSolutionEvent<TestdataSolution>>();

      var problemId = 1L;
      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(problemId)
              .withProblemFinder(id -> PlannerTestUtils.generateTestdataSolution("s1", 4))
              .withThrottledBestSolutionEventConsumer(
                  event -> intermediateEvents.add(event), Duration.ofMillis(200))
              .withFinalBestSolutionEventConsumer(finalEvent::set)
              .run();

      // Add a problem change
      var futureChange =
          solverManager.addProblemChange(
              problemId,
              (workingSolution, problemChangeDirector) -> {
                problemChangeDirector.addProblemFact(
                    new TestdataValue("addedValue"), workingSolution.getValueList()::add);
              });

      futureChange.get();
      assertThat(futureChange).isCompleted();

      var finalSolution = solverJob.getFinalBestSolution();

      // Throttling should still work with problem changes
      assertThat(intermediateEvents).isNotEmpty();
      assertThat(finalEvent.get()).isNotNull();
      assertThat(finalSolution).isNotNull();

      // The final solution should include the added value
      assertThat(finalSolution.getValueList()).hasSize(5);
    }
  }

  @Test
  @Timeout(60)
  void builderMethod_convenienceMethodWorksCorrectly()
      throws ExecutionException, InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(500)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var eventCount = new AtomicInteger();

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(DEFAULT_PROBLEM_FINDER)
              .withThrottledBestSolutionEventConsumer(
                  event -> eventCount.incrementAndGet(), Duration.ofMillis(100))
              .run();

      var finalSolution = solverJob.getFinalBestSolution();

      // Should have received some events but throttled
      assertThat(eventCount.get()).isPositive();
      assertThat(finalSolution).isNotNull();
    }
  }

  @Test
  @Timeout(60)
  void exceptionInConsumer_throttlingContinues() throws ExecutionException, InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(500)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var eventCount = new AtomicInteger();
      var finalEvent = new AtomicReference<FinalBestSolutionEvent<TestdataSolution>>();

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(DEFAULT_PROBLEM_FINDER)
              .withThrottledBestSolutionEventConsumer(
                  event -> {
                    eventCount.incrementAndGet();
                    // Throw exception on first event
                    if (eventCount.get() == 1) {
                      throw new RuntimeException("Test exception in consumer");
                    }
                  },
                  Duration.ofMillis(100))
              .withFinalBestSolutionEventConsumer(finalEvent::set)
              .run();

      var finalSolution = solverJob.getFinalBestSolution();

      // Throttling should continue despite exception
      // (The exception is caught internally by ThrottlingBestSolutionEventConsumer and doesn't
      // break throttling)
      // The important thing is that the final solution is delivered, proving throttling continued
      assertThat(eventCount.get()).isPositive();

      // Final solution should still be delivered
      assertThat(finalEvent.get()).isNotNull();
      assertThat(finalSolution).isNotNull();
    }
  }

  @Test
  @Timeout(60)
  void veryShortThrottleDuration_handlesCorrectly()
      throws ExecutionException, InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(2)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var eventCount = new AtomicInteger();

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(DEFAULT_PROBLEM_FINDER)
              .withThrottledBestSolutionEventConsumer(
                  event -> eventCount.incrementAndGet(), Duration.ofMillis(10)) // Very short
              .run();

      var finalSolution = solverJob.getFinalBestSolution();

      // Should receive at least one event with very short throttle
      assertThat(eventCount.get()).isPositive();
      assertThat(finalSolution).isNotNull();
    }
  }

  @Test
  @Timeout(60)
  void veryLongThrottleDuration_handlesCorrectly() throws ExecutionException, InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(500)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var eventCount = new AtomicInteger();
      var finalEvent = new AtomicReference<FinalBestSolutionEvent<TestdataSolution>>();

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(DEFAULT_PROBLEM_FINDER)
              .withThrottledBestSolutionEventConsumer(
                  event -> eventCount.incrementAndGet(), Duration.ofSeconds(10)) // Very long
              .withFinalBestSolutionEventConsumer(finalEvent::set)
              .run();

      var finalSolution = solverJob.getFinalBestSolution();

      // Should receive very few events (maybe 0 or 1) with long throttle
      assertThat(eventCount.get()).isLessThan(3);

      // But final solution should always be delivered
      assertThat(finalEvent.get()).isNotNull();
      assertThat(finalSolution).isNotNull();
    }
  }

  @Test
  @Timeout(60)
  void noIntermediateEvents_finalStillDelivered() throws ExecutionException, InterruptedException {
    // Create a solver that terminates very quickly
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(10)));

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var intermediateEvents =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());
      var finalEvent = new AtomicReference<FinalBestSolutionEvent<TestdataSolution>>();

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(DEFAULT_PROBLEM_FINDER)
              .withThrottledBestSolutionEventConsumer(
                  event -> intermediateEvents.add(event), Duration.ofMillis(100))
              .withFinalBestSolutionEventConsumer(finalEvent::set)
              .run();

      var finalSolution = solverJob.getFinalBestSolution();

      // May have zero intermediate events if solver finishes very quickly
      assertThat(intermediateEvents.size()).isLessThan(3);

      // But final solution should always be delivered
      assertThat(finalEvent.get()).isNotNull();
      assertThat(finalSolution).isNotNull();
    }
  }

  @Test
  @Timeout(60)
  void terminateEarly_throttledConsumerDeliversPending()
      throws ExecutionException, InterruptedException {
    var solvingStartedLatch = new CountDownLatch(1);
    var pausedPhaseConfig =
        new CustomPhaseConfig()
            .withCustomPhaseCommands(
                (scoreDirector, isPhaseTerminated) -> solvingStartedLatch.countDown());

    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withPhases(pausedPhaseConfig, new ConstructionHeuristicPhaseConfig());

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      var intermediateEvents =
          Collections.synchronizedList(new ArrayList<NewBestSolutionEvent<TestdataSolution>>());
      var finalEvent = new AtomicReference<FinalBestSolutionEvent<TestdataSolution>>();

      var solverJob =
          solverManager
              .solveBuilder()
              .withProblemId(1L)
              .withProblemFinder(DEFAULT_PROBLEM_FINDER)
              .withThrottledBestSolutionEventConsumer(
                  event -> intermediateEvents.add(event), Duration.ofMillis(100))
              .withFinalBestSolutionEventConsumer(finalEvent::set)
              .run();

      // Wait for solving to start
      solvingStartedLatch.await();

      // Terminate early
      solverJob.terminateEarly();

      var finalSolution = solverJob.getFinalBestSolution();

      // Final solution should be delivered even with early termination
      assertThat(finalEvent.get()).isNotNull();
      assertThat(finalSolution).isNotNull();
    }
  }

  @Test
  void nullParameters_throwsException() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);

    try (var solverManager = SolverManager.<TestdataSolution, Long>create(solverConfig)) {
      assertThatCode(
              () ->
                  solverManager
                      .solveBuilder()
                      .withProblemId(1L)
                      .withProblemFinder(DEFAULT_PROBLEM_FINDER)
                      .withThrottledBestSolutionEventConsumer(null, Duration.ofMillis(100))
                      .run())
          .isInstanceOf(NullPointerException.class);

      assertThatCode(
              () ->
                  solverManager
                      .solveBuilder()
                      .withProblemId(1L)
                      .withProblemFinder(DEFAULT_PROBLEM_FINDER)
                      .withThrottledBestSolutionEventConsumer(event -> {}, null)
                      .run())
          .isInstanceOf(NullPointerException.class);
    }
  }
}
