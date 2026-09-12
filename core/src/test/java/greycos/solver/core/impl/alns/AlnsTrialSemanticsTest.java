package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlnsTrialSemanticsTest {
  private static final ThreadLocal<List<AlnsTrialResult<SimpleScore>>> RESULTS =
      ThreadLocal.withInitial(ArrayList::new);
  private static final ThreadLocal<Integer> REPAIR_CALLS = ThreadLocal.withInitial(() -> 0);
  private static final ThreadLocal<Integer> CLOSED = ThreadLocal.withInitial(() -> 0);

  @BeforeEach
  void reset() {
    RESULTS.get().clear();
    REPAIR_CALLS.set(0);
    CLOSED.set(0);
  }

  @Test
  void rejectedCandidateLeavesIncumbentAndCompletesTrials() {
    var problem = TestdataSolution.generateSolution(2, 2);
    var solution =
        SolverFactory.<TestdataSolution>create(config(WorsenRepair.class, 3))
            .buildSolver()
            .solve(problem);
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(solution.getEntityList().get(0).getValue())
        .isNotEqualTo(solution.getEntityList().get(1).getValue());
    assertThat(RESULTS.get())
        .hasSize(3)
        .allSatisfy(
            result -> {
              assertThat(result.outcome()).isEqualTo(AlnsOutcome.REJECTED);
              assertThat(result.candidateScore()).isEqualTo(SimpleScore.of(-1));
              assertThat(result.beforeScore()).isEqualTo(result.afterScore());
            });
  }

  @Test
  void failedRepairStillAdvancesMoveCountTermination() {
    var solution =
        SolverFactory.<TestdataSolution>create(config(FailedRepair.class, 3))
            .buildSolver()
            .solve(TestdataSolution.generateSolution(2, 2));
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(RESULTS.get())
        .hasSize(3)
        .allSatisfy(result -> assertThat(result.outcome()).isEqualTo(AlnsOutcome.REPAIR_FAILED));
  }

  @Test
  void repairBudgetRestoresIncumbentAndAdvancesTrials() {
    var config = config(WorsenRepair.class, 3);
    ((AlnsPhaseConfig) config.getPhaseConfigList().getFirst()).withRepairScoreCalculationLimit(1L);
    var solution =
        SolverFactory.<TestdataSolution>create(config)
            .buildSolver()
            .solve(TestdataSolution.generateSolution(2, 2));
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(RESULTS.get())
        .hasSize(3)
        .allSatisfy(result -> assertThat(result.outcome()).isEqualTo(AlnsOutcome.REPAIR_FAILED));
  }

  @Test
  void unchangedReconstructionDoesNotEarnAcceptanceReward() {
    SolverFactory.<TestdataSolution>create(config(OriginalRepair.class, 2))
        .buildSolver()
        .solve(TestdataSolution.generateSolution(2, 2));
    assertThat(RESULTS.get())
        .hasSize(2)
        .allSatisfy(result -> assertThat(result.outcome()).isEqualTo(AlnsOutcome.NO_CHANGE));
  }

  @Test
  void acceptedRepairIsConstructedOnce() {
    var problem = TestdataSolution.generateSolution(2, 2);
    problem.getEntityList().forEach(entity -> entity.setValue(problem.getValueList().getFirst()));
    var solution =
        SolverFactory.<TestdataSolution>create(config(ImproveRepair.class, 1))
            .buildSolver()
            .solve(problem);
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(REPAIR_CALLS.get()).isEqualTo(1);
    assertThat(RESULTS.get())
        .singleElement()
        .satisfies(result -> assertThat(result.outcome()).isEqualTo(AlnsOutcome.NEW_BEST));
  }

  @Test
  void partialInitializationClosesAlreadyCreatedOperators() {
    var config = config(ConstructorFailureRepair.class, 1);
    ((AlnsPhaseConfig) config.getPhaseConfigList().getFirst())
        .getDestroyOperatorConfigList()
        .getFirst()
        .setCustomClass(CloseableDestroy.class);
    assertThatThrownBy(
            () ->
                SolverFactory.<TestdataSolution>create(config)
                    .buildSolver()
                    .solve(TestdataSolution.generateSolution(2, 2)))
        .isInstanceOf(RuntimeException.class);
    assertThat(CLOSED.get()).isEqualTo(1);
  }

  @Test
  void mutatingDestroySelectorIsRejectedBeforeRepair() {
    var config = config(ImproveRepair.class, 1);
    ((AlnsPhaseConfig) config.getPhaseConfigList().getFirst())
        .getDestroyOperatorConfigList()
        .getFirst()
        .setCustomClass(MutatingDestroy.class);
    assertThatThrownBy(
            () ->
                SolverFactory.<TestdataSolution>create(config)
                    .buildSolver()
                    .solve(TestdataSolution.generateSolution(2, 2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("leave the incumbent unchanged");
    assertThat(REPAIR_CALLS.get()).isZero();
  }

  private SolverConfig config(Class<? extends AlnsRepairOperator> repairClass, int trialCount) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(CollisionConstraints.class)
        .withMoveThreadCount("NONE")
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withPhases(
            new AlnsPhaseConfig()
                .withSelectionPolicyClass(ObservingSelection.class)
                .withAcceptanceType(AlnsAcceptanceType.HILL_CLIMBING)
                .withDestroyOperators(
                    new AlnsDestroyOperatorConfig()
                        .withId("one")
                        .withCustomClass(FirstDestroy.class)
                        .withMinimumDestroyedCount(1)
                        .withMaximumDestroyedCount(1))
                .withRepairOperators(
                    new AlnsRepairOperatorConfig().withId("repair").withCustomClass(repairClass))
                .withTerminationConfig(
                    new TerminationConfig().withMoveCountLimit((long) trialCount)));
  }

  public static final class CollisionConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachUniquePair(TestdataEntity.class, Joiners.equal(TestdataEntity::getValue))
            .penalize(SimpleScore.ONE)
            .asConstraint("Same value")
      };
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

  public static final class WorsenRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      var target = pending.getFirst();
      var value = context.workingSolution().getEntityList().get(1).getValue();
      context.assign(
          context.assignments(target).stream()
              .filter(assignment -> assignment.value() == value)
              .findFirst()
              .orElseThrow());
      return true;
    }
  }

  public static final class FailedRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      return false;
    }
  }

  public static final class OriginalRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      var target = pending.getFirst();
      context.assign(
          context.assignments(target).stream()
              .filter(assignment -> assignment.value() == target.value())
              .findFirst()
              .orElseThrow());
      return true;
    }
  }

  public static final class ImproveRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      REPAIR_CALLS.set(REPAIR_CALLS.get() + 1);
      context.assign(context.assignments(pending.getFirst()).getLast());
      return true;
    }
  }

  public static final class ObservingSelection implements AlnsSelectionPolicy<SimpleScore> {
    @Override
    public AlnsOperatorPair select(List<AlnsOperatorPair> eligiblePairs, RandomGenerator random) {
      return eligiblePairs.getFirst();
    }

    @Override
    public void update(AlnsTrialResult<SimpleScore> result) {
      RESULTS.get().add(result);
    }
  }

  public static final class CloseableDestroy
      implements AlnsDestroyOperator<TestdataSolution, SimpleScore>, AutoCloseable {
    @Override
    public List<AlnsTarget<TestdataSolution>> select(
        AlnsContext<TestdataSolution, SimpleScore> context, int size) {
      return List.of(context.targets().getFirst());
    }

    @Override
    public void close() {
      CLOSED.set(CLOSED.get() + 1);
    }
  }

  public static final class ConstructorFailureRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    public ConstructorFailureRepair() {
      throw new IllegalStateException("Intentional constructor failure");
    }

    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      throw new AssertionError();
    }
  }

  public static final class MutatingDestroy
      implements AlnsDestroyOperator<TestdataSolution, SimpleScore> {
    @Override
    public List<AlnsTarget<TestdataSolution>> select(
        AlnsContext<TestdataSolution, SimpleScore> context, int size) {
      var target = context.targets().getFirst();
      context.assign(context.assignments(target).getLast());
      return List.of(target);
    }
  }
}
