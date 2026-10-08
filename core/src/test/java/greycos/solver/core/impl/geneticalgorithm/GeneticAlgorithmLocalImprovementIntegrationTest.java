package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordSteps;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmLocalImprovementIntegrationTest {

  @ParameterizedTest
  @ValueSource(longs = {0, 37, 997})
  void explicitZeroPreservesTheDefaultListTraceAndPhysicalScoreCalls(long seed) {
    var defaultConfig =
        GeneticAlgorithmListIntegrationTest.config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withPBestRate(0.8)
                    .withTabuEntityRate(0.25)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(44)))
            .withRandomSeed(seed);
    var zeroConfig = defaultConfig.copyConfig();
    ((GeneticAlgorithmPhaseConfig) zeroConfig.getPhaseConfigList().getFirst())
        .setLocalImprovementMoveCountLimit(0L);
    var defaultSolver = GeneticAlgorithmListIntegrationTest.solver(defaultConfig);
    var zeroSolver = GeneticAlgorithmListIntegrationTest.solver(zeroConfig);
    var defaultTrace = GeneticAlgorithmListIntegrationTest.trace(defaultSolver);
    var zeroTrace = GeneticAlgorithmListIntegrationTest.trace(zeroSolver);

    var defaultResult = defaultSolver.solve(GeneticAlgorithmListIntegrationTest.problem(12, 3));
    var zeroResult = zeroSolver.solve(GeneticAlgorithmListIntegrationTest.problem(12, 3));

    assertThat(zeroTrace).containsExactlyElementsOf(defaultTrace);
    assertThat(GeneticAlgorithmListIntegrationTest.assignments(zeroResult))
        .isEqualTo(GeneticAlgorithmListIntegrationTest.assignments(defaultResult));
    assertThat(zeroResult.getScore()).isEqualTo(defaultResult.getScore());
    assertThat(zeroSolver.getSolverScope().getScoreCalculationCount())
        .isEqualTo(defaultSolver.getSolverScope().getScoreCalculationCount());
    assertThat(zeroSolver.getSolverScope().getMoveEvaluationCount()).isEqualTo(44);
    GeneticAlgorithmListIntegrationTest.assertFreshReplay(zeroResult);
  }

  @Test
  void freshBasicChildrenImproveWithOneRetainedSessionAndIndependentlyReplayedScores() {
    var solver = solver(config(phase(8L, 45)).withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
            TestdataSolution.buildSolutionDescriptor(),
            new GeneticAlgorithmIntegrationTest.NumberConstraints(),
            EnvironmentMode.NO_ASSERT);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          private Object session;

          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            session =
                ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession();
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
            assertThat(
                    ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector())
                        .getSession())
                .isSameAs(session);
            assertEligibleCounters(step, 8L);
            try (var fresh = factory.createScoreDirectorBuilder().build()) {
              fresh.setWorkingSolution(scope.getScoreDirector().cloneWorkingSolution());
              assertThat(fresh.calculateScore()).isEqualTo(step.getScore());
            }
          }
        });

    var result = solver.solve(problem(8, 12));

    assertThat(completed).hasSize(45);
    assertThat(phase.get().getLocalImprovementAcceptedCount()).isPositive();
    assertAccounting(phase.get(), completed);
    assertReplay(result);
  }

  @Test
  void equalScoreProbesAreNeverAccepted() {
    var solver = solver(config(phase(5L, 25)).withConstraintProviderClass(FlatConstraints.class));
    var phase = recordPhase(solver);
    var completed = new ArrayList<GeneticAlgorithmStepScope<TestdataSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
            completed.add(step);
            assertThat(step.getLocalImprovementAcceptedCount()).isZero();
            assertThat(step.getScore().raw()).isEqualTo(SimpleScore.ZERO);
            assertEligibleCounters(step, 5L);
          }
        });

    assertThat(solver.solve(problem(8, 12)).getScore()).isEqualTo(SimpleScore.ZERO);

    assertThat(phase.get().getLocalImprovementProbeCount()).isPositive();
    assertThat(phase.get().getLocalImprovementAcceptedCount()).isZero();
    assertAccounting(phase.get(), completed);
  }

  @Test
  void cachedAndUnchangedChildrenDoNotStartLocalImprovementOrInventScoreCalls() {
    var solver =
        solver(
            config(
                    new GeneticAlgorithmPhaseConfig()
                        .withPopulationSize(1)
                        .withLocalImprovementMoveCountLimit(20L)
                        .withMutationOperators(
                            new GeneticAlgorithmMutationOperatorConfig()
                                .withType(GeneticAlgorithmMutationType.SWAP)
                                .withProbability(1.0))
                        .withNoProgressAttemptLimit(7L))
                .withTerminationConfig(
                    new TerminationConfig().withScoreCalculationCountLimit(100L)));
    var phase = recordPhase(solver);
    var completed = recordSteps(solver);

    assertReplay(solver.solve(problem(2, 8)));

    assertThat(completed).hasSize(7);
    assertThat(completed)
        .allSatisfy(
            step -> {
              assertThat(step.getOutcome())
                  .isIn(GeneticAlgorithmOutcome.NO_CHANGE, GeneticAlgorithmOutcome.DUPLICATE);
              assertThat(step.getLocalImprovementProbeCount()).isZero();
              assertThat(step.getLocalImprovementAcceptedCount()).isZero();
            });
    assertThat(phase.get().getLocalImprovementProbeCount()).isZero();
    assertThat(phase.get().getPhaseScoreCalculationCount()).isEqualTo(1);
    assertThat(phase.get().getPhaseMoveEvaluationCount()).isEqualTo(7);
  }

  @Test
  void listImprovementKeepsBestClonesIndependentAndResetsAllStateOnSolverReuse() {
    var solver =
        GeneticAlgorithmListIntegrationTest.solver(
            GeneticAlgorithmListIntegrationTest.config(phase(8L, 45))
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var trace = GeneticAlgorithmListIntegrationTest.trace(solver);
    var localTrace = new ArrayList<List<Long>>();
    var completed = new ArrayList<GeneticAlgorithmStepScope<TestdataListSolution>>();
    var phase = new AtomicReference<GeneticAlgorithmPhaseScope<TestdataListSolution>>();
    var publications = new ArrayList<TestdataListSolution>();
    var snapshots = new ArrayList<List<List<String>>>();
    var scores = new ArrayList<SimpleScore>();
    solver.addEventListener(
        event -> {
          var best = event.getNewBestSolution();
          GeneticAlgorithmListIntegrationTest.assertReplay(best);
          publications.add(best);
          snapshots.add(GeneticAlgorithmListIntegrationTest.assignments(best));
          scores.add(best.getScore());
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataListSolution>) scope;
            assertEligibleCounters(step, 8L);
            completed.add(step);
            localTrace.add(
                List.of(
                    step.getLocalImprovementProbeCount(), step.getLocalImprovementAcceptedCount()));
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataListSolution> scope) {
            phase.set((GeneticAlgorithmPhaseScope<TestdataListSolution>) scope);
          }
        });
    var input = GeneticAlgorithmListIntegrationTest.problem(12, 3);
    var original = GeneticAlgorithmListIntegrationTest.assignments(input);

    var first = solver.solve(input);

    assertThat(phase.get().getLocalImprovementAcceptedCount()).isPositive();
    assertAccounting(phase.get(), completed);
    var firstTrace = List.copyOf(trace);
    var firstLocalTrace = List.copyOf(localTrace);
    var firstCalculations = solver.getSolverScope().getScoreCalculationCount();
    var firstMoves = solver.getSolverScope().getMoveEvaluationCount();
    trace.clear();
    localTrace.clear();
    completed.clear();

    var second = solver.solve(GeneticAlgorithmListIntegrationTest.problem(12, 3));

    assertThat(trace).containsExactlyElementsOf(firstTrace);
    assertThat(localTrace).containsExactlyElementsOf(firstLocalTrace);
    assertThat(solver.getSolverScope().getScoreCalculationCount()).isEqualTo(firstCalculations);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(firstMoves);
    assertAccounting(phase.get(), completed);
    assertThat(GeneticAlgorithmListIntegrationTest.assignments(input)).isEqualTo(original);
    assertThat(GeneticAlgorithmListIntegrationTest.assignments(second))
        .isEqualTo(GeneticAlgorithmListIntegrationTest.assignments(first));
    assertThat(publications).hasSizeGreaterThan(2);
    for (var i = 0; i < publications.size(); i++) {
      var best = publications.get(i);
      assertThat(GeneticAlgorithmListIntegrationTest.assignments(best)).isEqualTo(snapshots.get(i));
      assertThat(best.getScore()).isEqualTo(scores.get(i));
      GeneticAlgorithmListIntegrationTest.assertReplay(best);
      assertThat(best.getEntityList().getFirst()).isNotSameAs(input.getEntityList().getFirst());
      assertThat(best.getValueList().getFirst()).isNotSameAs(input.getValueList().getFirst());
    }
    GeneticAlgorithmListIntegrationTest.assertFreshReplay(first);
    GeneticAlgorithmListIntegrationTest.assertFreshReplay(second);
  }

  @Test
  void problemChangeRebuildsLocalImprovementSelectorsAndResetsProbeAccounting() {
    var solver =
        GeneticAlgorithmListIntegrationTest.solver(
            GeneticAlgorithmListIntegrationTest.config(phase(8L, 24).withPopulationSize(3))
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var queued = new AtomicBoolean();
    var ownerCounts = new ArrayList<Integer>();
    var valueCounts = new ArrayList<Integer>();
    var sessions = new ArrayList<Object>();
    var phases = new ArrayList<GeneticAlgorithmPhaseScope<TestdataListSolution>>();
    var completed = new ArrayList<List<GeneticAlgorithmStepScope<TestdataListSolution>>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataListSolution> scope) {
            var phase = (GeneticAlgorithmPhaseScope<TestdataListSolution>) scope;
            assertThat(phase.getLocalImprovementProbeCount()).isZero();
            assertThat(phase.getLocalImprovementAcceptedCount()).isZero();
            assertThat(phase.getNextStepIndex()).isZero();
            phases.add(phase);
            ownerCounts.add(scope.getWorkingSolution().getEntityList().size());
            valueCounts.add(scope.getWorkingSolution().getValueList().size());
            sessions.add(
                ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession());
            completed.add(new ArrayList<>());
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataListSolution>) scope;
            completed.getLast().add(step);
            assertEligibleCounters(step, 8L);
            var solution = step.getWorkingSolution();
            GeneticAlgorithmListIntegrationTest.assertStructure(solution);
            assertThat(step.getScore().raw())
                .isEqualTo(GeneticAlgorithmListIntegrationTest.replayScore(solution));
            assertThat(
                    ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector())
                        .getSession())
                .isSameAs(sessions.getLast());
            if (phases.size() == 2) {
              assertThat(solution.getEntityList())
                  .extracting(TestdataListEntity::getCode)
                  .containsExactly("0", "1", "9");
              assertThat(solution.getValueList())
                  .extracting(TestdataListValue::getCode)
                  .containsExactly("0", "1", "2", "3", "4", "5", "9");
            }
            // The original selectors must have completed real probes before they are discarded.
            if (step.getLocalImprovementProbeCount() > 0 && queued.compareAndSet(false, true)) {
              solver.addProblemChange(
                  (working, director) -> {
                    working.setEntityList(new ArrayList<>(working.getEntityList()));
                    working.setValueList(new ArrayList<>(working.getValueList()));
                    var value = new TestdataListValue("9");
                    var owner = new TestdataListEntity("9", value);
                    director.addEntity(value, working.getValueList()::add);
                    director.addEntity(owner, working.getEntityList()::add);
                  });
            }
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataListSolution> scope) {
            assertAccounting(
                (GeneticAlgorithmPhaseScope<TestdataListSolution>) scope, completed.getLast());
          }
        });
    var input = GeneticAlgorithmListIntegrationTest.problem(6, 2);

    var result = solver.solve(input);

    assertThat(queued).isTrue();
    assertThat(ownerCounts).containsExactly(2, 3);
    assertThat(valueCounts).containsExactly(6, 7);
    assertThat(phases).hasSize(2);
    assertThat(phases.getFirst().getLocalImprovementProbeCount()).isEqualTo(8);
    assertThat(phases.getLast().getLocalImprovementProbeCount()).isPositive();
    assertThat(completed.getLast()).hasSize(24);
    for (var steps : completed) {
      for (var i = 0; i < steps.size(); i++) {
        assertThat(steps.get(i).getStepIndex()).isEqualTo(i);
      }
    }
    assertThat(sessions.getLast()).isNotSameAs(sessions.getFirst());
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(input.getEntityList()).hasSize(2);
    assertThat(input.getValueList()).hasSize(6);
    assertThat(result.getEntityList()).extracting(TestdataListEntity::getCode).contains("9");
    assertThat(result.getValueList()).extracting(TestdataListValue::getCode).contains("9");
    GeneticAlgorithmListIntegrationTest.assertFreshReplay(result);
  }

  static GeneticAlgorithmPhaseConfig phase(long probeLimit, int stepLimit) {
    return new GeneticAlgorithmPhaseConfig()
        .withPopulationSize(5)
        .withPBestRate(0.8)
        .withLocalImprovementMoveCountLimit(probeLimit)
        .withMutationRateMultiplier(1.0)
        .withMutationOperators(
            new GeneticAlgorithmMutationOperatorConfig()
                .withType(GeneticAlgorithmMutationType.CHANGE)
                .withProbability(1.0))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(stepLimit));
  }

  static void assertEligibleCounters(GeneticAlgorithmStepScope<?> step, long limit) {
    if (step.isSeeding() || step.getOutcome() != GeneticAlgorithmOutcome.EVALUATED) {
      assertThat(step.getLocalImprovementProbeCount()).isZero();
      assertThat(step.getLocalImprovementAcceptedCount()).isZero();
    } else {
      assertThat(step.getLocalImprovementProbeCount()).isEqualTo(limit);
      assertThat(step.getLocalImprovementAcceptedCount()).isBetween(0L, limit);
    }
  }

  static <Solution_> void assertAccounting(
      GeneticAlgorithmPhaseScope<Solution_> phase,
      List<GeneticAlgorithmStepScope<Solution_>> completed) {
    var probes =
        completed.stream()
            .mapToLong(GeneticAlgorithmStepScope::getLocalImprovementProbeCount)
            .sum();
    var accepted =
        completed.stream()
            .mapToLong(GeneticAlgorithmStepScope::getLocalImprovementAcceptedCount)
            .sum();
    assertThat(phase.getLocalImprovementProbeCount()).isEqualTo(probes);
    assertThat(phase.getLocalImprovementAcceptedCount()).isEqualTo(accepted);
    assertThat(phase.getPhaseMoveEvaluationCount()).isEqualTo(completed.size() + probes);
  }

  public static final class FlatConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .reward(SimpleScore.ONE, entity -> 0)
            .asConstraint("Flat")
      };
    }
  }
}
