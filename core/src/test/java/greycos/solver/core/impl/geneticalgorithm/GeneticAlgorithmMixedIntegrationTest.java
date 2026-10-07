package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmMixedIntegrationTest {

  @Test
  void shadowExceptionAbortsTheAttemptWithoutPublishingOrCreditingIt() {
    var solver =
        (DefaultSolver<MixedSolution>)
            SolverFactory.<MixedSolution>create(
                    new SolverConfig()
                        .withSolutionClass(MixedSolution.class)
                        .withEntityClasses(Owner.class, Task.class)
                        .withConstraintProviderClass(MixedConstraints.class)
                        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
                        .withRandomSeed(37L)
                        .withPhases(
                            new GeneticAlgorithmPhaseConfig()
                                .withPopulationSize(5)
                                .withTerminationConfig(
                                    new TerminationConfig().withStepCountLimit(12))))
                .buildSolver();
    var failure = new IllegalStateException("intentional mixed list shadow failure");
    var armed = new AtomicBoolean(true);
    var completed = new AtomicInteger();
    var gaPublications = new AtomicInteger();
    solver.addEventListener(
        event -> {
          if (event.getProducerId().equals(EventProducerId.geneticAlgorithm(0)))
            gaPublications.incrementAndGet();
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<MixedSolution> step) {
            if (armed.compareAndSet(true, false)) Task.SHADOW_FAILURE.set(failure);
          }

          @Override
          public void stepEnded(AbstractStepScope<MixedSolution> step) {
            completed.incrementAndGet();
          }
        });
    var input = problem();
    var original = snapshot(input);

    try {
      assertThatThrownBy(() -> solver.solve(input)).isSameAs(failure);
      assertThat(completed).hasValue(0);
      assertThat(gaPublications).hasValue(0);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
      assertThat(solver.isSolving()).isFalse();
      assertThat(snapshot(input)).isEqualTo(original);
      assertReplay(solver.getSolverScope().getBestSolution());
    } finally {
      Task.SHADOW_FAILURE.remove();
    }

    assertFreshReplay(solver.solve(problem()));
    assertThat(completed).hasValue(12);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(12);
  }

  @Test
  void mixedOwnerAndValueBasicsAndListShadowsReplayThroughThePublicSolver() {
    var solver =
        (DefaultSolver<MixedSolution>)
            SolverFactory.<MixedSolution>create(
                    new SolverConfig()
                        .withSolutionClass(MixedSolution.class)
                        .withEntityClasses(Owner.class, Task.class)
                        .withConstraintProviderClass(MixedConstraints.class)
                        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                        .withRandomSeed(37L)
                        .withPhases(
                            new GeneticAlgorithmPhaseConfig()
                                .withPopulationSize(7)
                                .withTerminationConfig(
                                    new TerminationConfig().withStepCountLimit(80))))
                .buildSolver();
    var completed = new AtomicInteger();
    var evaluated = new AtomicInteger();
    var invalid = new AtomicInteger();
    var published = new ArrayList<MixedSolution>();
    var snapshots = new ArrayList<Snapshot>();
    solver.addEventListener(
        event -> {
          assertReplay(event.getNewBestSolution());
          published.add(event.getNewBestSolution());
          snapshots.add(snapshot(event.getNewBestSolution()));
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          private Object session;

          @Override
          public void phaseStarted(AbstractPhaseScope<MixedSolution> phase) {
            session =
                ((BavetConstraintStreamScoreDirector<?, ?>) phase.getScoreDirector()).getSession();
          }

          @Override
          public void stepEnded(AbstractStepScope<MixedSolution> scope) {
            var step = (GeneticAlgorithmStepScope<MixedSolution>) scope;
            completed.incrementAndGet();
            if (step.getOutcome() == GeneticAlgorithmOutcome.EVALUATED) evaluated.incrementAndGet();
            if (step.getOutcome() == GeneticAlgorithmOutcome.INVALID) {
              invalid.incrementAndGet();
              assertThat(step.getCandidateScore()).isNull();
              assertThat(step.isAdmitted()).isFalse();
            }
            assertThat(step.getScore().isStructurallyFlawed()).isFalse();
            assertThat(step.getScore().raw()).isEqualTo(replayScore(step.getWorkingSolution()));
            assertThat(
                    ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector())
                        .getSession())
                .isSameAs(session);
          }
        });
    var input = problem();
    var original = snapshot(input);

    var result = solver.solve(input);

    assertThat(completed).hasValue(80);
    assertThat(evaluated.get()).isPositive();
    assertThat(invalid.get()).isPositive();
    assertThat(snapshot(input)).isEqualTo(original);
    for (var i = 0; i < published.size(); i++) {
      assertThat(snapshot(published.get(i))).isEqualTo(snapshots.get(i));
      assertReplay(published.get(i));
    }
    assertFreshReplay(result);
  }

  @Test
  void mixedDeclarativeCycleRestoresEveryAssignmentBeforeALaterSuccessfulTrial() {
    var factory = factory();
    try (var director = factory.createScoreDirectorBuilder().build()) {
      var solution = problem();
      director.setWorkingSolution(solution);
      var initialScore = director.calculateScore();
      var workspace = new GeneticAlgorithmWorkspace<>(director, initialScore);
      var initial = workspace.genome();
      var initialLists = initial.lists();
      var original = snapshot(solution);
      var session = director.getSession();
      var basic = initial.toArray();
      var lists = initial.lists();
      var first = solution.tasks.getFirst();
      var second = solution.tasks.get(1);
      var dependencySlot = -1;
      for (var i = 0; i < workspace.slots().size(); i++) {
        var slot = workspace.slots().get(i);
        if (slot.entity() == solution.owners.getFirst()
            && slot.variableDescriptor().getVariableName().equals("start")) basic[i] = 2;
        if (slot.entity() == first) {
          if (slot.variableDescriptor().getVariableName().equals("duration")) basic[i] = 3;
          if (slot.variableDescriptor().getVariableName().equals("dependency")) dependencySlot = i;
        }
      }
      assertThat(dependencySlot).isNotNegative();
      basic[dependencySlot] = second;
      var reversedOwner = 1;
      var firstId = lists[reversedOwner][0];
      lists[reversedOwner][0] = lists[reversedOwner][1];
      lists[reversedOwner][1] = firstId;
      var cycle = new GeneticAlgorithmGenome(basic, lists);

      var rejected = workspace.transition(cycle);

      assertThat(rejected.valid()).isFalse();
      assertThat(rejected.changedAssignmentCount()).isPositive();
      assertThat(director.isLastVariableUpdateSuccessful()).isTrue();
      assertThat(workspace.genome()).isEqualTo(initial);
      assertThat(workspace.score()).isEqualTo(initialScore);
      assertThat(snapshot(solution)).isEqualTo(original);
      assertThat(director.calculateScore()).isEqualTo(initialScore);
      assertThat(replayScore(solution)).isEqualTo(initialScore.raw());
      assertThat(director.getSession()).isSameAs(session);

      // Removing the cycle leaves the simultaneous owner, task and list changes intact.
      basic[dependencySlot] = solution.tasks.get(2);
      var valid = new GeneticAlgorithmGenome(basic, lists);
      assertThat(workspace.transition(valid).valid()).isTrue();
      workspace.scored(director.calculateScore());
      assertThat(snapshot(solution)).isNotEqualTo(original);
      assertThat(workspace.score().raw()).isEqualTo(replayScore(solution));
      assertThat(director.getSession()).isSameAs(session);
      try (var fresh = factory.createScoreDirectorBuilder().build()) {
        fresh.setWorkingSolution(director.cloneWorkingSolution());
        assertThat(fresh.calculateScore()).isEqualTo(workspace.score());
        assertThat(replayScore(fresh.getWorkingSolution())).isEqualTo(workspace.score().raw());
      }

      workspace.restore(initial, initialScore);

      assertThat(snapshot(solution)).isEqualTo(original);
      assertThat(workspace.genome()).isEqualTo(initial);
      assertThat(workspace.score()).isEqualTo(initialScore);
      assertThat(replayScore(solution)).isEqualTo(initialScore.raw());
      assertThat(director.getSession()).isSameAs(session);
      // Later materializations cannot rewrite a population member's list snapshot.
      assertThat(initial.lists()).isDeepEqualTo(initialLists);
    }
  }

  static MixedSolution problem() {
    var solution = new MixedSolution();
    solution.owners = new ArrayList<>(List.of(new Owner("a"), new Owner("b")));
    solution.tasks =
        new ArrayList<>(List.of(new Task("a"), new Task("b"), new Task("c"), new Task("d")));
    solution.owners.getFirst().tasks.addAll(solution.tasks.subList(0, 2));
    solution.owners.getLast().tasks.addAll(solution.tasks.subList(2, 4));
    return solution;
  }

  private static Snapshot snapshot(MixedSolution solution) {
    return new Snapshot(
        solution.owners.stream().map(owner -> owner.start).toList(),
        solution.owners.stream()
            .map(owner -> owner.tasks.stream().map(Task::getCode).toList())
            .toList(),
        solution.tasks.stream().map(task -> task.duration).toList(),
        solution.tasks.stream()
            .map(task -> task.dependency == null ? "-" : task.dependency.getCode())
            .toList());
  }

  private static SimpleScore replayScore(MixedSolution solution) {
    var positions = new IdentityHashMap<Task, Position>();
    for (var owner : solution.owners) {
      for (var i = 0; i < owner.tasks.size(); i++) {
        var task = owner.tasks.get(i);
        var previous = i == 0 ? null : owner.tasks.get(i - 1);
        assertThat(positions.put(task, new Position(owner, previous))).isNull();
        assertThat(task.owner).isSameAs(owner);
        assertThat(task.index).isEqualTo(i);
        assertThat(task.previous).isSameAs(previous);
        assertThat(solution.tasks).anySatisfy(canonical -> assertThat(canonical).isSameAs(task));
      }
    }
    assertThat(positions).hasSize(solution.tasks.size());
    var depths = new IdentityHashMap<Task, Integer>();
    var visiting = Collections.newSetFromMap(new IdentityHashMap<Task, Boolean>());
    var total = 0;
    for (var task : solution.tasks) {
      var depth = replayDepth(task, positions, depths, visiting);
      assertThat(task.depth).isEqualTo(depth);
      total += depth;
    }
    return SimpleScore.of(-total);
  }

  private static int replayDepth(
      Task task, Map<Task, Position> positions, Map<Task, Integer> depths, Set<Task> visiting) {
    if (depths.containsKey(task)) return depths.get(task);
    assertThat(visiting.add(task)).as("No cycle in the genuine dependency graph").isTrue();
    var position = positions.get(task);
    assertThat(position).isNotNull();
    var previousDepth =
        position.previous() == null
            ? 0
            : replayDepth(position.previous(), positions, depths, visiting);
    var dependencyDepth =
        task.dependency == null ? 0 : replayDepth(task.dependency, positions, depths, visiting);
    var depth = position.owner().start + task.duration + Math.max(previousDepth, dependencyDepth);
    visiting.remove(task);
    depths.put(task, depth);
    return depth;
  }

  private static void assertReplay(MixedSolution solution) {
    assertThat(solution.score).isEqualTo(replayScore(solution));
  }

  private static void assertFreshReplay(MixedSolution solution) {
    assertReplay(solution);
    var factory = factory();
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(
          factory.getSolutionDescriptor().getSolutionCloner().cloneSolution(solution));
      assertThat(director.calculateScore().raw()).isEqualTo(replayScore(solution));
      assertThat(replayScore(director.getWorkingSolution())).isEqualTo(solution.score);
    }
  }

  private static BavetConstraintStreamScoreDirectorFactory<MixedSolution, SimpleScore> factory() {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(MixedSolution.class, Owner.class, Task.class),
        new MixedConstraints(),
        EnvironmentMode.NO_ASSERT);
  }

  private record Snapshot(
      List<Integer> starts,
      List<List<String>> lists,
      List<Integer> durations,
      List<String> dependencies) {}

  private record Position(Owner owner, Task previous) {}

  @PlanningSolution
  public static class MixedSolution {
    @PlanningEntityCollectionProperty public List<Owner> owners;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "tasks")
    public List<Task> tasks;

    @ValueRangeProvider(id = "starts")
    public List<Integer> starts = List.of(0, 1, 2);

    @ValueRangeProvider(id = "durations")
    public List<Integer> durations = List.of(1, 2, 3);

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class Owner extends TestdataObject {
    @PlanningVariable(valueRangeProviderRefs = "starts")
    public Integer start = 0;

    @PlanningListVariable(valueRangeProviderRefs = "tasks")
    public List<Task> tasks = new ArrayList<>();

    public Owner() {}

    Owner(String code) {
      super(code);
    }
  }

  @PlanningEntity
  public static class Task extends TestdataObject {
    static final ThreadLocal<RuntimeException> SHADOW_FAILURE = new ThreadLocal<>();

    @PlanningVariable(valueRangeProviderRefs = "durations")
    public Integer duration = 1;

    @PlanningVariable(valueRangeProviderRefs = "tasks", allowsUnassigned = true)
    public Task dependency;

    @InverseRelationShadowVariable(sourceVariableName = "tasks")
    public Owner owner;

    @IndexShadowVariable(sourceVariableName = "tasks")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "tasks")
    public Task previous;

    @ShadowVariable(supplierName = "calculateDepth")
    public Integer depth;

    public Task() {}

    Task(String code) {
      super(code);
    }

    @ShadowSources({"owner.start", "duration", "previous.depth", "dependency.depth"})
    public Integer calculateDepth() {
      var failure = SHADOW_FAILURE.get();
      if (failure != null) throw failure;
      if (owner == null
          || previous != null && previous.depth == null
          || dependency != null && dependency.depth == null) return null;
      return owner.start
          + duration
          + Math.max(
              previous == null ? 0 : previous.depth, dependency == null ? 0 : dependency.depth);
    }
  }

  public static final class MixedConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(Task.class)
            .penalize(SimpleScore.ONE, task -> task.depth)
            .asConstraint("Completion time")
      };
    }
  }
}
