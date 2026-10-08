package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmLocalImprovementIntegrationTest.assertAccounting;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmLocalImprovementIntegrationTest.assertEligibleCounters;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmLocalImprovementIntegrationTest.phase;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.MixedConstraints;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.MixedSolution;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.Owner;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMixedIntegrationTest.Task;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmLocalImprovementMixedTest {

  @Test
  void mixedProbesPreserveDeclarativeShadowsAndFreshScoreParityAcrossValidAndRejectedChildren() {
    var solver = solver(config(phase(8L, 65)).withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var completed = new ArrayList<GeneticAlgorithmStepScope<MixedSolution>>();
    var phase = new AtomicReference<GeneticAlgorithmPhaseScope<MixedSolution>>();
    var published = new ArrayList<MixedSolution>();
    var snapshots = new ArrayList<Snapshot>();
    solver.addEventListener(
        event -> {
          var best = event.getNewBestSolution();
          assertThat(best.score).isEqualTo(replay(best));
          published.add(best);
          snapshots.add(snapshot(best));
        });
    var factory = factory();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          private Object session;

          @Override
          public void phaseStarted(AbstractPhaseScope<MixedSolution> scope) {
            session =
                ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession();
          }

          @Override
          public void stepEnded(AbstractStepScope<MixedSolution> scope) {
            var step = (GeneticAlgorithmStepScope<MixedSolution>) scope;
            completed.add(step);
            assertEligibleCounters(step, 8L);
            assertThat(step.getScore().isStructurallyFlawed()).isFalse();
            assertThat(step.getScore().raw()).isEqualTo(replay(step.getWorkingSolution()));
            assertThat(
                    ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector())
                        .getSession())
                .isSameAs(session);
            try (var fresh = factory.createScoreDirectorBuilder().build()) {
              fresh.setWorkingSolution(scope.getScoreDirector().cloneWorkingSolution());
              assertThat(fresh.calculateScore()).isEqualTo(step.getScore());
              assertThat(replay(fresh.getWorkingSolution())).isEqualTo(step.getScore().raw());
            }
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<MixedSolution> scope) {
            phase.set((GeneticAlgorithmPhaseScope<MixedSolution>) scope);
          }
        });
    var input = problem();
    var original = snapshot(input);

    var result = solver.solve(input);

    assertThat(completed).hasSize(65);
    assertThat(completed)
        .anySatisfy(
            step -> assertThat(step.getOutcome()).isEqualTo(GeneticAlgorithmOutcome.INVALID));
    assertThat(phase.get().getLocalImprovementAcceptedCount()).isPositive();
    assertAccounting(phase.get(), completed);
    assertThat(snapshot(input)).isEqualTo(original);
    for (var i = 0; i < published.size(); i++) {
      assertThat(snapshot(published.get(i))).isEqualTo(snapshots.get(i));
      assertThat(published.get(i).score).isEqualTo(replay(published.get(i)));
      assertThat(published.get(i).tasks.getFirst()).isNotSameAs(input.tasks.getFirst());
    }
    assertThat(result.score).isEqualTo(replay(result));
    try (var fresh = factory.createScoreDirectorBuilder().build()) {
      fresh.setWorkingSolution(
          factory.getSolutionDescriptor().getSolutionCloner().cloneSolution(result));
      assertThat(fresh.calculateScore().raw()).isEqualTo(replay(result));
    }
  }

  @Test
  void explicitZeroPreservesDefaultMixedTraceAndScoreCalculations() {
    var defaultPhase = phase(0L, 50);
    defaultPhase.setLocalImprovementMoveCountLimit(null);
    var defaultSolver = solver(config(defaultPhase));
    var zeroSolver = solver(config(phase(0L, 50)));
    var defaultTrace = trace(defaultSolver);
    var zeroTrace = trace(zeroSolver);

    var defaultResult = defaultSolver.solve(problem());
    var zeroResult = zeroSolver.solve(problem());

    assertThat(zeroTrace).containsExactlyElementsOf(defaultTrace);
    assertThat(snapshot(zeroResult)).isEqualTo(snapshot(defaultResult));
    assertThat(zeroResult.score).isEqualTo(defaultResult.score);
    assertThat(zeroSolver.getSolverScope().getScoreCalculationCount())
        .isEqualTo(defaultSolver.getSolverScope().getScoreCalculationCount());
    assertThat(zeroSolver.getSolverScope().getMoveEvaluationCount()).isEqualTo(50);
  }

  private static List<String> trace(DefaultSolver<MixedSolution> solver) {
    var trace = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<MixedSolution> scope) {
            var step = (GeneticAlgorithmStepScope<MixedSolution>) scope;
            assertThat(step.getLocalImprovementProbeCount()).isZero();
            assertThat(step.getLocalImprovementAcceptedCount()).isZero();
            trace.add(
                List.of(
                        step.getStepIndex(),
                        step.isSeeding(),
                        step.getGeneration(),
                        step.getCandidateId(),
                        step.getFirstParentId(),
                        step.getSecondParentId(),
                        step.getNativeId(),
                        step.isCrossed(),
                        String.valueOf(step.getMutationType()),
                        String.valueOf(step.getMutationGroup()),
                        step.getOutcome(),
                        step.isAdmitted(),
                        step.getChangedAssignmentCount(),
                        step.getBeforeScore(),
                        step.getBestBeforeScore(),
                        String.valueOf(step.getCandidateScore()),
                        step.getScore(),
                        step.getBestScoreImproved(),
                        snapshot(step.getWorkingSolution()))
                    .toString());
          }
        });
    return trace;
  }

  private static SolverConfig config(GeneticAlgorithmPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(MixedSolution.class)
        .withEntityClasses(Owner.class, Task.class)
        .withConstraintProviderClass(MixedConstraints.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withRandomSeed(37L)
        .withPhases(phase);
  }

  private static DefaultSolver<MixedSolution> solver(SolverConfig config) {
    return (DefaultSolver<MixedSolution>) SolverFactory.<MixedSolution>create(config).buildSolver();
  }

  private static MixedSolution problem() {
    var solution = GeneticAlgorithmMixedIntegrationTest.problem();
    solution.owners.forEach(owner -> owner.start = 2);
    solution.tasks.forEach(task -> task.duration = 3);
    return solution;
  }

  private static BavetConstraintStreamScoreDirectorFactory<MixedSolution, SimpleScore> factory() {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(MixedSolution.class, Owner.class, Task.class),
        new MixedConstraints(),
        EnvironmentMode.NO_ASSERT);
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

  private static SimpleScore replay(MixedSolution solution) {
    var positions = new IdentityHashMap<Task, Position>();
    for (var owner : solution.owners) {
      for (var i = 0; i < owner.tasks.size(); i++) {
        var task = owner.tasks.get(i);
        var previous = i == 0 ? null : owner.tasks.get(i - 1);
        assertThat(positions.put(task, new Position(owner, previous))).isNull();
        assertThat(task.owner).isSameAs(owner);
        assertThat(task.index).isEqualTo(i);
        assertThat(task.previous).isSameAs(previous);
        assertThat(solution.tasks).anySatisfy(value -> assertThat(value).isSameAs(task));
      }
    }
    assertThat(positions).hasSize(solution.tasks.size());
    var depths = new IdentityHashMap<Task, Integer>();
    var visiting = Collections.newSetFromMap(new IdentityHashMap<Task, Boolean>());
    return SimpleScore.of(
        -solution.tasks.stream().mapToInt(task -> depth(task, positions, depths, visiting)).sum());
  }

  private static int depth(
      Task task, Map<Task, Position> positions, Map<Task, Integer> depths, Set<Task> visiting) {
    if (depths.containsKey(task)) return depths.get(task);
    assertThat(visiting.add(task))
        .as("A rejected probe must not leave a declarative cycle")
        .isTrue();
    var position = positions.get(task);
    var previous =
        position.previous() == null ? 0 : depth(position.previous(), positions, depths, visiting);
    var dependency =
        task.dependency == null ? 0 : depth(task.dependency, positions, depths, visiting);
    var depth = position.owner().start + task.duration + Math.max(previous, dependency);
    assertThat(task.depth).isEqualTo(depth);
    visiting.remove(task);
    depths.put(task, depth);
    return depth;
  }

  private record Position(Owner owner, Task previous) {}

  private record Snapshot(
      List<Integer> starts,
      List<List<String>> lists,
      List<Integer> durations,
      List<String> dependencies) {}
}
