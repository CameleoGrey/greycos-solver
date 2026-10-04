package greycos.solver.core.impl.neighborhood;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.preview.api.move.builtin.ChangeMoveProvider;
import greycos.solver.core.preview.api.neighborhood.Neighborhood;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodBuilder;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodProvider;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinnedEntityValueSolverIntegrationTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void pinnedDestinationReachesTheSameScoreAsClassicChange(String moveThreadCount) {
    var classic = solve(moveThreadCount, false);
    var neighborhoods = solve(moveThreadCount, true);

    assertSolved(classic);
    assertSolved(neighborhoods);
    assertThat(neighborhoods.score).isEqualTo(classic.score);
  }

  private static Plan solve(String moveThreadCount, boolean neighborhoods) {
    var phase =
        new LocalSearchPhaseConfig()
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(10));
    if (neighborhoods) {
      phase.setNeighborhoodProviderClass(PinnedDestinationNeighborhoodProvider.class);
    } else {
      phase.withMoveSelectorConfig(new ChangeMoveSelectorConfig());
    }
    var config =
        new SolverConfig()
            .withSolutionClass(Plan.class)
            .withEntityClasses(Node.class)
            .withEasyScoreCalculatorClass(PinnedDestinationScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(37L)
            .withMoveThreadCount(moveThreadCount)
            .withPhases(phase);
    if (neighborhoods) {
      config.withPreviewFeature(PreviewFeature.NEIGHBORHOODS);
    }
    var first = new Node("first", false);
    var pinned = new Node("pinned", true);
    first.target = first;
    pinned.target = first;
    var problem = new Plan();
    problem.nodes = List.of(first, pinned);
    return SolverFactory.<Plan>create(config).buildSolver().solve(problem);
  }

  private static void assertSolved(Plan solution) {
    var first = solution.nodes.getFirst();
    var pinned = solution.nodes.get(1);
    assertThat(first.target).isSameAs(pinned);
    assertThat(solution.score).isEqualTo(SimpleScore.ONE);
    assertThat(solution.score)
        .isEqualTo(new PinnedDestinationScoreCalculator().calculateScore(solution));
    assertThat(pinned.pinned).isTrue();
    assertThat(pinned.target).isSameAs(first);
  }

  @PlanningEntity
  public static class Node {
    @PlanningId public String id;
    @PlanningPin public boolean pinned;

    @PlanningVariable(valueRangeProviderRefs = "nodes")
    public Node target;

    public Node() {}

    public Node(String id, boolean pinned) {
      this.id = id;
      this.pinned = pinned;
    }

    @Override
    public String toString() {
      return id;
    }
  }

  @PlanningSolution
  public static class Plan {
    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "nodes")
    public List<Node> nodes;

    @PlanningScore public SimpleScore score;
  }

  public static final class PinnedDestinationNeighborhoodProvider
      implements NeighborhoodProvider<Plan> {
    @Override
    public Neighborhood defineNeighborhood(NeighborhoodBuilder<Plan> builder) {
      var variable =
          builder
              .getSolutionMetaModel()
              .genuineEntity(Node.class)
              .basicVariable("target", Node.class);
      return builder.add(new ChangeMoveProvider<>(variable)).build();
    }
  }

  public static final class PinnedDestinationScoreCalculator
      implements EasyScoreCalculator<Plan, SimpleScore> {
    @Override
    public SimpleScore calculateScore(Plan solution) {
      return SimpleScore.of(solution.nodes.getFirst().target == solution.nodes.get(1) ? 1 : 0);
    }
  }
}
