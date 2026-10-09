package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.assertFreshReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.assertStructure;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.DefaultGeneticAlgorithmPhase.LogicalAttempt;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.MixedConstraints;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.MixedSolution;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.Owner;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.Task;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.WorkingSolutionMutationObserver;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.SolverTags;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Timeout(30)
class GeneticAlgorithmDeferredMaterializationTest {

  @Test
  void productionPathMaterializesFarFewerCoordinatorAssignmentsThanEvaluatedOffspring() {
    var solver =
        solver(config(phase(3, 13, 253)).withConstraintProviderClass(ConstantConstraints.class));
    var phase = ga(solver);
    var trace = new ArrayList<LogicalAttempt>();
    var working = new AtomicReference<TestdataListSolution>();
    phase.setLogicalAttemptObserver(
        attempt -> {
          trace.add(attempt);
          working.compareAndSet(null, solver.getSolverScope().getWorkingSolution());
        });
    var input = problem(32, 4);
    var original = assignments(input);

    var result = solver.solve(input);

    var diagnostics = phase.getRunDiagnostics();
    assertThat(diagnostics.deferredMaterializationUsed()).isTrue();
    assertThat(diagnostics.eagerReason()).isNull();
    assertThat(diagnostics.completedAttempts()).isEqualTo(253);
    assertThat(diagnostics.evaluatedOffspring()).isGreaterThan(100);
    assertThat(diagnostics.coordinatorMaterializations())
        .isPositive()
        .isLessThan(diagnostics.evaluatedOffspring() / 3);
    assertThat(diagnostics.seedingNanos()).isPositive();
    assertThat(diagnostics.searchNanos()).isGreaterThanOrEqualTo(diagnostics.seedingNanos());
    assertThat(trace).hasSize(253);
    assertThat(
            trace.stream()
                .filter(attempt -> !attempt.seeding())
                .filter(attempt -> attempt.outcome() == GeneticAlgorithmOutcome.EVALUATED)
                .count())
        .isEqualTo(diagnostics.evaluatedOffspring());
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(253);
    assertThat(assignments(input)).isEqualTo(original);
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-32));
    assertStructure(result);
    assertStructure(working.get());
    assertThat(snapshot(working.get())).isEqualTo(trace.getLast().assignments());
    assertThat(working.get().getScore()).isEqualTo(trace.getLast().score().raw());
    assertWorkersClosed(phase, 3);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 37, 997})
  void detachedLogicalTracesAndBestPublicationsMatchEagerAcrossWorkerCounts(long seed) {
    var eager = run(seed, 1, true);
    for (int workers : new int[] {1, 3}) {
      var deferred = run(seed, workers, false);

      assertThat(deferred.attempts).containsExactlyElementsOf(eager.attempts);
      assertThat(deferred.publications).containsExactlyElementsOf(eager.publications);
      assertThat(deferred.result).isEqualTo(eager.result);
      assertThat(deferred.phase.getRunDiagnostics().deferredMaterializationUsed()).isTrue();
      assertThat(deferred.phase.getRunDiagnostics().eagerReason()).isNull();
      assertThat(deferred.phase.getRunDiagnostics().coordinatorMaterializations())
          .isLessThan(eager.phase.getRunDiagnostics().coordinatorMaterializations());
    }
    assertThat(eager.phase.getRunDiagnostics().deferredMaterializationUsed()).isFalse();
    assertThat(eager.phase.getRunDiagnostics().eagerReason()).isEqualTo("lifecycle observers");
  }

  @Test
  void optionalMembershipChangesReplayAndMatchEagerWithoutStaleUnassignedIndexes() {
    List<LogicalAttempt> baseline = null;
    for (boolean eager : new boolean[] {true, false}) {
      var phaseConfig =
          phase(3, 7, 157)
              .withMutationRateMultiplier(3.0)
              .withMutationOperators(
                  new GeneticAlgorithmMutationOperatorConfig()
                      .withType(GeneticAlgorithmMutationType.CHANGE)
                      .withProbability(1.0));
      var solver =
          (DefaultSolver<TestdataPinnedUnassignedValuesListSolution>)
              SolverFactory.<TestdataPinnedUnassignedValuesListSolution>create(
                      new SolverConfig()
                          .withSolutionClass(TestdataPinnedUnassignedValuesListSolution.class)
                          .withEntityClasses(
                              TestdataPinnedUnassignedValuesListEntity.class,
                              TestdataPinnedUnassignedValuesListValue.class)
                          .withConstraintProviderClass(
                              GeneticAlgorithmListIntegrationTest.OptionalConstraints.class)
                          .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
                          .withRandomSeed(37L)
                          .withPhases(phaseConfig))
                  .buildSolver();
      var phase =
          (DefaultGeneticAlgorithmPhase<TestdataPinnedUnassignedValuesListSolution>)
              solver.getPhaseList().getFirst();
      if (eager) phase.addPhaseLifecycleListener(new PhaseLifecycleListenerAdapter<>() {});
      var input = optionalProblem();
      var initial =
          new GeneticAlgorithmListSnapshot(new int[][] {{0, 1, 2, 3, 4, 5}, {6, 7, 8, 9, 10, 11}});
      var previous = new AtomicReference<>(positions(initial));
      var previouslyIndexed = new HashSet<Integer>();
      previous
          .get()
          .forEach(
              (value, position) -> {
                if (position.get(1) > 0) previouslyIndexed.add(value);
              });
      var absentAfterEarlierIndex = new AtomicInteger();
      var trace = new ArrayList<LogicalAttempt>();
      phase.setLogicalAttemptObserver(
          attempt -> {
            var before = previous.get();
            var after = positions(attempt.assignments());
            int changed = 0;
            for (int value = 0; value < input.getValueList().size(); value++) {
              if (!Objects.equals(before.get(value), after.get(value))) changed++;
              if (!attempt.seeding()
                  && attempt.outcome() == GeneticAlgorithmOutcome.EVALUATED
                  && !before.containsKey(value)
                  && !after.containsKey(value)
                  && previouslyIndexed.contains(value)) {
                absentAfterEarlierIndex.incrementAndGet();
              }
            }
            assertThat(attempt.changedAssignmentCount()).isEqualTo(changed);
            assertThat(attempt.score().raw()).isEqualTo(SimpleScore.of(-after.size()));
            assertThat(after.get(0)).containsExactly(0, 0);
            assertThat(after.get(6)).containsExactly(1, 0);
            after.forEach(
                (value, position) -> {
                  if (position.get(1) > 0) previouslyIndexed.add(value);
                });
            previous.set(after);
            trace.add(attempt);
          });
      solver.addEventListener(event -> assertOptionalReplay(event.getNewBestSolution()));

      assertOptionalReplay(solver.solve(input));

      assertThat(trace).hasSize(157);
      assertThat(absentAfterEarlierIndex.get()).isPositive();
      assertThat(phase.getRunDiagnostics().deferredMaterializationUsed()).isEqualTo(!eager);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(157);
      assertWorkersClosed(phase, 3);
      if (baseline == null) baseline = trace;
      else assertThat(trace).containsExactlyElementsOf(baseline);
    }
  }

  @Test
  void constraintSamplesSynchronizeOnlyWhenDueWithoutDisablingDeferredExecution() {
    try (var meters = new TestMeters()) {
      var monitoring =
          new MonitoringConfig()
              .withConstraintMatchMetricSampleInterval(7)
              .withSolverMetricList(
                  List.of(
                      SolverMetric.MOVE_COUNT_PER_STEP,
                      SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
                      SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE));
      var solver = solver(config(phase(3, 7, 83)).withMonitoringConfig(monitoring));
      solver.setMonitorTags(SolverTags.withProblemId(meters.tag));
      var sampled = new AtomicInteger();
      var expectedStepScore = new AtomicReference<SimpleScore>();
      var bestScore = new AtomicReference<SimpleScore>();
      solver.addEventListener(event -> bestScore.set(event.getNewBestSolution().getScore()));
      var input = problem(24, 4);
      expectedStepScore.set(GeneticAlgorithmListIntegrationTest.replayScore(input));
      bestScore.set(expectedStepScore.get());
      ga(solver)
          .setLogicalAttemptObserver(
              attempt -> {
                if ((attempt.stepIndex() + 1) % 7 == 0) {
                  expectedStepScore.set((SimpleScore) attempt.score().raw());
                  sampled.incrementAndGet();
                  var working = solver.getSolverScope().getWorkingSolution();
                  assertReplay(working);
                  assertThat(snapshot(working)).isEqualTo(attempt.assignments());
                }
                assertThat(meters.score(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE))
                    .isEqualTo((double) expectedStepScore.get().score());
                assertThat(meters.score(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE))
                    .isEqualTo((double) bestScore.get().score());
              });

      var result = solver.solve(input);

      assertThat(sampled).hasValue(11);
      assertFreshReplay(result);
      assertThat(ga(solver).getRunDiagnostics().deferredMaterializationUsed()).isTrue();
      assertThat(ga(solver).getRunDiagnostics().eagerReason()).isNull();
      assertThat(meters.score(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE))
          .isEqualTo((double) result.getScore().score());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void installingThenRemovingALifecycleListenerPermanentlyFallsBackToEager(boolean solverLevel) {
    var solver = solver(config(phase(3, 7, 83)));
    var phase = ga(solver);
    var trace = new ArrayList<LogicalAttempt>();
    var starts = new AtomicInteger();
    var ends = new AtomicInteger();
    var listener =
        new PhaseLifecycleListenerAdapter<TestdataListSolution>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataListSolution> scope) {
            starts.incrementAndGet();
            assertReplay(scope.getWorkingSolution());
            assertThat(snapshot(scope.getWorkingSolution()))
                .isEqualTo(trace.getLast().assignments());
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> scope) {
            ends.incrementAndGet();
            assertReplay(scope.getWorkingSolution());
            assertThat(scope.getScore().raw()).isEqualTo(scope.getWorkingSolution().getScore());
          }
        };
    phase.setLogicalAttemptObserver(
        attempt -> {
          trace.add(attempt);
          if (attempt.stepIndex() == 20) {
            if (solverLevel) solver.addPhaseLifecycleListener(listener);
            else phase.addPhaseLifecycleListener(listener);
          }
          if (attempt.stepIndex() == 30) {
            if (solverLevel) solver.removePhaseLifecycleListener(listener);
            else phase.removePhaseLifecycleListener(listener);
          }
          if (attempt.stepIndex() > 30) {
            var working = solver.getSolverScope().getWorkingSolution();
            assertReplay(working);
            assertThat(snapshot(working)).isEqualTo(attempt.assignments());
          }
        });

    assertFreshReplay(solver.solve(problem(24, 4)));

    assertThat(starts).hasValue(10);
    assertThat(ends).hasValue(10);
    assertThat(trace).hasSize(83);
    assertThat(phase.getRunDiagnostics().deferredMaterializationUsed()).isTrue();
    assertThat(phase.getRunDiagnostics().eagerReason()).isEqualTo("lifecycle observers");
    assertThat(phase.hasPhaseLifecycleListeners()).isFalse();
    assertThat(solver.hasPhaseLifecycleListeners()).isFalse();
  }

  @Test
  void bestSolutionListenerCanInstallAnObserverBeforeTheSameStepEnds() {
    var solver = solver(config(phase(3, 7, 137)));
    var phase = ga(solver);
    var installed = new AtomicBoolean();
    var observed = new AtomicInteger();
    solver.addEventListener(
        event -> {
          assertReplay(event.getNewBestSolution());
          if (phase.getEvaluatorDiagnostics() != null && installed.compareAndSet(false, true)) {
            phase.addPhaseLifecycleListener(
                new PhaseLifecycleListenerAdapter<>() {
                  @Override
                  public void stepEnded(AbstractStepScope<TestdataListSolution> scope) {
                    observed.incrementAndGet();
                    assertReplay(scope.getWorkingSolution());
                    assertThat(scope.getScore().raw())
                        .isEqualTo(scope.getWorkingSolution().getScore());
                  }
                });
          }
        });

    assertFreshReplay(solver.solve(problem(24, 4)));

    assertThat(installed).isTrue();
    assertThat(observed.get()).isPositive();
    assertThat(phase.getRunDiagnostics().deferredMaterializationUsed()).isTrue();
    assertThat(phase.getRunDiagnostics().eagerReason()).isEqualTo("lifecycle observers");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void workingMutationObserverRequiresEagerAssignmentsIncludingLateInstallation(boolean late) {
    var solver = solver(config(phase(3, 7, 83)));
    var phase = ga(solver);
    var notifications = new AtomicInteger();
    var closed = new AtomicInteger();
    var installAt = late ? 20 : 0;
    phase.setLogicalAttemptObserver(
        attempt -> {
          if (attempt.stepIndex() == installAt) {
            solver
                .getSolverScope()
                .getScoreDirector()
                .setWorkingSolutionMutationObserver(
                    new WorkingSolutionMutationObserver<>() {
                      @Override
                      public void workingSolutionChanged() {}

                      @Override
                      public void afterListVariableChanged(
                          Object entity, String variableName, int fromIndex, int toIndex) {
                        notifications.incrementAndGet();
                      }

                      @Override
                      public void close() {
                        closed.incrementAndGet();
                      }
                    });
          } else if (attempt.stepIndex() > installAt) {
            var working = solver.getSolverScope().getWorkingSolution();
            assertReplay(working);
            assertThat(snapshot(working)).isEqualTo(attempt.assignments());
          }
        });

    assertFreshReplay(solver.solve(problem(24, 4)));

    assertThat(notifications.get()).isPositive();
    assertThat(closed).hasValue(1);
    assertThat(phase.hasPhaseLifecycleListeners()).isFalse();
    assertThat(solver.hasPhaseLifecycleListeners()).isFalse();
    assertThat(phase.getRunDiagnostics().deferredMaterializationUsed()).isEqualTo(late);
    assertThat(phase.getRunDiagnostics().eagerReason()).isEqualTo("lifecycle observers");
    assertWorkersClosed(phase, 3);
  }

  @ParameterizedTest
  @EnumSource(
      value = EnvironmentMode.class,
      names = {"STEP_ASSERT", "FULL_ASSERT"})
  void assertionModesKeepEagerMaterialization(EnvironmentMode mode) {
    var solver = solver(config(phase(2, 5, 31)).withEnvironmentMode(mode));

    assertFreshReplay(solver.solve(problem(12, 3)));

    assertThat(ga(solver).getRunDiagnostics().deferredMaterializationUsed()).isFalse();
    assertThat(ga(solver).getRunDiagnostics().eagerReason()).isEqualTo("assertions");
    assertThat(ga(solver).getRunDiagnostics().coordinatorMaterializations()).isPositive();
  }

  @Test
  void basicAndMixedAssignmentsKeepEagerMaterialization() {
    var basic =
        GeneticAlgorithmIntegrationTest.solver(
            GeneticAlgorithmIntegrationTest.config(phase(2, 5, 31)));
    GeneticAlgorithmIntegrationTest.assertReplay(
        basic.solve(GeneticAlgorithmIntegrationTest.problem(5, 12)));
    var basicPhase = (DefaultGeneticAlgorithmPhase<?>) basic.getPhaseList().getFirst();
    assertThat(basicPhase.getRunDiagnostics().deferredMaterializationUsed()).isFalse();
    assertThat(basicPhase.getRunDiagnostics().eagerReason()).isEqualTo("basic assignments");

    var mixed =
        (DefaultSolver<MixedSolution>)
            SolverFactory.<MixedSolution>create(
                    new SolverConfig()
                        .withSolutionClass(MixedSolution.class)
                        .withEntityClasses(Owner.class, Task.class)
                        .withConstraintProviderClass(MixedConstraints.class)
                        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
                        .withRandomSeed(37L)
                        .withPhases(phase(2, 5, 31)))
                .buildSolver();
    var result = mixed.solve(GeneticAlgorithmMixedIntegrationTest.problem());
    assertThat(result.score)
        .isEqualTo(SimpleScore.of(-result.tasks.stream().mapToInt(task -> task.depth).sum()));
    var mixedPhase = (DefaultGeneticAlgorithmPhase<?>) mixed.getPhaseList().getFirst();
    assertThat(mixedPhase.getRunDiagnostics().deferredMaterializationUsed()).isFalse();
    assertThat(mixedPhase.getRunDiagnostics().eagerReason()).isEqualTo("basic assignments");
  }

  @Test
  void cancellationSynchronizesTheLastCompletedCursorAndClosesWorkers() {
    var solver = solver(config(phase(3, 7, 137)));
    var phase = ga(solver);
    var trace = new ArrayList<LogicalAttempt>();
    var working = new AtomicReference<TestdataListSolution>();
    phase.setLogicalAttemptObserver(
        attempt -> {
          trace.add(attempt);
          working.compareAndSet(null, solver.getSolverScope().getWorkingSolution());
          if (attempt.stepIndex() == 24) solver.terminateEarly();
        });

    assertFreshReplay(solver.solve(problem(24, 4)));

    assertThat(trace).hasSize(25);
    assertThat(phase.getRunDiagnostics().completedAttempts()).isEqualTo(25);
    assertThat(phase.getRunDiagnostics().deferredMaterializationUsed()).isTrue();
    assertThat(phase.getRunDiagnostics().eagerReason()).isNull();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(25);
    assertReplay(working.get());
    assertThat(snapshot(working.get())).isEqualTo(trace.getLast().assignments());
    assertWorkersClosed(phase, 3);
  }

  @Test
  void followingPhaseReceivesPublishedBestAndReuseRebuildsDeferredState() {
    var gaConfig = phase(3, 7, 83);
    var solver =
        solver(
            config(gaConfig)
                .withPhases(
                    gaConfig,
                    new LocalSearchPhaseConfig()
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(3))));
    var handoffs = new AtomicInteger();
    // A listener on the following phase does not observe GA working state.
    solver
        .getPhaseList()
        .get(1)
        .addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void phaseStarted(AbstractPhaseScope<TestdataListSolution> scope) {
                handoffs.incrementAndGet();
                assertReplay(scope.getWorkingSolution());
                assertThat(assignments(scope.getWorkingSolution()))
                    .isEqualTo(assignments(scope.getSolverScope().getBestSolution()));
              }
            });
    var trace = new ArrayList<LogicalAttempt>();
    ga(solver).setLogicalAttemptObserver(trace::add);
    List<LogicalAttempt> firstTrace = null;
    Publication firstResult = null;
    for (int run = 0; run < 2; run++) {
      trace.clear();
      var result = solver.solve(problem(24, 4));
      assertFreshReplay(result);
      assertThat(trace).hasSize(83);
      assertThat(ga(solver).getRunDiagnostics().completedAttempts()).isEqualTo(83);
      assertThat(ga(solver).getRunDiagnostics().deferredMaterializationUsed()).isTrue();
      assertThat(ga(solver).getRunDiagnostics().eagerReason()).isNull();
      var publication = new Publication(assignments(result), result.getScore());
      if (firstTrace == null) {
        firstTrace = List.copyOf(trace);
        firstResult = publication;
      } else {
        assertThat(trace).containsExactlyElementsOf(firstTrace);
        assertThat(publication).isEqualTo(firstResult);
      }
      assertWorkersClosed(ga(solver), 3);
    }
    assertThat(handoffs).hasValue(2);
  }

  private static Run run(long seed, int workers, boolean eager) {
    var solver = solver(config(phase(workers, 7, 137)).withRandomSeed(seed));
    var phase = ga(solver);
    var run = new Run(phase);
    if (eager) phase.addPhaseLifecycleListener(new PhaseLifecycleListenerAdapter<>() {});
    phase.setLogicalAttemptObserver(
        attempt -> {
          assertThat(attempt.score().raw()).isEqualTo(replay(attempt.assignments(), 24));
          run.attempts.add(attempt);
        });
    var clones = new ArrayList<TestdataListSolution>();
    solver.addEventListener(
        event -> {
          var best = event.getNewBestSolution();
          assertReplay(best);
          clones.add(best);
          run.publications.add(new Publication(assignments(best), best.getScore()));
        });
    var input = problem(24, 4);
    var original = assignments(input);
    var result = solver.solve(input);
    assertFreshReplay(result);
    assertThat(run.attempts).hasSize(137);
    assertThat(run.publications).hasSizeGreaterThan(1);
    assertThat(assignments(input)).isEqualTo(original);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(137);
    for (int i = 0; i < clones.size(); i++) {
      var clone = clones.get(i);
      assertReplay(clone);
      assertThat(new Publication(assignments(clone), clone.getScore()))
          .isEqualTo(run.publications.get(i));
      assertThat(clone.getEntityList().getFirst()).isNotSameAs(input.getEntityList().getFirst());
      assertThat(clone.getValueList().getFirst()).isNotSameAs(input.getValueList().getFirst());
      if (i > 0) {
        assertThat(clone.getValueList().getFirst())
            .isNotSameAs(clones.get(i - 1).getValueList().getFirst());
      }
    }
    // The detached assignments survive later transitions and do not expose their backing arrays.
    for (var attempt : run.attempts) {
      var copy = attempt.assignments().copyLists();
      var saved = new GeneticAlgorithmListSnapshot(copy);
      for (var list : copy) {
        if (list.length > 0) list[0] = -1;
      }
      assertThat(attempt.assignments()).isEqualTo(saved);
      assertThat(attempt.score().raw()).isEqualTo(replay(attempt.assignments(), 24));
    }
    run.result = new Publication(assignments(result), result.getScore());
    assertWorkersClosed(phase, workers);
    return run;
  }

  private static SimpleScore replay(GeneticAlgorithmListSnapshot snapshot, int valueCount) {
    var seen = new boolean[valueCount];
    long score = 0;
    for (int owner = 0; owner < snapshot.ownerCount(); owner++) {
      for (int index = 0; index < snapshot.size(owner); index++) {
        int value = snapshot.get(owner, index);
        assertThat(value).isBetween(0, valueCount - 1);
        assertThat(seen[value]).isFalse();
        seen[value] = true;
        score += (long) (owner + 1) * (index + 1) * (value + 1);
      }
    }
    assertThat(seen).containsOnly(true);
    return SimpleScore.of(score);
  }

  private static TestdataPinnedUnassignedValuesListSolution optionalProblem() {
    var solution = new TestdataPinnedUnassignedValuesListSolution();
    var values = new ArrayList<TestdataPinnedUnassignedValuesListValue>();
    for (int i = 0; i < 16; i++) values.add(new TestdataPinnedUnassignedValuesListValue("" + i));
    var first =
        new TestdataPinnedUnassignedValuesListEntity("0", new ArrayList<>(values.subList(0, 6)));
    var second =
        new TestdataPinnedUnassignedValuesListEntity("1", new ArrayList<>(values.subList(6, 12)));
    first.setPlanningPinToIndex(1);
    second.setPlanningPinToIndex(1);
    solution.setEntityList(new ArrayList<>(List.of(first, second)));
    solution.setValueList(values);
    return solution;
  }

  private static Map<Integer, List<Integer>> positions(GeneticAlgorithmListSnapshot snapshot) {
    var positions = new HashMap<Integer, List<Integer>>();
    for (int owner = 0; owner < snapshot.ownerCount(); owner++) {
      for (int index = 0; index < snapshot.size(owner); index++) {
        assertThat(positions.put(snapshot.get(owner, index), List.of(owner, index))).isNull();
      }
    }
    return positions;
  }

  private static void assertOptionalReplay(TestdataPinnedUnassignedValuesListSolution solution) {
    var assigned = new HashSet<Integer>();
    for (var owner : solution.getEntityList()) {
      var values = owner.getValueList();
      for (int index = 0; index < values.size(); index++) {
        var value = values.get(index);
        int id = Integer.parseInt(value.getCode());
        assertThat(assigned.add(id)).isTrue();
        assertThat(solution.getValueList().get(id)).isSameAs(value);
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(index);
        assertThat(value.getPrevious()).isSameAs(index == 0 ? null : values.get(index - 1));
        assertThat(value.getNext())
            .isSameAs(index + 1 == values.size() ? null : values.get(index + 1));
      }
    }
    for (var value : solution.getValueList()) {
      if (!assigned.contains(Integer.parseInt(value.getCode()))) {
        assertThat(value.getEntity()).isNull();
        assertThat(value.getIndex()).isNull();
        assertThat(value.getPrevious()).isNull();
        assertThat(value.getNext()).isNull();
      }
    }
    assertThat(solution.getScore()).isEqualTo(SimpleScore.of(-assigned.size()));
  }

  private static GeneticAlgorithmListSnapshot snapshot(TestdataListSolution solution) {
    return new GeneticAlgorithmListSnapshot(
        solution.getEntityList().stream()
            .map(
                owner ->
                    owner.getValueList().stream()
                        .mapToInt(value -> Integer.parseInt(value.getCode()))
                        .toArray())
            .toArray(int[][]::new));
  }

  private static GeneticAlgorithmPhaseConfig phase(int workers, int population, int steps) {
    return new GeneticAlgorithmPhaseConfig()
        .withEvaluatorThreadCount(workers)
        .withPopulationSize(population)
        .withPBestRate(0.8)
        .withTabuEntityRate(0.25)
        .withNoProgressAttemptLimit(1000L)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static DefaultGeneticAlgorithmPhase<TestdataListSolution> ga(
      DefaultSolver<TestdataListSolution> solver) {
    return (DefaultGeneticAlgorithmPhase<TestdataListSolution>) solver.getPhaseList().getFirst();
  }

  private static void assertWorkersClosed(DefaultGeneticAlgorithmPhase<?> phase, int workers) {
    var diagnostics = phase.getEvaluatorDiagnostics();
    assertThat(diagnostics.initializedWorkerCount()).isEqualTo(workers);
    assertThat(diagnostics.closedWorkerCount()).isEqualTo(workers);
    assertThat(diagnostics.sessionCount()).isEqualTo(workers);
    assertThat(diagnostics.calculationCount()).isEqualTo(diagnostics.transferredCalculationCount());
  }

  private static final class Run {
    private final DefaultGeneticAlgorithmPhase<TestdataListSolution> phase;
    private final List<LogicalAttempt> attempts = new ArrayList<>();
    private final List<Publication> publications = new ArrayList<>();
    private Publication result;

    private Run(DefaultGeneticAlgorithmPhase<TestdataListSolution> phase) {
      this.phase = phase;
    }
  }

  private record Publication(List<List<String>> assignments, SimpleScore score) {}

  public static final class ConstantConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory.forEach(TestdataListValue.class).penalize(SimpleScore.ONE).asConstraint("assigned")
      };
    }
  }

  private static final class TestMeters implements AutoCloseable {
    private final String tag = UUID.randomUUID().toString();
    private final Tags tags = SolverTags.withProblemId(tag).asTags();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private TestMeters() {
      Metrics.addRegistry(registry);
    }

    private double score(SolverMetric metric) {
      var gauges = registry.find(metric.getMeterId() + ".score").tags(tags).gauges();
      assertThat(gauges).isNotEmpty();
      return gauges.stream().mapToDouble(gauge -> gauge.value()).sum();
    }

    @Override
    public void close() {
      Metrics.removeRegistry(registry);
      registry.close();
      for (var meter : List.copyOf(Metrics.globalRegistry.getMeters())) {
        if (tag.equals(meter.getId().getTag("problem.id"))) {
          Metrics.globalRegistry.remove(meter);
        }
      }
    }
  }
}
