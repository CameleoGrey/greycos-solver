package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsEvaluation;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlnsStructuralScoreTest {
  private static final ThreadLocal<List<AlnsTrialResult<SimpleScore>>> RESULTS =
      ThreadLocal.withInitial(ArrayList::new);

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void cyclicProbesAndRejectedCommitsRestoreShadowState(int workers) {
    try (var fixture = new Fixture(workers)) {
      var context = fixture.context;
      context.beginTrial();
      var target = fixture.target();
      context.setPendingTargets(List.of(target));
      var cyclic = fixture.assignment(target, 2);
      var original = fixture.assignment(target, 0);
      var evaluations = context.evaluateAssignments(List.of(cyclic, original, cyclic));
      assertThat(evaluations.getFirst().isComplete()).isTrue();
      assertThat(evaluations.getFirst().isStructurallyFlawed()).isTrue();
      assertThat(evaluations.get(1).isStructurallyFlawed()).isFalse();
      assertThat(evaluations.getLast()).isEqualTo(evaluations.getFirst());
      fixture.assertOriginal();
      if (workers > 0) assertThat(context.moveEvaluationDiagnostics().generated()).isPositive();

      context.assign(cyclic);
      assertThat(context.score().isStructurallyFlawed()).isTrue();
      assertThatThrownBy(context::commit)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("structurally flawed");
      context.rollback();
      fixture.assertOriginal();

      // The worker baseline must consume the rejected trial's rollback before another batch.
      context.beginTrial();
      context.setPendingTargets(List.of(target));
      assertThat(context.evaluateAssignments(List.of(original, cyclic, original)))
          .extracting(AlnsEvaluation::isStructurallyFlawed)
          .containsExactly(false, true, false);
      context.rollback();
      fixture.assertOriginal();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void builtInRepairFiltersCyclicPreparedCandidates(int workers) {
    try (var fixture = new Fixture(workers)) {
      var context = fixture.context;
      context.beginTrial();
      var target = fixture.target();
      context.setPendingTargets(List.of(target));
      context.destroy(List.of(target));
      var candidates = context.bestAssignments(List.of(target), 10).getFirst();
      assertThat(candidates).isNotEmpty();
      assertThat(candidates)
          .allSatisfy(
              candidate -> assertThat(candidate.evaluation().isStructurallyFlawed()).isFalse());
      assertThat(candidates)
          .noneMatch(
              candidate ->
                  candidate.assignment().value() == fixture.solution.nodes.get(1)
                      || candidate.assignment().value() == fixture.solution.nodes.get(2));
      context.assignEvaluated(candidates.getFirst());
      assertThat(context.score().isStructurallyFlawed()).isFalse();
      context.rollback();
      fixture.assertOriginal();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void cyclicTrialsNeverReachCustomAcceptance(int workers) {
    RESULTS.get().clear();
    var config =
        new SolverConfig()
            .withSolutionClass(CycleSolution.class)
            .withEntityClasses(CycleNode.class)
            .withConstraintProviderClass(CycleConstraints.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(workers == 0 ? "NONE" : Integer.toString(workers))
            .withPhases(
                new AlnsPhaseConfig()
                    .withSelectionPolicyClass(ObservingSelection.class)
                    .withAcceptancePolicyClass(FailIfCalledAcceptance.class)
                    .withDestroyOperators(
                        new AlnsDestroyOperatorConfig()
                            .withId("first")
                            .withCustomClass(FirstDestroy.class)
                            .withMinimumDestroyedCount(1)
                            .withMaximumDestroyedCount(1))
                    .withRepairOperators(
                        new AlnsRepairOperatorConfig()
                            .withId("cycle")
                            .withCustomClass(CyclicRepair.class))
                    .withTerminationConfig(new TerminationConfig().withMoveCountLimit(2L)));
    var solution = SolverFactory.<CycleSolution>create(config).buildSolver().solve(problem());
    assertOriginal(solution);
    assertThat(RESULTS.get())
        .hasSize(2)
        .allSatisfy(
            result -> {
              assertThat(result.outcome()).isEqualTo(AlnsOutcome.REPAIR_FAILED);
              assertThat(result.candidateScore().structuralScore()).isNegative();
              assertThat(result.afterScore()).isEqualTo(result.beforeScore());
            });
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void repairAttemptsReturnOnlyConsistentCandidates(int workers) {
    try (var fixture = new Fixture(0);
        var attempts =
            new AlnsRepairAttemptExecutor<>(
                fixture.director,
                workers == 0 ? null : workers,
                Thread::new,
                EnvironmentMode.FULL_ASSERT,
                2)) {
      var context = fixture.context;
      context.enableReplayRecording();
      context.beginTrial();
      var target = fixture.target();
      context.setPendingTargets(List.of(target));
      context.destroy(List.of(target));
      var result =
          attempts.evaluate(
              context,
              List.of(target),
              new AlnsRepairOperatorConfig()
                  .withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY)
                  .withTopK(3),
              new long[] {11, 19});
      assertThat(result).isNotNull();
      assertThat(result.score().isStructurallyFlawed()).isFalse();
      context.applyRepairJournal(result.journal(), result.score());
      assertThat(context.score().score()).isEqualTo(result.score().raw());
      assertThat(context.score().isStructurallyFlawed()).isFalse();
      context.rollback();
      fixture.assertOriginal();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void repairAttemptWorkersReplayCyclicBaselinesAcrossTrials(int workers) {
    try (var fixture = new Fixture(0);
        var attempts =
            new AlnsRepairAttemptExecutor<>(
                fixture.director,
                workers == 0 ? null : workers,
                Thread::new,
                EnvironmentMode.FULL_ASSERT,
                2)) {
      var context = fixture.context;
      context.enableReplayRecording();
      for (int trial = 0; trial < 3; trial++) {
        context.beginTrial();
        var target = fixture.target();
        context.setPendingTargets(List.of(target));
        context.destroy(List.of(target));
        // A partially applied repair may produce a cycle before the parallel attempts start.
        context.assign(fixture.assignment(target, 2));
        assertThat(context.score().isStructurallyFlawed()).isTrue();
        var result =
            attempts.evaluate(
                context,
                List.of(target),
                new AlnsRepairOperatorConfig()
                    .withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY)
                    .withTopK(3),
                new long[] {11, 19});
        assertThat(result).isNotNull();
        assertThat(result.score().isStructurallyFlawed()).isFalse();
        context.applyRepairJournal(result.journal(), result.score());
        assertThat(context.score().score()).isEqualTo(result.score().raw());
        assertThat(context.score().isStructurallyFlawed()).isFalse();
        context.rollback();
        fixture.assertOriginal();
      }
    }
  }

  @Test
  void structuralValidityPrecedesAssignmentCompleteness() {
    var flawed = new AlnsEvaluation<>(new SimpleScore(-1, 0), 0);
    var incomplete = new AlnsEvaluation<>(SimpleScore.of(-100), 1);
    assertThat(incomplete).isGreaterThan(flawed);
    try (var fixture = new Fixture(0)) {
      fixture.context.beginTrial();
      assertThatThrownBy(
              () ->
                  fixture.context.applyRepairJournal(
                      List.of(), InnerScore.fullyAssigned(flawed.score())))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("structurally flawed");
      fixture.context.rollback();
      fixture.assertOriginal();
    }
  }

  private static CycleSolution problem() {
    var solution = new CycleSolution();
    var root = new CycleNode("root");
    root.pinned = true;
    var first = new CycleNode("first");
    first.previous = root;
    var second = new CycleNode("second");
    second.previous = first;
    solution.nodes = new ArrayList<>(List.of(root, first, second));
    return solution;
  }

  private static void assertOriginal(CycleSolution solution) {
    assertThat(solution.nodes.get(1).previous).isSameAs(solution.nodes.getFirst());
    assertThat(solution.nodes.get(2).previous).isSameAs(solution.nodes.get(1));
    assertThat(solution.nodes).extracting(node -> node.depth).containsExactly(0, 1, 2);
    assertThat(solution.score).isEqualTo(SimpleScore.of(-2));
  }

  private static final class Fixture implements AutoCloseable {
    final CycleSolution solution = problem();
    final BavetConstraintStreamScoreDirector<CycleSolution, SimpleScore> director;
    final DefaultAlnsContext<CycleSolution, SimpleScore> context;

    Fixture(int workers) {
      var descriptor =
          SolutionDescriptor.buildSolutionDescriptor(CycleSolution.class, CycleNode.class);
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<CycleSolution, SimpleScore>(
              descriptor, new CycleConstraints(), EnvironmentMode.FULL_ASSERT);
      director = factory.createScoreDirectorBuilder(EnvironmentMode.FULL_ASSERT).build();
      director.setWorkingSolution(solution);
      director.calculateScore();
      context = new DefaultAlnsContext<>(director, new Random(1), () -> false);
      context.configureMoveThreads(
          workers == 0 ? null : workers, 2, Thread::new, 0, EnvironmentMode.FULL_ASSERT);
    }

    AlnsTarget<CycleSolution> target() {
      return context.targets().stream()
          .filter(target -> target.entity() == solution.nodes.get(1))
          .findFirst()
          .orElseThrow();
    }

    AlnsAssignment<CycleSolution> assignment(AlnsTarget<CycleSolution> target, int index) {
      return new AlnsAssignment<>(target, target.entity(), solution.nodes.get(index), -1);
    }

    void assertOriginal() {
      assertThat(director.calculateScore().isStructurallyFlawed()).isFalse();
      AlnsStructuralScoreTest.assertOriginal(solution);
    }

    @Override
    public void close() {
      try {
        context.close();
      } finally {
        director.close();
      }
    }
  }

  @PlanningSolution
  public static class CycleSolution {
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<CycleNode> nodes;
    @PlanningScore public SimpleScore score;

    public CycleSolution() {}
  }

  @PlanningEntity
  public static class CycleNode {
    @PlanningId public String id;
    @PlanningPin public boolean pinned;

    @PlanningVariable(allowsUnassigned = true)
    public CycleNode previous;

    @ShadowVariable(supplierName = "calculateDepth")
    public Integer depth;

    public CycleNode() {}

    CycleNode(String id) {
      this.id = id;
    }

    @ShadowSources("previous.depth")
    public Integer calculateDepth() {
      return previous == null ? 0 : previous.depth == null ? null : previous.depth + 1;
    }
  }

  public static final class CycleConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(CycleNode.class)
            .penalize(SimpleScore.ONE, node -> node.previous == null ? 0 : 1)
            .asConstraint("Assigned predecessors")
      };
    }
  }

  public static final class FirstDestroy
      implements AlnsDestroyOperator<CycleSolution, SimpleScore> {
    @Override
    public List<AlnsTarget<CycleSolution>> select(
        AlnsContext<CycleSolution, SimpleScore> context, int size) {
      return List.of(
          context.targets().stream()
              .filter(target -> ((CycleNode) target.entity()).id.equals("first"))
              .findFirst()
              .orElseThrow());
    }
  }

  public static final class CyclicRepair implements AlnsRepairOperator<CycleSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<CycleSolution, SimpleScore> context, List<AlnsTarget<CycleSolution>> pending) {
      var target = pending.getFirst();
      var solution = context.workingSolution();
      var cyclic = new AlnsAssignment<>(target, target.entity(), solution.nodes.get(2), -1);
      var original = new AlnsAssignment<>(target, target.entity(), solution.nodes.getFirst(), -1);
      assertThat(
              context
                  .evaluateAssignments(List.of(cyclic, original))
                  .getFirst()
                  .isStructurallyFlawed())
          .isTrue();
      context.assign(cyclic);
      return true;
    }
  }

  public static final class FailIfCalledAcceptance implements AlnsAcceptancePolicy<SimpleScore> {
    @Override
    public boolean isAccepted(SimpleScore current, SimpleScore candidate, RandomGenerator random) {
      throw new AssertionError("A structurally flawed trial reached the user acceptance policy.");
    }
  }

  public static final class ObservingSelection implements AlnsSelectionPolicy<SimpleScore> {
    @Override
    public AlnsOperatorPair select(List<AlnsOperatorPair> eligible, RandomGenerator random) {
      return eligible.getFirst();
    }

    @Override
    public void update(AlnsTrialResult<SimpleScore> result) {
      RESULTS.get().add(result);
    }
  }
}
