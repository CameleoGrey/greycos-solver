package greycos.solver.core.impl.cotwin.variable.descriptor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.preview.api.move.builtin.ListChangeMoveProvider;
import greycos.solver.core.preview.api.move.builtin.ListSwapMoveProvider;
import greycos.solver.core.preview.api.move.builtin.SubListChangeMoveProvider;
import greycos.solver.core.preview.api.move.builtin.SubListSwapMoveProvider;
import greycos.solver.core.preview.api.move.builtin.TwoOptListMoveProvider;
import greycos.solver.core.preview.api.move.test.MoveTester;
import greycos.solver.core.preview.api.neighborhood.MoveProvider;
import greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class InheritedListVariablePinningTest {

  private static final Class<?>[] ENTITY_CLASSES = {
    BaseEntity.class, PrefixEntity.class, SiblingEntity.class, PinnedEntity.class, Value.class
  };

  private static SolutionDescriptor<PinSolution> descriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(PinSolution.class, ENTITY_CLASSES);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pinReadersAreResolvedPerConcreteTypeWithoutSharingInstanceState(boolean subclassFirst) {
    var variable = descriptor().getListVariableDescriptor();
    assertThat(variable.supportsPinning()).isTrue();
    var base = new BaseEntity();
    var sibling = new SiblingEntity();
    var prefix = new PrefixEntity();
    prefix.pinIndex = 1;
    var inherited = new PrefixEntity();
    inherited.pinIndex = 2;
    var unconfigured = new UnconfiguredPrefixEntity();
    unconfigured.pinIndex = 3;
    var ordered =
        subclassFirst
            ? List.of(prefix, inherited, unconfigured, base, sibling)
            : List.of(base, sibling, prefix, inherited, unconfigured);
    for (var entity : ordered) {
      int expected = entity instanceof PrefixEntity pinned ? pinned.pinIndex : 0;
      assertThat(variable.getFirstUnpinnedIndex(entity)).isEqualTo(expected);
      assertThat(variable.isElementPinned(new PinSolution(), entity, 0)).isEqualTo(expected > 0);
    }
    prefix.pinIndex = 0;
    assertThat(variable.getFirstUnpinnedIndex(prefix)).isZero();
    assertThat(variable.getFirstUnpinnedIndex(inherited)).isEqualTo(2);
    var pinned = new PinnedEntity();
    pinned.pinned = true;
    assertThat(variable.isElementPinned(new PinSolution(), pinned, 10)).isTrue();
    pinned.pinned = false;
    assertThat(variable.isElementPinned(new PinSolution(), pinned, 0)).isFalse();
  }

  @Test
  void inheritedBasePinReaderRemainsEffective() {
    var variable =
        SolutionDescriptor.buildSolutionDescriptor(
                BasePinSolution.class, BasePinnedEntity.class, ChildPinnedEntity.class)
            .getListVariableDescriptor();
    var base = new BasePinnedEntity();
    base.pinIndex = 1;
    var child = new ChildPinnedEntity();
    child.pinIndex = 2;
    assertThat(variable.supportsPinning()).isTrue();
    assertThat(variable.getFirstUnpinnedIndex(base)).isEqualTo(1);
    assertThat(variable.getFirstUnpinnedIndex(child)).isEqualTo(2);
    assertThat(variable.isElementPinned(new BasePinSolution(), child, 1)).isTrue();
    assertThat(variable.isElementPinned(new BasePinSolution(), child, 2)).isFalse();
  }

  @Test
  void pinCapabilitiesAreScopedToEachSolutionDescriptor() {
    var withoutSubtype =
        SolutionDescriptor.buildSolutionDescriptor(PinSolution.class, BaseEntity.class, Value.class)
            .getListVariableDescriptor();
    var withSubtype = descriptor().getListVariableDescriptor();
    var entity = new PrefixEntity();
    entity.pinIndex = 2;
    assertThat(withSubtype.supportsPinning()).isTrue();
    assertThat(withSubtype.getFirstUnpinnedIndex(entity)).isEqualTo(2);
    assertThat(withoutSubtype.supportsPinning()).isFalse();
    assertThat(withoutSubtype.getFirstUnpinnedIndex(entity)).isZero();
    assertThat(withSubtype.getFirstUnpinnedIndex(entity)).isEqualTo(2);
  }

  static Stream<MoveSelectorConfig<?>> selectors() {
    return Stream.of(
        new ListChangeMoveSelectorConfig(),
        new ListSwapMoveSelectorConfig(),
        new SubListChangeMoveSelectorConfig(),
        new SubListSwapMoveSelectorConfig(),
        new KOptListMoveSelectorConfig(),
        new ListRuinRecreateMoveSelectorConfig()
            .withMinimumRuinedCount(2)
            .withMaximumRuinedCount(2));
  }

  @ParameterizedTest
  @MethodSource("selectors")
  void ordinaryMoveFamiliesKeepSubtypePrefixes(MoveSelectorConfig<?> selector) {
    var solution = solution();
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(PinSolution.class)
            .withEntityClasses(ENTITY_CLASSES)
            .withEasyScoreCalculatorClass(PinScore.class)
            .withMoveThreadCount("NONE")
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(0L)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                    .withMoveSelectorConfig(selector)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(10)));
    var solved = SolverFactory.<PinSolution>create(solverConfig).buildSolver().solve(solution);
    assertPrefixes(solved);
    assertThat(solved.score).isEqualTo(new PinScore().calculateScore(solved));
    assertInverseShadows(solved);
  }

  @Test
  void pinnedSubtypeOwnersCannotBeSourcesOrDestinations() {
    var solution = solution();
    var source = new PinnedEntity();
    source.code = "pinnedSource";
    source.pinned = true;
    source.values = new ArrayList<>(solution.values.subList(0, 4));
    var destination = new PinnedEntity();
    destination.code = "pinnedDestination";
    destination.pinned = true;
    destination.values = new ArrayList<>();
    var movable = new BaseEntity();
    movable.code = "movable";
    movable.values = new ArrayList<>(solution.values.subList(4, 12));
    solution.entities = List.of(source, destination, movable);
    var config =
        new SolverConfig()
            .withSolutionClass(PinSolution.class)
            .withEntityClasses(ENTITY_CLASSES)
            .withEasyScoreCalculatorClass(RewardPinnedChanges.class)
            .withMoveThreadCount("NONE")
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withAcceptorConfig(
                        new greycos.solver.core.config.localsearch.decider.acceptor
                                .LocalSearchAcceptorConfig()
                            .withAcceptorTypeList(
                                List.of(
                                    greycos.solver.core.config.localsearch.decider.acceptor
                                        .AcceptorType.HILL_CLIMBING)))
                    .withForagerConfig(
                        new greycos.solver.core.config.localsearch.decider.forager
                                .LocalSearchForagerConfig()
                            .withAcceptedCountLimit(1000))
                    .withMoveSelectorConfig(
                        new ListChangeMoveSelectorConfig()
                            .withSelectionOrder(SelectionOrder.ORIGINAL))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    var solved = SolverFactory.<PinSolution>create(config).buildSolver().solve(solution);
    assertThat(solved.entities.get(0).values.stream().map(v -> v.id).toList())
        .containsExactly(0, 1, 2, 3);
    assertThat(solved.entities.get(1).values).isEmpty();
    assertInverseShadows(solved);
  }

  @Test
  void previewListDestinationsExcludeRuntimePinnedOwners() {
    var solution = solution();
    var pinned = new PinnedEntity();
    pinned.code = "pinned";
    pinned.pinned = true;
    pinned.values = new ArrayList<>(solution.entities.getFirst().values);
    solution.entities = List.of(pinned, solution.entities.get(1), solution.entities.get(2));
    var meta = descriptor().getMetaModel();
    var variable = meta.genuineEntity(BaseEntity.class).listVariable("values", Value.class);
    var moves =
        NeighborhoodTester.build(new ListChangeMoveProvider<>(variable), meta)
            .using(solution)
            .getMovesAsStream()
            .limit(100)
            .toList();
    assertThat(moves)
        .isNotEmpty()
        .allSatisfy(move -> assertThat(move.getPlanningEntities()).doesNotContain(pinned));
  }

  @Test
  void standardListChangeCannotImproveScoreByMovingTheOnlyPinnedValue() {
    var solution = solution();
    solution.entities = List.of(solution.entities.getFirst());
    solution.values = new ArrayList<>(solution.entities.getFirst().values);
    var config =
        new SolverConfig()
            .withSolutionClass(PinSolution.class)
            .withEntityClasses(ENTITY_CLASSES)
            .withEasyScoreCalculatorClass(PinScore.class)
            .withMoveThreadCount("NONE")
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                    .withMoveSelectorConfig(
                        new ListChangeMoveSelectorConfig()
                            .withSelectionOrder(SelectionOrder.ORIGINAL))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    var solved = SolverFactory.<PinSolution>create(config).buildSolver().solve(solution);
    assertThat(solved.entities.getFirst().values.getFirst().id).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"change", "swap", "subListChange", "subListSwap", "twoOpt"})
  void previewProvidersRespectSubtypePrefixesAndUndo(String type) {
    var solution = solution();
    var metaModel = descriptor().getMetaModel();
    var variable = metaModel.genuineEntity(BaseEntity.class).listVariable("values", Value.class);
    MoveProvider<PinSolution> provider =
        switch (type) {
          case "change" -> new ListChangeMoveProvider<>(variable);
          case "swap" -> new ListSwapMoveProvider<>(variable);
          case "subListChange" -> new SubListChangeMoveProvider<>(variable, 2, 3);
          case "subListSwap" -> new SubListSwapMoveProvider<>(variable, 1, 3);
          default -> new TwoOptListMoveProvider<>(variable);
        };
    var neighborhood = NeighborhoodTester.build(provider, metaModel).using(solution);
    var moves = neighborhood.getMovesAsStream().limit(100).toList();
    assertThat(moves).isNotEmpty();
    var original = solution.entities.stream().map(e -> List.copyOf(e.values)).toList();
    var tester = MoveTester.build(metaModel).using(solution);
    for (var move : moves) {
      tester.executeTemporarily(
          move,
          view -> {
            assertPrefixes(solution);
            assertInverseShadows(solution);
          });
      assertThat(solution.entities.stream().map(e -> e.values).toList()).isEqualTo(original);
      assertInverseShadows(solution);
    }
  }

  private static PinSolution solution() {
    var solution = new PinSolution();
    solution.values = new ArrayList<>();
    for (int i = 0; i < 12; i++) solution.values.add(new Value(i));
    var first = new PrefixEntity();
    first.code = "first";
    first.pinIndex = 1;
    first.values = new ArrayList<>(solution.values.subList(0, 4));
    var second = new PrefixEntity();
    second.code = "second";
    second.pinIndex = 2;
    second.values = new ArrayList<>(solution.values.subList(4, 8));
    var third = new SiblingEntity();
    third.code = "third";
    third.values = new ArrayList<>(solution.values.subList(8, 12));
    solution.entities = List.of(first, second, third);
    // Use the explicit hierarchy; automatic discovery would not configure all test subtypes.
    var factory =
        new greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory<>(
            descriptor(), new PinScore(), EnvironmentMode.FULL_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
    }
    return solution;
  }

  private static void assertPrefixes(PinSolution solution) {
    assertThat(solution.entities.get(0).values.getFirst().id).isZero();
    assertThat(solution.entities.get(1).values.subList(0, 2).stream().map(v -> v.id).toList())
        .containsExactly(4, 5);
  }

  private static void assertInverseShadows(PinSolution solution) {
    assertThat(solution.entities.stream().flatMap(e -> e.values.stream()).toList())
        .containsExactlyInAnyOrderElementsOf(solution.values);
    for (var entity : solution.entities) {
      for (var value : entity.values) assertThat(value.entity).isSameAs(entity);
    }
  }

  @PlanningSolution
  public static class BasePinSolution {
    @PlanningEntityCollectionProperty public List<BasePinnedEntity> entities;

    @greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty
    @ValueRangeProvider(id = "range")
    public List<Integer> values;

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class BasePinnedEntity {
    @PlanningListVariable(valueRangeProviderRefs = "range")
    public List<Integer> values;

    @PlanningPinToIndex public int pinIndex;
  }

  @PlanningEntity
  public static class ChildPinnedEntity extends BasePinnedEntity {}

  @PlanningSolution
  public static class PinSolution {
    @PlanningEntityCollectionProperty public List<BaseEntity> entities;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "range")
    public List<Value> values;

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class BaseEntity {
    @PlanningId public String code;

    @PlanningListVariable(valueRangeProviderRefs = "range")
    public List<Value> values;
  }

  @PlanningEntity
  public static class PrefixEntity extends BaseEntity {
    @PlanningPinToIndex public int pinIndex;
  }

  public static class UnconfiguredPrefixEntity extends PrefixEntity {}

  @PlanningEntity
  public static class SiblingEntity extends BaseEntity {}

  @PlanningEntity
  public static class PinnedEntity extends BaseEntity {
    @PlanningPin public boolean pinned;
  }

  @PlanningEntity
  public static class Value {
    @PlanningId public int id;

    @InverseRelationShadowVariable(sourceVariableName = "values")
    public BaseEntity entity;

    public Value() {}

    Value(int id) {
      this.id = id;
    }
  }

  public static class PinScore implements EasyScoreCalculator<PinSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(PinSolution solution) {
      int score = 0;
      for (var entity : solution.entities) {
        for (int i = 0; i < entity.values.size(); i++) score += (12 - entity.values.get(i).id) * i;
      }
      return SimpleScore.of(score);
    }
  }

  public static class RewardPinnedChanges implements EasyScoreCalculator<PinSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(PinSolution solution) {
      return SimpleScore.of(
          100 * solution.entities.get(1).values.size() - solution.entities.get(0).values.size());
    }
  }
}
