package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;

class ProblemChangeLifecycleTest {

  @Test
  void problemChangeCycleIsRecoveredBeforeAnyBestSolutionEvent() {
    var solver = solver();
    var queued = new AtomicBoolean();
    var eventScores = new ArrayList<SimpleScore>();
    solver.addEventListener(event -> eventScores.add(event.getNewBestSolution().score));
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<CycleSolution> scope) {
            if (!queued.getAndSet(true)) {
              solver.addProblemChange(
                  (solution, director) -> {
                    assertThat(solver.getSolverScope().getScoreDirector().getEnvironmentMode())
                        .isEqualTo(EnvironmentMode.PHASE_ASSERT);
                    var first = solution.nodes.get(1);
                    director.changeProblemProperty(first, node -> node.label = "changed");
                    director.changeVariable(
                        first, "previous", node -> node.previous = solution.nodes.get(2));
                  });
            }
          }
        });

    var result = solver.solve(problem());
    assertThat(eventScores)
        .hasSizeGreaterThanOrEqualTo(2)
        .allSatisfy(score -> assertThat(score.structuralScore()).isZero());
    assertThat(result.nodes.get(1).label).isEqualTo("changed");
    assertThat(result.nodes).extracting(node -> node.depth).containsExactly(0, 0, 0);
    assertThat(result.score).isEqualTo(SimpleScore.ZERO);
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
  }

  @Test
  void failedProblemChangeClosesItsDirectorAndAllowsAnotherSolve() {
    var solver = solver();
    var queued = new AtomicBoolean();
    var failedDirector = new AtomicReference<InnerScoreDirector<CycleSolution, ?>>();
    var failure = new IllegalStateException("Problem change failed");
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<CycleSolution> scope) {
            if (!queued.getAndSet(true)) {
              solver.addProblemChange(
                  (solution, director) -> {
                    failedDirector.set(solver.getSolverScope().getScoreDirector());
                    throw failure;
                  });
            }
          }
        });

    assertThatThrownBy(() -> solver.solve(problem())).isSameAs(failure);
    assertThat(failedDirector.get().getWorkingSolution()).isNull();
    assertThat(solver.isSolving()).isFalse();
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(solver.solve(problem()).score).isEqualTo(SimpleScore.of(-2));
    assertThat(solver.isSolving()).isFalse();
  }

  @Test
  void invalidChangedPlanningIdFailsBeforePublication() {
    var solver = solver();
    var queued = new AtomicBoolean();
    var publishedIds = new ArrayList<List<String>>();
    solver.addEventListener(
        event ->
            publishedIds.add(
                event.getNewBestSolution().nodes.stream().map(node -> node.id).toList()));
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<CycleSolution> scope) {
            if (!queued.getAndSet(true)) {
              solver.addProblemChange(
                  (solution, director) ->
                      director.changeProblemProperty(
                          solution.nodes.get(1), node -> node.id = null));
            }
          }
        });

    assertThatThrownBy(() -> solver.solve(problem()))
        // The structural refresh validates lookup IDs before the final publication check.
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("planningId (null)");
    assertThat(publishedIds).isNotEmpty().allSatisfy(ids -> assertThat(ids).doesNotContainNull());
    assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
    assertThat(solver.isSolving()).isFalse();
  }

  private static DefaultSolver<CycleSolution> solver() {
    var config =
        new SolverConfig()
            .withSolutionClass(CycleSolution.class)
            .withEntityClasses(CycleNode.class)
            .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
            .withEasyScoreCalculatorClass(CycleScore.class)
            .withPhases(
                new CustomPhaseConfig()
                    .withCustomPhaseCommands(context -> {})
                    .withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    return (DefaultSolver<CycleSolution>) SolverFactory.<CycleSolution>create(config).buildSolver();
  }

  private static CycleSolution problem() {
    var root = new CycleNode("root");
    root.pinned = true;
    var first = new CycleNode("first");
    first.previous = root;
    var second = new CycleNode("second");
    second.previous = first;
    var solution = new CycleSolution();
    solution.nodes = new ArrayList<>(List.of(root, first, second));
    return solution;
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

    @ShadowVariable(supplierName = "depth")
    public Integer depth;

    public String label;

    public CycleNode() {}

    CycleNode(String id) {
      this.id = id;
    }

    @ShadowSources("previous.depth")
    public Integer depth() {
      return previous == null ? 0 : previous.depth == null ? null : previous.depth + 1;
    }
  }

  public static class CycleScore implements EasyScoreCalculator<CycleSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(CycleSolution solution) {
      return SimpleScore.of(-solution.nodes.stream().filter(node -> node.previous != null).count());
    }
  }
}
