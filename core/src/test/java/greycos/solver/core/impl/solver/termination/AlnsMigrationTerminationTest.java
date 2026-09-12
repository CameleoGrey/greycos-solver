package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.alns.DefaultAlnsPhase;
import greycos.solver.core.impl.islandmodel.GlobalCompareListener;
import greycos.solver.core.impl.islandmodel.IslandModelConfig;
import greycos.solver.core.impl.islandmodel.SharedGlobalState;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlnsMigrationTerminationTest {
  private static final ThreadLocal<Harness> CURRENT = new ThreadLocal<>();

  @ParameterizedTest
  @CsvSource({
    "true, false, REJECTED",
    "false, true, REJECTED",
    "true, true, REJECTED",
    "true, false, REPAIR_FAILED",
    "false, true, REPAIR_FAILED",
    "true, true, REPAIR_FAILED",
    "true, false, NO_CHANGE",
    "false, true, NO_CHANGE",
    "true, true, NO_CHANGE"
  })
  void adoptedImprovementPostponesTerminationWithoutCompletingATrial(
      boolean solverThreshold, boolean phaseThreshold, AlnsOutcome followingOutcome) {
    var harness = new Harness(solverThreshold, phaseThreshold, followingOutcome, false);
    CURRENT.set(harness);
    try {
      // Reuse the same solver and phase to exercise initialization and cleanup of local history.
      for (int run = 0; run < 2; run++) {
        harness.reset(10_000L * (run + 1));
        var solution = harness.solver.solve(problem());
        assertThat(solution.getScore()).isEqualTo(SimpleScore.of(11));
        assertTrialAccounting(harness, AlnsOutcome.NEW_BEST, 1);
      }
    } finally {
      CURRENT.remove();
      harness.solver.getSolverScope().getScoreDirector().close();
    }
  }

  @Test
  void adoptionBeforeFirstTrialDoesNotEmitAnUnmatchedLifecycleEvent() {
    var harness = new Harness(true, true, AlnsOutcome.REJECTED, true);
    CURRENT.set(harness);
    try {
      harness.reset(10_000L);
      var solution = harness.solver.solve(problem());
      assertThat(solution.getScore()).isEqualTo(SimpleScore.of(11));
      assertTrialAccounting(harness, AlnsOutcome.NO_CHANGE, 2);
      assertThat(harness.results.getFirst().beforeScore()).isEqualTo(SimpleScore.ONE);
      assertThat(harness.results.getFirst().bestBeforeScore()).isEqualTo(SimpleScore.ONE);
    } finally {
      CURRENT.remove();
      harness.solver.getSolverScope().getScoreDirector().close();
    }
  }

  private static void assertTrialAccounting(
      Harness harness, AlnsOutcome firstOutcome, int expectedAdoptions) {
    assertThat(harness.results)
        .extracting(AlnsTrialResult::outcome)
        .containsExactly(firstOutcome, harness.followingOutcome, harness.followingOutcome);
    assertThat(harness.results).extracting(AlnsTrialResult::trialIndex).containsExactly(0L, 1L, 2L);
    assertThat(harness.results.subList(1, 3))
        .allSatisfy(
            result -> {
              assertThat(result.beforeScore()).isEqualTo(SimpleScore.of(11));
              assertThat(result.bestBeforeScore()).isEqualTo(SimpleScore.of(11));
              assertThat(result.afterScore()).isEqualTo(SimpleScore.of(11));
            });
    assertThat(harness.started).isEqualTo(3);
    assertThat(harness.ended).isEqualTo(3);
    assertThat(harness.acceptanceSteps).isEqualTo(3);
    assertThat(harness.adoptions).isEqualTo(expectedAdoptions);
    assertThat(harness.solver.getMoveEvaluationCount()).isEqualTo(3);
  }

  private static TestdataSolution problem() {
    var problem = new TestdataSolution("migration");
    problem.setValueList(
        List.of(new TestdataValue("0"), new TestdataValue("1"), new TestdataValue("11")));
    problem.setEntityList(List.of(new TestdataEntity("entity", problem.getValueList().getFirst())));
    return problem;
  }

  private static final class Harness {
    private final AtomicLong now = new AtomicLong();
    private final DefaultSolver<TestdataSolution> solver;
    private final PhaseTermination<TestdataSolution> phaseTermination;
    private final UniversalTermination<TestdataSolution> globalTermination;
    private final AlnsOutcome followingOutcome;
    private final List<AlnsTrialResult<SimpleScore>> results = new ArrayList<>();
    private long start;
    private int repairCalls;
    private int started;
    private int ended;
    private int acceptanceSteps;
    private int adoptions;

    Harness(
        boolean solverThreshold,
        boolean phaseThreshold,
        AlnsOutcome followingOutcome,
        boolean initialAdoption) {
      this.followingOutcome = followingOutcome;
      var clock = mock(Clock.class);
      when(clock.millis()).thenAnswer(ignored -> now.get());
      var config =
          PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
              .withEasyScoreCalculatorClass(ValueScoreCalculator.class)
              .withMoveThreadCount("NONE")
              .withEnvironmentMode(EnvironmentMode.NO_ASSERT);
      config.setClock(clock);
      var infrastructure =
          (DefaultSolver<TestdataSolution>)
              SolverFactory.<TestdataSolution>create(config).buildSolver();
      var plumbing = new BasicPlumbingTermination<TestdataSolution>(false);
      globalTermination =
          solverThreshold ? UniversalTermination.or(plumbing, threshold(clock)) : plumbing;
      var bridge = PhaseTermination.bridge(globalTermination);
      phaseTermination =
          phaseThreshold ? UniversalTermination.or(bridge, threshold(clock)) : bridge;
      var alnsConfig =
          new AlnsPhaseConfig()
              .withSelectionPolicyClass(ObservingSelection.class)
              .withAcceptancePolicyClass(ObservingAcceptance.class)
              .withDestroyOperators(
                  new AlnsDestroyOperatorConfig()
                      .withId("destroy")
                      .withCustomClass(FirstDestroy.class)
                      .withMinimumDestroyedCount(1)
                      .withMaximumDestroyedCount(1))
              .withRepairOperators(
                  new AlnsRepairOperatorConfig()
                      .withId("repair")
                      .withCustomClass(ScriptedRepair.class));
      var recaller = infrastructure.getBestSolutionRecaller();
      var phase =
          new DefaultAlnsPhase.Builder<>(0, "", phaseTermination, alnsConfig, recaller).build();
      solver =
          new DefaultSolver<>(
              EnvironmentMode.NO_ASSERT,
              infrastructure::getRandomSource,
              recaller,
              plumbing,
              globalTermination,
              List.of(phase),
              infrastructure.getSolverScope(),
              "NONE");
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void phaseStarted(AbstractPhaseScope<TestdataSolution> phaseScope) {
              if (initialAdoption) {
                queueMigrant(
                    new AlnsStepScope<>((AlnsPhaseScope<TestdataSolution>) phaseScope), "1");
              }
            }

            @Override
            public void stepStarted(AbstractStepScope<TestdataSolution> step) {
              started++;
            }

            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> step) {
              ended++;
              assertThat(((AlnsStepScope<TestdataSolution>) step).getTrialResult()).isNotNull();
              if (step.getStepIndex() == 0) {
                now.set(start + 500);
                queueMigrant(step, "11");
              } else {
                now.set(start + (step.getStepIndex() == 1 ? 1500 : 1501));
                assertThat(phaseTermination.isPhaseTerminated(step.getPhaseScope()))
                    .isEqualTo(step.getStepIndex() == 2);
              }
            }
          });
    }

    private static void queueMigrant(AbstractStepScope<TestdataSolution> step, String valueCode) {
      var migrant = step.getScoreDirector().cloneWorkingSolution();
      migrant
          .getEntityList()
          .getFirst()
          .setValue(
              migrant.getValueList().stream()
                  .filter(value -> value.getCode().equals(valueCode))
                  .findFirst()
                  .orElseThrow());
      var global = new SharedGlobalState<TestdataSolution>();
      global.tryUpdate(
          migrant, InnerScore.fullyAssigned(SimpleScore.of(Integer.parseInt(valueCode))));
      new GlobalCompareListener<>(
              global, IslandModelConfig.builder().withReceiveGlobalUpdateFrequency(1).build(), 0)
          .stepEnded(step);
    }

    private static UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<TestdataSolution>
        threshold(Clock clock) {
      return new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<>(
          1000L, SimpleScore.of(7), clock);
    }

    void reset(long start) {
      this.start = start;
      now.set(start);
      repairCalls = started = ended = acceptanceSteps = adoptions = 0;
      results.clear();
    }
  }

  public static final class ValueScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(
          solution.getEntityList().stream()
              .filter(entity -> entity.getValue() != null)
              .mapToInt(entity -> Integer.parseInt(entity.getValue().getCode()))
              .sum());
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

  public static final class ScriptedRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      var harness = CURRENT.get();
      var first = harness.repairCalls++ == 0;
      if (harness.repairCalls == 2) {
        harness.now.set(harness.start + 1001);
        // A rejection after adoption must not lose the imported improvement's history.
        assertThat(harness.globalTermination.isSolverTerminated(harness.solver.getSolverScope()))
            .isFalse();
        context.checkTermination();
      }
      if (!first && harness.followingOutcome == AlnsOutcome.REPAIR_FAILED) {
        return false;
      }
      var code = first ? "1" : harness.followingOutcome == AlnsOutcome.NO_CHANGE ? "11" : "0";
      context.assign(
          context.assignments(pending.getFirst()).stream()
              .filter(assignment -> ((TestdataValue) assignment.value()).getCode().equals(code))
              .findFirst()
              .orElseThrow());
      return true;
    }
  }

  public static final class ObservingSelection implements AlnsSelectionPolicy<SimpleScore> {
    @Override
    public AlnsOperatorPair select(List<AlnsOperatorPair> pairs, RandomGenerator random) {
      return pairs.getFirst();
    }

    @Override
    public void update(AlnsTrialResult<SimpleScore> result) {
      CURRENT.get().results.add(result);
    }
  }

  public static final class ObservingAcceptance implements AlnsAcceptancePolicy<SimpleScore> {
    @Override
    public boolean isAccepted(SimpleScore current, SimpleScore candidate, RandomGenerator random) {
      return candidate.compareTo(current) >= 0;
    }

    @Override
    public void stepEnded(SimpleScore resultingIncumbent) {
      CURRENT.get().acceptanceSteps++;
    }

    @Override
    public void incumbentChanged(SimpleScore score) {
      assertThat(score).isIn(SimpleScore.ONE, SimpleScore.of(11));
      CURRENT.get().adoptions++;
    }
  }
}
