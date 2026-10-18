package greycos.solver.core.impl.exhaustivesearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchType;
import greycos.solver.core.config.exhaustivesearch.NodeExplorationType;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.score.director.ScoreDirector;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(30)
class JointExhaustiveSearchInheritedTargetTest {

  static Stream<Arguments> modes() {
    return Stream.of(ExhaustiveSearchType.values())
        .flatMap(type -> Stream.of(false, true).map(product -> Arguments.of(type, product)));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void inheritedListKeepsExplicitSubtypeDestination(ExhaustiveSearchType type, boolean product) {
    var problem = problem();
    problem.owners.get(1).choice = 0;

    var result = solver(type, product, childSelector(), childSelector()).solve(problem);

    // The excluded base owner is worth 100, whereas the configured child is worth only 10.
    assertThat(result.owners.getFirst().visits).isEmpty();
    assertThat(result.owners.get(1).visits).containsExactly("visit");
    assertThat(result.otherOwners.getFirst().extras).containsExactly(1L);
    assertThat(result.score).isEqualTo(SimpleScore.of(10));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void inheritedBasicTargetCompletesWithInitializedExcludedBase(
      ExhaustiveSearchType type, boolean product) {
    var result = solver(type, product, childSelector(), childSelector()).solve(problem());

    assertThat(result.owners.getFirst().choice).isZero();
    assertThat(result.owners.get(1).choice).isEqualTo(1);
    assertThat(result.owners.getFirst().visits).isEmpty();
    assertThat(result.owners.get(1).visits).containsExactly("visit");
    assertThat(result.otherOwners.getFirst().extras).containsExactly(1L);
    assertThat(result.score).isEqualTo(SimpleScore.of(11));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void excludedRequiredBasicCoordinateDoesNotBecomeACompleteIncumbent(
      ExhaustiveSearchType type, boolean product) {
    var problem = problem();
    problem.owners.getFirst().choice = null;
    problem.owners.get(1).visits.add("visit");
    problem.otherOwners.getFirst().extras.add(1L);
    var solver = solver(type, product, childSelector(), childSelector());
    solver.addEventListener(event -> assertThat(event.isNewBestSolutionInitialized()).isFalse());

    var result = solver.solve(problem);

    // The decision frontier still includes the excluded base's required assignment.
    // Restricting the move selector must neither initialize it nor hide its incompleteness.
    assertThat(result.owners.getFirst().choice).isNull();
    assertThat(result.owners.get(1).choice).isNull();
    assertThat(result.owners.get(1).visits).containsExactly("visit");
    assertThat(result.otherOwners.getFirst().extras).containsExactly(1L);
  }

  @ParameterizedTest
  @MethodSource("modes")
  void classlessLeafSelectorsKeepTheirFilteringAndSorting(
      ExhaustiveSearchType type, boolean product) {
    var problem = problem();
    var laterChild = new ChildOwner();
    laterChild.code = "child-z";
    laterChild.listReward = 10;
    problem.owners.add(laterChild);
    var listEntities = filteredSortedSelector();
    var basicEntities = filteredSortedSelector();

    var result = solver(type, product, listEntities, basicEntities).solve(problem);

    assertThat(result.owners.getFirst().visits).isEmpty();
    assertThat(result.owners.getFirst().choice).isZero();
    assertThat(result.owners.get(1).choice).isEqualTo(1);
    assertThat(result.owners.get(2).choice).isEqualTo(1);
    // Both children have equal list rewards; sorting chooses child-z before child-a.
    assertThat(result.owners.get(1).visits).isEmpty();
    assertThat(result.owners.get(2).visits).containsExactly("visit");
    assertThat(result.otherOwners.getFirst().extras).containsExactly(1L);
    assertThat(result.score).isEqualTo(SimpleScore.of(12));
    for (var original : List.of(listEntities, basicEntities)) {
      assertThat(original.getEntityClass()).isNull();
      assertThat(original.getFilterClass()).isEqualTo(ChildFilter.class);
      assertThat(original.getComparatorClass()).isEqualTo(ReverseCodeComparator.class);
      assertThat(original.getSelectionOrder()).isEqualTo(SelectionOrder.SORTED);
      assertThat(original.getCacheType()).isEqualTo(SelectionCacheType.PHASE);
    }
  }

  private static EntitySelectorConfig childSelector() {
    return new EntitySelectorConfig(ChildOwner.class);
  }

  private static EntitySelectorConfig filteredSortedSelector() {
    return new EntitySelectorConfig()
        .withFilterClass(ChildFilter.class)
        .withComparatorClass(ReverseCodeComparator.class)
        .withSelectionOrder(SelectionOrder.SORTED)
        .withCacheType(SelectionCacheType.PHASE);
  }

  private static Solver<InheritedSolution> solver(
      ExhaustiveSearchType type,
      boolean product,
      EntitySelectorConfig listEntities,
      EntitySelectorConfig basicEntities) {
    var leaves =
        new MoveSelectorConfig<?>[] {
          listSelector("visits", listEntities),
          listSelector("extras", new EntitySelectorConfig(OtherOwner.class)),
          new ChangeMoveSelectorConfig()
              .withEntitySelectorConfig(basicEntities)
              .withValueSelectorConfig(new ValueSelectorConfig("choice"))
        };
    var moves =
        product
            ? new CartesianProductMoveSelectorConfig().withMoveSelectors(leaves)
            : new UnionMoveSelectorConfig().withMoveSelectors(leaves);
    var phase =
        new ExhaustiveSearchPhaseConfig()
            .withExhaustiveSearchType(type)
            .withNodeExplorationType(NodeExplorationType.ORIGINAL_ORDER)
            .withMoveSelectorConfig(moves);
    return SolverFactory.<InheritedSolution>create(
            new SolverConfig()
                .withSolutionClass(InheritedSolution.class)
                .withEntityClasses(BaseOwner.class, ChildOwner.class, OtherOwner.class)
                .withEasyScoreCalculatorClass(InheritedCalculator.class)
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withPhases(phase))
        .buildSolver();
  }

  private static ListChangeMoveSelectorConfig listSelector(
      String name, EntitySelectorConfig entities) {
    return new ListChangeMoveSelectorConfig()
        .withValueSelectorConfig(new ValueSelectorConfig(name))
        .withDestinationSelectorConfig(
            new DestinationSelectorConfig()
                .withEntitySelectorConfig(entities)
                .withValueSelectorConfig(new ValueSelectorConfig(name)));
  }

  private static InheritedSolution problem() {
    var problem = new InheritedSolution();
    var base = new BaseOwner();
    base.code = "base";
    base.listReward = 100;
    base.choice = 0;
    var child = new ChildOwner();
    child.code = "child-a";
    child.listReward = 10;
    problem.owners = new ArrayList<>(List.of(base, child));
    problem.otherOwners = List.of(new OtherOwner());
    return problem;
  }

  public static class ChildFilter implements SelectionFilter<InheritedSolution, BaseOwner> {
    @Override
    public boolean accept(ScoreDirector<InheritedSolution> director, BaseOwner owner) {
      return owner instanceof ChildOwner;
    }
  }

  public static class ReverseCodeComparator implements Comparator<BaseOwner> {
    @Override
    public int compare(BaseOwner left, BaseOwner right) {
      return right.code.compareTo(left.code);
    }
  }

  @PlanningEntity
  public static class BaseOwner {
    public String code;
    public int listReward;

    @PlanningListVariable(valueRangeProviderRefs = "visits")
    public List<String> visits = new ArrayList<>();

    @PlanningVariable(valueRangeProviderRefs = "choices")
    public Integer choice;
  }

  @PlanningEntity
  public static class ChildOwner extends BaseOwner {}

  @PlanningEntity
  public static class OtherOwner {
    @PlanningListVariable(valueRangeProviderRefs = "extras")
    public List<Long> extras = new ArrayList<>();
  }

  @PlanningSolution
  public static class InheritedSolution {
    @PlanningEntityCollectionProperty public List<BaseOwner> owners;
    @PlanningEntityCollectionProperty public List<OtherOwner> otherOwners;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "visits")
    public List<String> visits = List.of("visit");

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "extras")
    public List<Long> extras = List.of(1L);

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "choices")
    public List<Integer> choices = List.of(0, 1);

    @PlanningScore public SimpleScore score;
  }

  public static class InheritedCalculator
      implements EasyScoreCalculator<InheritedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(InheritedSolution solution) {
      int score = 0;
      for (var owner : solution.owners) {
        score += owner.listReward * owner.visits.size();
        score += owner.choice == null ? 0 : owner.choice;
      }
      return SimpleScore.of(score);
    }
  }
}
