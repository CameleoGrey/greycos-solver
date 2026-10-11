package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultipleListEntityRangeSolverTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void constructionAndSearchKeepEachDeclarationsEntityRange(boolean nearby) {
    var first = List.of(new FirstValue(0), new FirstValue(1), new FirstValue(2), new FirstValue(3));
    var second =
        List.of(new SecondValue(0), new SecondValue(1), new SecondValue(2), new SecondValue(3));
    var problem = new RangeSolution();
    problem.firstValues = first;
    problem.secondValues = second;
    problem.owners =
        List.of(
            new Owner(List.of(first.get(0), first.get(2)), List.of(second.get(1), second.get(3))),
            new Owner(List.of(first.get(1), first.get(3)), List.of(second.get(0), second.get(2))));
    var config =
        new SolverConfig()
            .withSolutionClass(RangeSolution.class)
            .withEntityClasses(Owner.class)
            .withEasyScoreCalculatorClass(RangeScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                construction("first", nearby),
                construction("second", nearby),
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new UnionMoveSelectorConfig()
                            .withMoveSelectors(
                                change("first", nearby, false), change("second", nearby, false)))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(12)));
    var result = SolverFactory.<RangeSolution>create(config).buildSolver().solve(problem);
    assertThat(result.owners.stream().flatMap(owner -> owner.first.stream()).toList())
        .containsExactlyInAnyOrderElementsOf(first);
    assertThat(result.owners.stream().flatMap(owner -> owner.second.stream()).toList())
        .containsExactlyInAnyOrderElementsOf(second);
    for (var owner : result.owners) {
      assertThat(owner.first).containsExactlyInAnyOrderElementsOf(owner.firstRange);
      assertThat(owner.second).containsExactlyInAnyOrderElementsOf(owner.secondRange);
    }
    assertThat(result.score).isEqualTo(new RangeScoreCalculator().calculateScore(result));
  }

  private static ConstructionHeuristicPhaseConfig construction(
      String variableName, boolean nearby) {
    return new ConstructionHeuristicPhaseConfig()
        .withEntityPlacerConfig(
            new QueuedValuePlacerConfig()
                .withEntityClass(Owner.class)
                .withValueSelectorConfig(new ValueSelectorConfig(variableName).withId(variableName))
                .withMoveSelectorConfig(change(variableName, nearby, true)));
  }

  private static ListChangeMoveSelectorConfig change(
      String variableName, boolean nearby, boolean replay) {
    var source = new ValueSelectorConfig(variableName);
    if (replay) {
      source.withMimicSelectorRef(variableName);
    } else {
      source.withId(variableName);
    }
    var destinationEntity = new EntitySelectorConfig(Owner.class);
    if (nearby) {
      destinationEntity.withNearbySelectionConfig(
          new NearbySelectionConfig()
              .withNearbyDistanceMeterClass(RangeDistanceMeter.class)
              .withOriginValueSelectorConfig(
                  new ValueSelectorConfig().withMimicSelectorRef(variableName)));
    }
    return new ListChangeMoveSelectorConfig()
        .withValueSelectorConfig(source)
        .withDestinationSelectorConfig(
            new DestinationSelectorConfig()
                .withEntitySelectorConfig(destinationEntity)
                .withValueSelectorConfig(new ValueSelectorConfig(variableName)));
  }

  public static class RangeDistanceMeter implements NearbyDistanceMeter<Object, Owner> {
    @Override
    public double getNearbyDistance(Object origin, Owner destination) {
      return origin instanceof FirstValue value ? value.index() : ((SecondValue) origin).index();
    }
  }

  public static class RangeScoreCalculator
      implements EasyScoreCalculator<RangeSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(RangeSolution solution) {
      int score = 0;
      for (var owner : solution.owners) {
        for (int i = 0; i < owner.first.size(); i++) {
          score -= (i + 1) * owner.first.get(i).index();
        }
        for (int i = 0; i < owner.second.size(); i++) {
          score -= (i + 1) * owner.second.get(i).index();
        }
      }
      return SimpleScore.of(score);
    }
  }

  public record FirstValue(int index) {}

  public record SecondValue(int index) {}

  @PlanningEntity
  public static class Owner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRange")
    public List<FirstValue> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "secondRange")
    public List<SecondValue> second = new ArrayList<>();

    @ValueRangeProvider(id = "firstRange")
    public List<FirstValue> firstRange;

    @ValueRangeProvider(id = "secondRange")
    public List<SecondValue> secondRange;

    public Owner() {}

    Owner(List<FirstValue> firstRange, List<SecondValue> secondRange) {
      this.firstRange = firstRange;
      this.secondRange = secondRange;
    }
  }

  @PlanningSolution
  public static class RangeSolution {
    @PlanningEntityCollectionProperty public List<Owner> owners;
    @ProblemFactCollectionProperty public List<FirstValue> firstValues;
    @ProblemFactCollectionProperty public List<SecondValue> secondValues;
    @PlanningScore public SimpleScore score;

    public RangeSolution() {}
  }
}
