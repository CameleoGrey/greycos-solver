package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.assertFreshReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.solver;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListIntegrationTest.trace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

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
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmListLifecycleTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void emptyListUniverseEndsWithoutAttemptsUnderSoleScoreCountLimit(int ownerCount) {
    var solver =
        solver(
            config(new GeneticAlgorithmPhaseConfig().withPopulationSize(5))
                .withTerminationConfig(
                    new TerminationConfig().withScoreCalculationCountLimit(100L)));
    var phaseObserved = requireNoAttempts(solver);

    var result = solver.solve(problem(0, ownerCount));

    // AbstractSolver skips all phases when there are no movable entities; empty movable
    // owners still enter GA, whose own exhaustion path is checked by the observer.
    assertThat(phaseObserved.get()).isEqualTo(ownerCount > 0);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
    assertThat(solver.getSolverScope().getScoreCalculationCount()).isLessThan(100);
    assertThat(solver.isSolving()).isFalse();
    assertThat(result.getEntityList()).hasSize(ownerCount);
    assertThat(result.getValueList()).isEmpty();
    assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    assertFreshReplay(result);
  }

  @Test
  void fullyPinnedOwnersLeaveOptionalUnassignedValueWithoutAMovableDestination() {
    var input =
        TestdataPinnedAllowsUnassignedValuesListSolution.generateUninitializedSolution(3, 2);
    input.getEntityList().getFirst().getValueList().add(input.getValueList().get(0));
    input.getEntityList().getLast().getValueList().add(input.getValueList().get(1));
    input.getEntityList().forEach(owner -> owner.setPinned(true));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataPinnedAllowsUnassignedValuesListSolution.class)
            .withEntityClasses(
                TestdataPinnedAllowsUnassignedValuesListEntity.class,
                TestdataPinnedAllowsUnassignedValuesListValue.class)
            .withConstraintProviderClass(PinnedOptionalConstraints.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(37L)
            .withPhases(new GeneticAlgorithmPhaseConfig().withPopulationSize(5))
            .withTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(100L));
    var solver =
        (DefaultSolver<TestdataPinnedAllowsUnassignedValuesListSolution>)
            SolverFactory.<TestdataPinnedAllowsUnassignedValuesListSolution>create(config)
                .buildSolver();
    var phaseObserved = requireNoAttempts(solver);

    var result = solver.solve(input);

    // Whole-owner pins trigger the solver's ordinary early exit before any phase starts.
    assertThat(phaseObserved).isFalse();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
    assertThat(solver.getSolverScope().getScoreCalculationCount()).isLessThan(100);
    assertThat(solver.isSolving()).isFalse();
    for (var i = 0; i < result.getEntityList().size(); i++) {
      var owner = result.getEntityList().get(i);
      var value = result.getValueList().get(i);
      assertThat(owner.isPinned()).isTrue();
      assertThat(owner.getValueList()).containsExactly(value);
      assertThat(value.getEntity()).isSameAs(owner);
      assertThat(value.getIndex()).isZero();
      assertThat(value.getPrevious()).isNull();
      assertThat(value.getNext()).isNull();
      assertThat(input.getEntityList().get(i).getValueList())
          .containsExactly(input.getValueList().get(i));
    }
    var unassigned = result.getValueList().getLast();
    assertThat(unassigned.getEntity()).isNull();
    assertThat(unassigned.getIndex()).isNull();
    assertThat(unassigned.getPrevious()).isNull();
    assertThat(unassigned.getNext()).isNull();
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-1));
  }

  private static <Solution_> AtomicBoolean requireNoAttempts(DefaultSolver<Solution_> solver) {
    var phaseObserved = new AtomicBoolean();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<Solution_> step) {
            fail("An immovable list model must not start a genetic algorithm attempt.");
          }

          @Override
          public void stepEnded(AbstractStepScope<Solution_> step) {
            fail("An immovable list model must not complete a genetic algorithm attempt.");
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<Solution_> scope) {
            var phase = (GeneticAlgorithmPhaseScope<Solution_>) scope;
            phaseObserved.set(true);
            assertThat(phase.getNextStepIndex()).isZero();
            assertThat(phase.getPhaseMoveEvaluationCount()).isZero();
            assertThat(phase.getPhaseScoreCalculationCount()).isLessThan(100);
            assertThat(phase.getTerminationReason()).contains("no movable assignments");
          }
        });
    return phaseObserved;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void soleMoveLimitCountsEveryCompletedListAttempt(boolean phaseLocal) {
    var phase = new GeneticAlgorithmPhaseConfig().withPopulationSize(5);
    var config = config(phase);
    var limit = new TerminationConfig().withMoveCountLimit(14L);
    if (phaseLocal) phase.setTerminationConfig(limit);
    else config.setTerminationConfig(limit);
    var solver = solver(config);
    var steps = trace(solver);

    assertFreshReplay(solver.solve(problem(10, 3)));

    assertThat(steps).hasSize(14);
    assertThat(steps)
        .extracting(GeneticAlgorithmListIntegrationTest.Step::index)
        .containsExactlyElementsOf(IntStream.range(0, 14).boxed().toList());
    assertThat(steps.stream().filter(GeneticAlgorithmListIntegrationTest.Step::seeding)).hasSize(4);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(14);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void zeroMoveLimitLeavesTheInputAssignmentUnchanged(boolean phaseLocal) {
    var phase = new GeneticAlgorithmPhaseConfig().withPopulationSize(5);
    var config = config(phase);
    var limit = new TerminationConfig().withMoveCountLimit(0L);
    if (phaseLocal) phase.setTerminationConfig(limit);
    else config.setTerminationConfig(limit);
    var solver = solver(config);
    var steps = trace(solver);
    var input = problem(10, 3);
    var original = assignments(input);

    var result = solver.solve(input);

    assertThat(steps).isEmpty();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
    assertThat(assignments(result)).isEqualTo(original);
    assertFreshReplay(result);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3, 8})
  void stepBudgetCanEndInsideSeedingOrInsideAGeneration(int limit) {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(limit))));
    var steps = trace(solver);

    assertFreshReplay(solver.solve(problem(10, 3)));

    assertThat(steps).hasSize(limit);
    assertThat(steps.stream().filter(GeneticAlgorithmListIntegrationTest.Step::seeding))
        .hasSize(Math.min(4, limit));
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(limit);
  }

  @Test
  void noProgressGuardStopsCachedListTrialsUnderSoleScoreCountLimit() {
    var solver =
        solver(
            config(
                    new GeneticAlgorithmPhaseConfig()
                        .withPopulationSize(1)
                        .withMutationRateMultiplier(1.0)
                        .withMutationOperators(
                            new GeneticAlgorithmMutationOperatorConfig()
                                .withType(GeneticAlgorithmMutationType.INVERSE)
                                .withProbability(1.0))
                        .withNoProgressAttemptLimit(9L))
                .withTerminationConfig(
                    new TerminationConfig().withScoreCalculationCountLimit(100L)));
    var steps = trace(solver);
    var phase = new AtomicReference<GeneticAlgorithmPhaseScope<TestdataListSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataListSolution> scope) {
            phase.set((GeneticAlgorithmPhaseScope<TestdataListSolution>) scope);
          }
        });

    var result = solver.solve(problem(2, 1));

    assertThat(steps).hasSize(10);
    assertThat(steps.getFirst().outcome()).isEqualTo(GeneticAlgorithmOutcome.EVALUATED);
    assertThat(steps.subList(1, steps.size()))
        .allSatisfy(
            step -> {
              assertThat(step.outcome())
                  .isIn(GeneticAlgorithmOutcome.NO_CHANGE, GeneticAlgorithmOutcome.DUPLICATE);
              assertThat(step.changedAssignments()).isZero();
            });
    assertThat(phase.get().getPhaseScoreCalculationCount()).isEqualTo(2);
    assertThat(phase.get().getNoProgressAttemptCount()).isEqualTo(9);
    assertThat(phase.get().getTerminationReason()).contains("no fresh valid offspring");
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(steps.size());
    assertFreshReplay(result);
  }

  @Test
  void cancellationBeforeMaterializationDoesNotCreditAnAttempt() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(20))));
    var steps = trace(solver);
    var starts = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataListSolution> step) {
            starts.incrementAndGet();
            assertThat(solver.terminateEarly()).isTrue();
          }
        });
    var input = problem(10, 3);
    var original = assignments(input);

    var result = solver.solve(input);

    assertThat(starts).hasValue(1);
    assertThat(steps).isEmpty();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
    assertThat(assignments(result)).isEqualTo(original);
    assertThat(solver.isSolving()).isFalse();
    assertFreshReplay(result);
  }

  @Test
  void cancellationInsideAGenerationRetainsThePublishedBestClone() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(3)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(20))));
    var steps = trace(solver);
    var published = new ArrayList<TestdataListSolution>();
    var snapshots = new ArrayList<List<List<String>>>();
    solver.addEventListener(
        event -> {
          published.add(event.getNewBestSolution());
          snapshots.add(assignments(event.getNewBestSolution()));
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> step) {
            if (step.getStepIndex() == 3) assertThat(solver.terminateEarly()).isTrue();
          }
        });

    var result = solver.solve(problem(10, 3));

    assertThat(steps).hasSize(4);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(4);
    assertThat(published).isNotEmpty();
    for (var i = 0; i < published.size(); i++) {
      assertThat(assignments(published.get(i))).isEqualTo(snapshots.get(i));
      assertReplay(published.get(i));
    }
    assertThat(assignments(result)).isEqualTo(snapshots.getLast());
    assertThat(result.getScore()).isEqualTo(published.getLast().getScore());
    assertFreshReplay(result);
  }

  @Test
  void problemChangeRebuildsOwnerAndValueIdsAndRestartsThePopulation() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(3)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(8))));
    var queued = new AtomicBoolean();
    var ownerCounts = new ArrayList<Integer>();
    var valueCounts = new ArrayList<Integer>();
    var traces = new ArrayList<List<Integer>>();
    var sessions = new ArrayList<Object>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataListSolution> scope) {
            ownerCounts.add(scope.getWorkingSolution().getEntityList().size());
            valueCounts.add(scope.getWorkingSolution().getValueList().size());
            sessions.add(
                ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession());
            traces.add(new ArrayList<>());
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> step) {
            traces.getLast().add(step.getStepIndex());
            assertThat(step.getScore().raw())
                .isEqualTo(
                    GeneticAlgorithmListIntegrationTest.replayScore(step.getWorkingSolution()));
            if (queued.compareAndSet(false, true)) {
              solver.addProblemChange(
                  (solution, director) -> {
                    solution.setEntityList(new ArrayList<>(solution.getEntityList()));
                    solution.setValueList(new ArrayList<>(solution.getValueList()));
                    var value = new TestdataListValue("9");
                    var owner = new TestdataListEntity("9", value);
                    director.addEntity(value, solution.getValueList()::add);
                    director.addEntity(owner, solution.getEntityList()::add);
                  });
            }
          }
        });

    var result = solver.solve(problem(6, 2));

    assertThat(ownerCounts).containsExactly(2, 3);
    assertThat(valueCounts).containsExactly(6, 7);
    assertThat(traces).hasSize(2);
    assertThat(traces.getFirst()).containsExactly(0);
    assertThat(traces.getLast()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    assertThat(sessions.getLast()).isNotSameAs(sessions.getFirst());
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(result.getEntityList()).extracting(TestdataListEntity::getCode).contains("9");
    assertThat(result.getValueList()).extracting(TestdataListValue::getCode).contains("9");
    assertFreshReplay(result);
  }

  public static final class PinnedOptionalConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(TestdataPinnedAllowsUnassignedValuesListValue.class)
            .filter(value -> value.getEntity() == null)
            .penalize(SimpleScore.ONE)
            .asConstraint("Unassigned values")
      };
    }
  }
}
