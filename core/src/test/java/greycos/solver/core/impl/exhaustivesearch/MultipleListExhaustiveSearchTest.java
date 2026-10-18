package greycos.solver.core.impl.exhaustivesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchType;
import greycos.solver.core.config.exhaustivesearch.NodeExplorationType;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MultipleListExhaustiveSearchTest {

  static Stream<Arguments> modes() {
    return Stream.concat(
        Stream.of(
            Arguments.of(ExhaustiveSearchType.BRUTE_FORCE, NodeExplorationType.ORIGINAL_ORDER)),
        Stream.of(NodeExplorationType.values())
            .map(order -> Arguments.of(ExhaustiveSearchType.BRANCH_AND_BOUND, order)));
  }

  private static SolverConfig config(ExhaustiveSearchPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(TestdataMultipleListSolution.class)
        .withEntityClasses(
            TestdataMultipleListEntity.class,
            TestdataMultipleListFirstValue.class,
            TestdataMultipleListSecondValue.class)
        .withEasyScoreCalculatorClass(CoupledCalculator.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withPhases(phase);
  }

  private static TestdataMultipleListSolution problem() {
    var problem = TestdataMultipleListSolution.generateUninitializedSolution(2, 1, 2);
    problem.setBasicValueRange(List.of(0, 1));
    problem.getEntityList().get(1).setBasicValue(0);
    return problem;
  }

  @ParameterizedTest
  @MethodSource("modes")
  void findsJointOptimumAcrossListsAndBasicVariables(
      ExhaustiveSearchType type, NodeExplorationType order) {
    CoupledCalculator.completeStates.clear();
    var solver =
        (DefaultSolver<TestdataMultipleListSolution>)
            SolverFactory.<TestdataMultipleListSolution>create(
                    config(
                        new ExhaustiveSearchPhaseConfig()
                            .withExhaustiveSearchType(type)
                            .withNodeExplorationType(order)))
                .buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataMultipleListSolution> step) {
            var scope =
                (ExhaustiveSearchPhaseScope<TestdataMultipleListSolution>) step.getPhaseScope();
            var bound = scope.getBestPessimisticBound();
            if (bound != null) {
              assertThat(bound.isFullyAssigned()).isTrue();
              assertThat(bound.isStructurallyFlawed()).isFalse();
              assertThat(bound.raw())
                  .isIn(
                      SimpleScore.of(100),
                      SimpleScore.ZERO,
                      SimpleScore.of(-10),
                      SimpleScore.of(-20));
            }
          }
        });
    var result = solver.solve(problem());
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(independentOptimum()));
    assertThat(result.getEntityList().get(1).getFirstValueList())
        .extracting(TestdataMultipleListFirstValue::getCode)
        .containsExactly("first-1", "first-0");
    assertThat(result.getEntityList().getFirst().getSecondValueList())
        .extracting(TestdataMultipleListSecondValue::getCode)
        .containsExactly("second-0");
    assertThat(result.getEntityList().getFirst().getBasicValue()).isEqualTo(1);
    assertThat(result.getSecondValueList().getFirst().getQuantity()).isEqualTo(1);
    for (var owner : result.getEntityList()) {
      for (int i = 0; i < owner.getFirstValueList().size(); i++) {
        var value = owner.getFirstValueList().get(i);
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious())
            .isSameAs(i == 0 ? null : owner.getFirstValueList().get(i - 1));
        assertThat(value.getNext())
            .isSameAs(
                i + 1 == owner.getFirstValueList().size()
                    ? null
                    : owner.getFirstValueList().get(i + 1));
      }
    }
    if (type == ExhaustiveSearchType.BRUTE_FORCE) {
      // Six ordered two-value allocations, two cargo owners, two basic values, two quantities.
      assertThat(CoupledCalculator.completeStates).hasSize(48);
    }
  }

  private static int independentOptimum() {
    int best = Integer.MIN_VALUE;
    for (var permutation : List.of(List.of(0, 1), List.of(1, 0))) {
      for (int split = 0; split <= 2; split++) {
        for (int cargoOwner = 0; cargoOwner < 2; cargoOwner++) {
          for (int basic = 0; basic < 2; basic++) {
            for (int quantity = 0; quantity < 2; quantity++) {
              int score =
                  split == 0
                          && permutation.equals(List.of(1, 0))
                          && cargoOwner == 0
                          && basic == 1
                          && quantity == 1
                      ? 100
                      : -10 * (basic + quantity);
              best = Math.max(best, score);
            }
          }
        }
      }
    }
    return best;
  }

  public static class CoupledCalculator
      implements EasyScoreCalculator<TestdataMultipleListSolution, SimpleScore> {
    static final Set<String> completeStates = new HashSet<>();

    @Override
    public SimpleScore calculateScore(TestdataMultipleListSolution solution) {
      var first = solution.getEntityList().getFirst();
      var second = solution.getEntityList().get(1);
      var cargo = solution.getSecondValueList().getFirst();
      int basic = first.getBasicValue() == null ? 0 : first.getBasicValue();
      int quantity = cargo.getQuantity() == null ? 0 : cargo.getQuantity();
      boolean target =
          second.getFirstValueList().size() == 2
              && second.getFirstValueList().getFirst().getCode().equals("first-1")
              && first.getSecondValueList().contains(cargo)
              && basic == 1
              && quantity == 1;
      if (first.getBasicValue() != null
          && cargo.getQuantity() != null
          && first.getFirstValueList().size() + second.getFirstValueList().size() == 2
          && first.getSecondValueList().size() + second.getSecondValueList().size() == 1) {
        completeStates.add(
            first.getFirstValueList()
                + "/"
                + second.getFirstValueList()
                + "/"
                + first.getSecondValueList()
                + "/"
                + second.getSecondValueList()
                + "/"
                + basic
                + "/"
                + quantity);
      }
      return SimpleScore.of(target ? 100 : -10 * (basic + quantity));
    }
  }

  @Test
  void explicitlyTargetedUnionPreservesTheJointSpace() {
    var phase =
        new ExhaustiveSearchPhaseConfig()
            .withExhaustiveSearchType(ExhaustiveSearchType.BRUTE_FORCE)
            .withMoveSelectorConfig(
                new UnionMoveSelectorConfig()
                    .withMoveSelectors(
                        new ChangeMoveSelectorConfig()
                            .withEntitySelectorConfig(
                                new EntitySelectorConfig(TestdataMultipleListEntity.class))
                            .withValueSelectorConfig(new ValueSelectorConfig("basicValue")),
                        new ChangeMoveSelectorConfig()
                            .withEntitySelectorConfig(
                                new EntitySelectorConfig(TestdataMultipleListSecondValue.class))
                            .withValueSelectorConfig(new ValueSelectorConfig("quantity")),
                        listSelector("firstValueList"),
                        listSelector("secondValueList")));
    assertThat(
            SolverFactory.<TestdataMultipleListSolution>create(config(phase))
                .buildSolver()
                .solve(problem())
                .getScore())
        .isEqualTo(SimpleScore.of(100));
  }

  static Stream<Arguments> productModes() {
    return Stream.of(false, true)
        .flatMap(nested -> Stream.of(Arguments.of(null, nested), Arguments.of(true, nested)));
  }

  @ParameterizedTest
  @MethodSource("productModes")
  void targetedCartesianProductRetainsAllJointCompletions(Boolean ignoreEmpty, boolean nested) {
    CoupledCalculator.completeStates.clear();
    var product = targetedProduct().withIgnoreEmptyChildIterators(ignoreEmpty);
    var moves = nested ? new UnionMoveSelectorConfig().withMoveSelectors(product) : product;
    var phase =
        new ExhaustiveSearchPhaseConfig()
            .withExhaustiveSearchType(ExhaustiveSearchType.BRUTE_FORCE)
            .withMoveSelectorConfig(moves);
    var result =
        SolverFactory.<TestdataMultipleListSolution>create(config(phase))
            .buildSolver()
            .solve(problem());
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(independentOptimum()));
    assertThat(CoupledCalculator.completeStates).hasSize(48);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsCartesianProductsThatDiscardCompletedCoordinates(boolean nested) {
    var product = targetedProduct().withIgnoreEmptyChildIterators(false);
    var moves =
        nested
            ? new UnionMoveSelectorConfig()
                .withMoveSelectors(
                    new CartesianProductMoveSelectorConfig().withMoveSelectors(product))
            : product;
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                SolverFactory.create(
                        config(new ExhaustiveSearchPhaseConfig().withMoveSelectorConfig(moves)))
                    .buildSolver())
        .withMessageContaining("joint exhaustiveSearch")
        .withMessageContaining("ignoreEmptyChildIterators (false)")
        .withMessageContaining("firstValueList")
        .withMessageContaining("Set ignoreEmptyChildIterators to true or omit it");
  }

  static Stream<Arguments> endlessSelectionModes() {
    return Stream.of(SelectionOrder.RANDOM, SelectionOrder.PROBABILISTIC)
        .flatMap(
            order ->
                Stream.of(
                        "root",
                        "nestedProduct",
                        "nestedUnion",
                        "listMove",
                        "basicMove",
                        "listSourceValue",
                        "destinationEntity",
                        "destinationValue",
                        "basicEntity",
                        "basicValue",
                        "phaseEntity")
                    .map(location -> Arguments.of(order, location)));
  }

  @ParameterizedTest
  @MethodSource("endlessSelectionModes")
  @Timeout(5)
  void rejectsEndlessNestedSelectionEvenWithCountLimits(SelectionOrder order, String location) {
    var basic = basicSelector(TestdataMultipleListEntity.class, "basicValue");
    var quantity = basicSelector(TestdataMultipleListSecondValue.class, "quantity");
    var first = listSelector("firstValueList");
    var second = listSelector("secondValueList");
    var union = new UnionMoveSelectorConfig().withMoveSelectors(basic, quantity);
    var product = new CartesianProductMoveSelectorConfig().withMoveSelectors(first, second);
    var root =
        new CartesianProductMoveSelectorConfig()
            .withMoveSelectors(union, product)
            .withSelectedCountLimit(1L);
    var phase = new ExhaustiveSearchPhaseConfig().withMoveSelectorConfig(root);
    switch (location) {
      case "root" -> root.withSelectionOrder(order);
      case "nestedProduct" -> product.withSelectionOrder(order).withSelectedCountLimit(1L);
      case "nestedUnion" -> union.withSelectionOrder(order).withSelectedCountLimit(1L);
      case "listMove" -> second.withSelectionOrder(order).withSelectedCountLimit(1L);
      case "basicMove" -> basic.withSelectionOrder(order).withSelectedCountLimit(1L);
      case "listSourceValue" ->
          second.getValueSelectorConfig().withSelectionOrder(order).withSelectedCountLimit(1L);
      case "destinationEntity" ->
          second
              .getDestinationSelectorConfig()
              .getEntitySelectorConfig()
              .withSelectionOrder(order)
              .withSelectedCountLimit(1L);
      case "destinationValue" ->
          second
              .getDestinationSelectorConfig()
              .getValueSelectorConfig()
              .withSelectionOrder(order)
              .withSelectedCountLimit(1L);
      case "basicEntity" ->
          basic.getEntitySelectorConfig().withSelectionOrder(order).withSelectedCountLimit(1L);
      case "basicValue" ->
          basic.getValueSelectorConfig().withSelectionOrder(order).withSelectedCountLimit(1L);
      case "phaseEntity" ->
          phase.withEntitySelectorConfig(
              new EntitySelectorConfig().withSelectionOrder(order).withSelectedCountLimit(1L));
      default -> throw new IllegalArgumentException("Unknown selector location: " + location);
    }
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SolverFactory.create(config(phase)).buildSolver())
        .withMessageContaining("joint exhaustiveSearch")
        .withMessageContaining("selectionOrder (" + order + ")")
        .withMessageContaining("selectedCountLimit (1)")
        .withMessageContaining("firstValueList")
        .withMessageContaining("ORIGINAL, SORTED or SHUFFLED");
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"ORIGINAL", "SORTED", "SHUFFLED"})
  void finiteSelectionOrdersRetainAllJointCompletions(SelectionOrder order) {
    var product = targetedProduct();
    for (var child : product.getMoveSelectorList()) {
      child.withCacheType(SelectionCacheType.STEP).withSelectionOrder(order);
      if (order == SelectionOrder.SORTED) {
        child.withComparatorClass(MoveComparator.class);
      }
    }
    var phase =
        new ExhaustiveSearchPhaseConfig()
            .withExhaustiveSearchType(ExhaustiveSearchType.BRUTE_FORCE)
            .withMoveSelectorConfig(product);
    CoupledCalculator.completeStates.clear();
    var result =
        SolverFactory.<TestdataMultipleListSolution>create(config(phase))
            .buildSolver()
            .solve(problem());
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(100));
    assertThat(CoupledCalculator.completeStates).hasSize(48);
  }

  public static class MoveComparator implements Comparator<Object> {
    @Override
    public int compare(Object left, Object right) {
      return left.toString().compareTo(right.toString());
    }
  }

  private static CartesianProductMoveSelectorConfig targetedProduct() {
    return new CartesianProductMoveSelectorConfig()
        .withMoveSelectors(
            basicSelector(TestdataMultipleListEntity.class, "basicValue"),
            basicSelector(TestdataMultipleListSecondValue.class, "quantity"),
            listSelector("firstValueList"),
            listSelector("secondValueList"));
  }

  private static ChangeMoveSelectorConfig basicSelector(Class<?> entityClass, String name) {
    return new ChangeMoveSelectorConfig()
        .withEntitySelectorConfig(new EntitySelectorConfig(entityClass))
        .withValueSelectorConfig(new ValueSelectorConfig(name));
  }

  private static ListChangeMoveSelectorConfig listSelector(String name) {
    return new ListChangeMoveSelectorConfig()
        .withValueSelectorConfig(new ValueSelectorConfig(name))
        .withDestinationSelectorConfig(
            new DestinationSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig(TestdataMultipleListEntity.class))
                .withValueSelectorConfig(new ValueSelectorConfig(name)));
  }

  static Stream<Arguments> basicCacheModes() {
    return Stream.of(SelectionCacheType.STEP, SelectionCacheType.PHASE)
        .flatMap(cache -> Stream.of(Arguments.of(cache, false), Arguments.of(cache, true)));
  }

  @ParameterizedTest
  @MethodSource("basicCacheModes")
  void cachedBasicCandidatesKeepLiveJointDecisionFiltering(
      SelectionCacheType cache, boolean product) {
    var solver =
        SolverFactory.<TestdataMultipleListSolution>create(config(cachedBasicPhase(cache, product)))
            .buildSolver();
    for (int solve = 0; solve < 2; solve++) {
      CoupledCalculator.completeStates.clear();
      var result = solver.solve(problem());
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(independentOptimum()));
      assertThat(CoupledCalculator.completeStates).hasSize(48);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void solverCacheRetainsExistingEntitySelectorRestriction(boolean product) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                SolverFactory.create(config(cachedBasicPhase(SelectionCacheType.SOLVER, product)))
                    .buildSolver())
        .withMessage(
            "The minimumCacheType (SOLVER) is not supported here. Please use PHASE instead.");
  }

  private static ExhaustiveSearchPhaseConfig cachedBasicPhase(
      SelectionCacheType cache, boolean product) {
    var basic =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataMultipleListEntity.class))
            .withValueSelectorConfig(new ValueSelectorConfig("basicValue"))
            .withCacheType(cache);
    var quantity =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(TestdataMultipleListSecondValue.class))
            .withValueSelectorConfig(new ValueSelectorConfig("quantity"))
            .withCacheType(cache);
    return new ExhaustiveSearchPhaseConfig()
        .withExhaustiveSearchType(ExhaustiveSearchType.BRUTE_FORCE)
        .withMoveSelectorConfig(
            product
                ? new CartesianProductMoveSelectorConfig()
                    .withMoveSelectors(
                        basic,
                        quantity,
                        listSelector("firstValueList"),
                        listSelector("secondValueList"))
                : new UnionMoveSelectorConfig()
                    .withMoveSelectors(
                        basic,
                        quantity,
                        listSelector("firstValueList"),
                        listSelector("secondValueList")));
  }

  @Test
  void rejectsIncompleteAndAmbiguousExplicitSelectors() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                SolverFactory.create(
                        config(
                            new ExhaustiveSearchPhaseConfig()
                                .withMoveSelectorConfig(listSelector("firstValueList"))))
                    .buildSolver())
        .withMessageContaining("does not cover")
        .withMessageContaining("secondValueList");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                SolverFactory.create(
                        config(
                            new ExhaustiveSearchPhaseConfig()
                                .withMoveSelectorConfig(new ListChangeMoveSelectorConfig())))
                    .buildSolver())
        .withMessageContaining("must target one variable");
  }

  @Test
  void terminationAndSolverReuseReleaseTheDecisionPlan() {
    var phase =
        new ExhaustiveSearchPhaseConfig()
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(15));
    var solver = SolverFactory.<TestdataMultipleListSolution>create(config(phase)).buildSolver();
    var first = solver.solve(problem());
    var second = solver.solve(problem());
    assertThat(first.getScore()).isEqualTo(second.getScore());
    for (int i = 0; i < first.getEntityList().size(); i++) {
      assertThat(second.getEntityList().get(i)).isNotSameAs(first.getEntityList().get(i));
    }
  }

  static Stream<Arguments> optionalModes() {
    return Stream.of(ExhaustiveSearchType.values())
        .flatMap(type -> Stream.of(Arguments.of(type, false), Arguments.of(type, true)));
  }

  @ParameterizedTest
  @MethodSource("optionalModes")
  void optionalValuesAndEntityRangesRespectPinnedPrefixes(
      ExhaustiveSearchType type, boolean product) {
    var requiredA = new RequiredValue("A");
    var requiredB = new RequiredValue("B");
    var optionalX = new OptionalValue("X");
    var optionalY = new OptionalValue("Y");
    var problem = new RangeSolution();
    problem.required = List.of(requiredA, requiredB);
    problem.optional = List.of(optionalX, optionalY);
    var first = new RequiredOwner();
    first.allowed = List.of(requiredA);
    first.values.add(requiredA);
    first.pin = 1;
    var second = new RequiredOwner();
    second.allowed = List.of(requiredB);
    problem.requiredOwners = List.of(first, second);
    var owner = new OptionalOwner();
    owner.allowed = problem.optional;
    problem.optionalOwners = List.of(owner);
    var phase = new ExhaustiveSearchPhaseConfig().withExhaustiveSearchType(type);
    if (product) {
      phase.withMoveSelectorConfig(
          new CartesianProductMoveSelectorConfig()
              .withMoveSelectors(
                  rangeListSelector(RequiredOwner.class), rangeListSelector(OptionalOwner.class)));
    }
    var config =
        new SolverConfig()
            .withSolutionClass(RangeSolution.class)
            .withEntityClasses(RequiredOwner.class, OptionalOwner.class)
            .withEasyScoreCalculatorClass(RangeCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(phase);
    var result = SolverFactory.<RangeSolution>create(config).buildSolver().solve(problem);
    assertThat(result.score).isEqualTo(SimpleScore.of(7));
    assertThat(result.requiredOwners.getFirst().values)
        .extracting(RequiredValue::code)
        .containsExactly("A");
    assertThat(result.requiredOwners.get(1).values)
        .extracting(RequiredValue::code)
        .containsExactly("B");
    assertThat(result.optionalOwners.getFirst().values)
        .extracting(OptionalValue::code)
        .containsExactly("Y");
  }

  private static ListChangeMoveSelectorConfig rangeListSelector(Class<?> owner) {
    return new ListChangeMoveSelectorConfig()
        .withValueSelectorConfig(new ValueSelectorConfig("values"))
        .withDestinationSelectorConfig(
            new DestinationSelectorConfig()
                .withEntitySelectorConfig(new EntitySelectorConfig(owner))
                .withValueSelectorConfig(new ValueSelectorConfig("values")));
  }

  @Test
  void emptyListPopulationsAreAlreadyComplete() {
    var problem = new RangeSolution();
    problem.required = List.of();
    problem.optional = List.of();
    var requiredOwner = new RequiredOwner();
    requiredOwner.allowed = List.of();
    var optionalOwner = new OptionalOwner();
    optionalOwner.allowed = List.of();
    problem.requiredOwners = List.of(requiredOwner);
    problem.optionalOwners = List.of(optionalOwner);
    var config =
        new SolverConfig()
            .withSolutionClass(RangeSolution.class)
            .withEntityClasses(RequiredOwner.class, OptionalOwner.class)
            .withEasyScoreCalculatorClass(RangeCalculator.class)
            .withPhases(new ExhaustiveSearchPhaseConfig());
    var result = SolverFactory.<RangeSolution>create(config).buildSolver().solve(problem);
    assertThat(result.score).isEqualTo(SimpleScore.ZERO);
    assertThat(result.requiredOwners.getFirst().values).isEmpty();
    assertThat(result.optionalOwners.getFirst().values).isEmpty();
  }

  public record RequiredValue(String code) {}

  public record OptionalValue(String code) {}

  @PlanningEntity
  public static class RequiredOwner {
    @PlanningListVariable(valueRangeProviderRefs = "requiredRange")
    public List<RequiredValue> values = new ArrayList<>();

    @ValueRangeProvider(id = "requiredRange")
    public List<RequiredValue> allowed;

    @PlanningPinToIndex public int pin;
  }

  @PlanningEntity
  public static class OptionalOwner {
    @PlanningListVariable(valueRangeProviderRefs = "optionalRange", allowsUnassignedValues = true)
    public List<OptionalValue> values = new ArrayList<>();

    @ValueRangeProvider(id = "optionalRange")
    public List<OptionalValue> allowed;
  }

  @PlanningSolution
  public static class RangeSolution {
    @PlanningEntityCollectionProperty public List<RequiredOwner> requiredOwners;
    @PlanningEntityCollectionProperty public List<OptionalOwner> optionalOwners;
    @ProblemFactCollectionProperty public List<RequiredValue> required;
    @ProblemFactCollectionProperty public List<OptionalValue> optional;
    @PlanningScore public SimpleScore score;
  }

  public static class RangeCalculator implements EasyScoreCalculator<RangeSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(RangeSolution solution) {
      return SimpleScore.of(
          solution.optionalOwners.stream()
              .flatMap(owner -> owner.values.stream())
              .mapToInt(value -> value.code().equals("Y") ? 7 : -20)
              .sum());
    }
  }
}
