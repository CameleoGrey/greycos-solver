package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.api.solver.event.FirstInitializedSolutionEvent;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests the GA through the public factory and the ordinary solver lifecycle. */
@Timeout(30)
class GeneticAlgorithmIntegrationTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void soleMoveCountTerminationCountsEveryCompletedAttempt(boolean phaseLocal) {
    var config = config(new GeneticAlgorithmPhaseConfig().withPopulationSize(5));
    var termination = new TerminationConfig().withMoveCountLimit(14L);
    if (phaseLocal) {
      config.getPhaseConfigList().getFirst().setTerminationConfig(termination);
    } else {
      config.setTerminationConfig(termination);
    }
    var solver = solver(config);
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);

    var result = solver.solve(problem(4, 8));

    assertThat(completed).hasSize(14);
    assertThat(completed)
        .extracting(AbstractStepScope::getStepIndex)
        .containsExactlyElementsOf(IntStream.range(0, 14).boxed().toList());
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(14);
    assertThat(phase.get().getPhaseMoveEvaluationCount()).isEqualTo(14);
    assertThat(completed.stream().filter(GeneticAlgorithmStepScope::isSeeding)).hasSize(4);
    assertThat(
            completed.stream()
                .filter(step -> !step.isSeeding())
                .collect(
                    java.util.stream.Collectors.groupingBy(
                        GeneticAlgorithmStepScope::getGeneration,
                        java.util.stream.Collectors.counting()))
                .values())
        .containsExactlyInAnyOrder(5L, 5L);
    assertReplay(result);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void zeroMoveLimitDoesNotCreditInputCaptureOrStartAnAttempt(boolean phaseLocal) {
    var config = config(new GeneticAlgorithmPhaseConfig().withPopulationSize(5));
    var termination = new TerminationConfig().withMoveCountLimit(0L);
    if (phaseLocal) {
      config.getPhaseConfigList().getFirst().setTerminationConfig(termination);
    } else {
      config.setTerminationConfig(termination);
    }
    var solver = solver(config);
    var completed = recordSteps(solver);

    var result = solver.solve(problem(3, 6));

    assertThat(completed).isEmpty();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
    assertThat(assignments(result)).containsOnly("0");
    assertReplay(result);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3, 8})
  void phaseStepLimitIncludesSeedingAndCanStopInsideInitialization(int limit) {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(limit))));
    var completed = recordSteps(solver);

    assertReplay(solver.solve(problem(4, 8)));

    assertThat(completed).hasSize(limit);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(limit);
    assertThat(completed.stream().filter(GeneticAlgorithmStepScope::isSeeding))
        .hasSize(Math.min(limit, 4));
  }

  @Test
  void duplicatePopulationStopsUnderSoleScoreCountTerminationWithoutInventedCalculations() {
    var solver =
        solver(
            config(
                    new GeneticAlgorithmPhaseConfig()
                        .withPopulationSize(1)
                        .withMutationOperators(
                            new GeneticAlgorithmMutationOperatorConfig()
                                .withType(GeneticAlgorithmMutationType.SWAP)
                                .withProbability(1.0))
                        .withNoProgressAttemptLimit(9L))
                .withTerminationConfig(
                    new TerminationConfig().withScoreCalculationCountLimit(100L)));
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);

    var result = solver.solve(problem(2, 5));

    assertThat(completed).hasSize(9);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(9);
    // Capture may calculate the initial fitness; cached offspring add no score calculations.
    assertThat(phase.get().getPhaseScoreCalculationCount()).isEqualTo(1);
    assertThat(phase.get().getPopulationSize()).isEqualTo(1);
    assertThat(phase.get().getDistinctPopulationSize()).isEqualTo(1);
    assertThat(phase.get().getNoProgressAttemptCount()).isEqualTo(9);
    assertThat(phase.get().getTerminationReason()).contains("no fresh valid offspring");
    assertThat(completed)
        .allSatisfy(
            step -> {
              assertThat(step.getOutcome().toString()).isIn("DUPLICATE", "NO_CHANGE");
              assertThat(step.getChangedAssignmentCount()).isZero();
            });
    assertReplay(result);
  }

  @Test
  void singletonRangesExitWithoutAttemptingUnsupportedMutations() {
    var solver =
        solver(
            config(new GeneticAlgorithmPhaseConfig().withPopulationSize(4))
                .withTerminationConfig(
                    new TerminationConfig().withScoreCalculationCountLimit(100L)));
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);

    assertReplay(solver.solve(problem(1, 5)));

    assertThat(completed).isEmpty();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
    assertThat(phase.get().getTerminationReason()).contains("no movable assignments");
  }

  @Test
  void cachedCandidateFitnessRemainsSeparateFromWorkspaceScore() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(32)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(31))));
    var phase = recordPhase(solver);
    var calculationsBefore = new AtomicLong();
    var assignmentsBefore = new AtomicReference<List<String>>();
    var sawDifferentCachedScore = new AtomicBoolean();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            calculationsBefore.set(scope.getScoreDirector().getCalculationCount());
            assignmentsBefore.set(assignments(scope.getWorkingSolution()));
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
            if (step.getOutcome() == GeneticAlgorithmOutcome.DUPLICATE) {
              assertThat(step.getScoreDirector().getCalculationCount())
                  .isEqualTo(calculationsBefore.get());
              assertThat(assignments(step.getWorkingSolution())).isEqualTo(assignmentsBefore.get());
              assertThat(step.getScore()).isEqualTo(step.getBeforeScore());
              if (!step.getCandidateScore().equals(step.getScore())) {
                sawDifferentCachedScore.set(true);
              }
            }
          }
        });

    assertReplay(solver.solve(problem(2, 1)));

    assertThat(sawDifferentCachedScore).isTrue();
    assertThat(phase.get().getPopulationSize()).isEqualTo(32);
    assertThat(phase.get().getDistinctPopulationSize()).isEqualTo(2);
    assertThat(phase.get().getPhaseScoreCalculationCount()).isEqualTo(2);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(31);
  }

  @Test
  void constructionGeneticAlgorithmAndLocalSearchKeepTheirPhaseIdentities() {
    var config =
        config(new GeneticAlgorithmPhaseConfig())
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(14)),
                new LocalSearchPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)));
    var solver = solver(config);
    var phaseIds = new ArrayList<EventProducerId>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            phaseIds.add(scope.getPhaseId());
          }

          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            if (scope.getPhaseIndex() > 0) {
              assertThat(scope.getStartingScore().isFullyAssigned()).isTrue();
              assertThat(scope.getWorkingSolution().getScore())
                  .isEqualTo(scope.getSolverScope().getBestSolution().getScore());
            }
          }
        });
    var input = problem(4, 8);
    input.getEntityList().forEach(entity -> entity.setValue(null));

    assertReplay(solver.solve(input));

    assertThat(phaseIds)
        .containsExactly(
            EventProducerId.constructionHeuristic(0),
            EventProducerId.geneticAlgorithm(1),
            EventProducerId.localSearch(2));
  }

  @Test
  void solverManagerPublishesFirstInitializationBeforeTheGeneticAlgorithm() throws Exception {
    var config =
        config(new GeneticAlgorithmPhaseConfig())
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(3)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(5)));
    var initialized = new AtomicReference<FirstInitializedSolutionEvent<TestdataSolution>>();
    var input = problem(3, 5);
    input.getEntityList().forEach(entity -> entity.setValue(null));

    try (var manager = SolverManager.<TestdataSolution>create(config)) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("ga-initialization")
              .withProblemFinder(id -> input)
              .withFirstInitializedSolutionEventConsumer(initialized::set)
              .run();
      assertReplay(job.getFinalBestSolution());
      assertThat(initialized.get()).isNotNull();
      assertThat(initialized.get().producerId())
          .isEqualTo(EventProducerId.constructionHeuristic(0));
      assertThat(initialized.get().isTerminatedEarly()).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = EnvironmentMode.class,
      names = {"NO_ASSERT", "PHASE_ASSERT", "STEP_ASSERT", "FULL_ASSERT", "TRACKED_FULL_ASSERT"})
  void phaseEnvironmentOverrideObtainsThePreparedDirector(EnvironmentMode mode) {
    var phaseConfig =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(5)
            .withEnvironmentMode(mode)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(14));
    var solver = solver(config(phaseConfig));
    var completed = recordSteps(solver);

    assertReplay(solver.solve(problem(4, 8)));

    assertThat(completed).hasSize(14);
    assertThat(solver.getPhaseList().getFirst().getEnvironmentMode()).isEqualTo(mode);
  }

  @Test
  void warmStartRequiresInitializedInput() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))));
    var input = problem(2, 3);
    input.getEntityList().getFirst().setValue(null);

    assertThatThrownBy(() -> solver.solve(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("initial");
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
  }

  @Test
  void explicitNoneOverridesSolverMoveThreads() {
    var solver =
        solver(
            config(
                    new GeneticAlgorithmPhaseConfig()
                        .withMoveThreadCount("NONE")
                        .withPopulationSize(3)
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(5)))
                .withMoveThreadCount("2"));

    assertReplay(solver.solve(problem(3, 5)));
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(5);
  }

  @Test
  void defaultSolverPhasesRemainConstructionAndLocalSearch() {
    var config = config(new GeneticAlgorithmPhaseConfig());
    config.setPhaseConfigList(null);
    config.withTerminationConfig(new TerminationConfig().withMoveCountLimit(10L));
    var solver = solver(config);

    assertThat(solver.getPhaseList()).extracting(Phase::getEventProducerIdSupplier).hasSize(2);
    assertThat(solver.getPhaseList().get(0).getEventProducerIdSupplier().apply(0))
        .isEqualTo(EventProducerId.constructionHeuristic(0));
    assertThat(solver.getPhaseList().get(1).getEventProducerIdSupplier().apply(1))
        .isEqualTo(EventProducerId.localSearch(1));
  }

  static SolverConfig config(GeneticAlgorithmPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(NumberConstraints.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withRandomSeed(37L)
        .withPhases(phase);
  }

  static DefaultSolver<TestdataSolution> solver(SolverConfig config) {
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  static TestdataSolution problem(int valueCount, int entityCount) {
    var problem = new TestdataSolution("numbers");
    var values = IntStream.range(0, valueCount).mapToObj(i -> new TestdataValue("" + i)).toList();
    problem.setValueList(new ArrayList<>(values));
    problem.setEntityList(
        new ArrayList<>(
            IntStream.range(0, entityCount)
                .mapToObj(i -> new TestdataEntity("entity-" + i, values.getFirst()))
                .toList()));
    return problem;
  }

  static List<String> assignments(TestdataSolution solution) {
    return solution.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList();
  }

  static void assertReplay(TestdataSolution result) {
    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    assertThat(result.getScore())
        .isEqualTo(
            SimpleScore.of(
                result.getEntityList().stream()
                    .mapToInt(entity -> Integer.parseInt(entity.getValue().getCode()))
                    .sum()));
  }

  static List<GeneticAlgorithmStepScope<TestdataSolution>> recordSteps(
      DefaultSolver<TestdataSolution> solver) {
    var steps = new ArrayList<GeneticAlgorithmStepScope<TestdataSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> stepScope) {
            if (stepScope instanceof GeneticAlgorithmStepScope<TestdataSolution> step) {
              assertThat(step.getScore().raw())
                  .isEqualTo(
                      SimpleScore.of(
                          step.getWorkingSolution().getEntityList().stream()
                              .mapToInt(entity -> Integer.parseInt(entity.getValue().getCode()))
                              .sum()));
              steps.add(step);
            }
          }
        });
    return steps;
  }

  static AtomicReference<GeneticAlgorithmPhaseScope<TestdataSolution>> recordPhase(
      DefaultSolver<TestdataSolution> solver) {
    var phase = new AtomicReference<GeneticAlgorithmPhaseScope<TestdataSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
            if (phaseScope instanceof GeneticAlgorithmPhaseScope<TestdataSolution> scope) {
              phase.set(scope);
            }
          }
        });
    return phase;
  }

  public static final class NumberConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .reward(SimpleScore.ONE, entity -> Integer.parseInt(entity.getValue().getCode()))
            .asConstraint("Assigned number")
      };
    }
  }
}
