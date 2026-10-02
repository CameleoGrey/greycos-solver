package greycos.solver.core.impl.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.decider.forager.DefaultConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.SAME_THREAD)
class SortedNearbyConstructionHeuristicTest {

  private static final ThreadLocal<Observation> OBSERVATION = new ThreadLocal<>();

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void globallySortedEarlyPickRetainsTheConfiguredFirstEntity(String moveThreads) {
    var move = sortedChange(ReverseEntityComparator.class);
    var phase =
        phase(move, 9)
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig()
                    .withPickEarlyType(
                        ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE));
    var config = basicConfig(phase, moveThreads);
    var solved = solve(config, TestdataSolution.generateUninitializedSolution(3, 3));
    assertThat(solved.getEntityList().get(2).getValue()).isSameAs(solved.getValueList().get(2));
    assertThat(solved.getEntityList().get(0).getValue()).isNull();
    assertThat(solved.getEntityList().get(1).getValue()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void arbitraryInterleavingAndEarlyPickUseGlobalCachedOrdinals(String moveThreads) {
    var phase =
        phase(sortedChange(InterleavingComparator.class), 3).withForagerConfig(recordingForager(2));
    solve(basicConfig(phase, moveThreads), TestdataSolution.generateUninitializedSolution(3, 3));
    assertThat(OBSERVATION.get().moves).containsExactly("2:value=0", "1:value=0");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void tiesRetainStableCachedOrder(String moveThreads) {
    solve(
        basicConfig(phase(sortedChange(EqualComparator.class), 3), moveThreads),
        TestdataSolution.generateUninitializedSolution(3, 2));
    assertThat(OBSERVATION.get().moves)
        .containsExactly(
            "0:value=0", "0:value=1", "0:value=2", "1:value=0", "1:value=1", "1:value=2");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void customSorterRunsOnlyOnItsAuthoritativeCache(String moveThreads) {
    var move =
        new ChangeMoveSelectorConfig()
            .withSelectionOrder(SelectionOrder.SORTED)
            .withCacheType(SelectionCacheType.PHASE)
            .withSorterClass(PopulationDependentSorter.class);
    solve(
        basicConfig(phase(move, 1), moveThreads),
        TestdataSolution.generateUninitializedSolution(3, 3));
    assertThat(OBSERVATION.get().sorterCalls).isEqualTo(1);
    assertThat(OBSERVATION.get().moves).containsExactly("2:value=0", "1:value=0", "0:value=0");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void filtersAndCountLimitsDefineTheAdmittedSortedPopulation(String moveThreads) {
    var move =
        sortedChange(ReverseEntityComparator.class)
            .withFilterClass(ExcludeLastValue.class)
            .withSelectedCountLimit(3L);
    solve(
        basicConfig(phase(move, 1), moveThreads),
        TestdataSolution.generateUninitializedSolution(3, 3));
    assertThat(OBSERVATION.get().moves).containsExactly("2:value=0", "1:value=1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void eachInterleavedOriginWidensUsingOnlyItsOwnDelayedScores(String moveThreads) {
    var config =
        new SolverConfig()
            .withSolutionClass(HardSolution.class)
            .withEntityClasses(HardEntity.class)
            .withEasyScoreCalculatorClass(HardCalculator.class)
            .withNearbyDistanceMeterClass(HardMeter.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(moveThreads)
            .withPhases(
                phase(
                    sortedChange(InterleavingComparator.class)
                        .withValueSelectorConfig(new ValueSelectorConfig("value")),
                    1));
    var problem = new HardSolution();
    problem.values =
        List.of(
            new TestdataValue("Value 0"),
            new TestdataValue("Value 1"),
            new TestdataValue("Value 2"),
            new TestdataValue("Value 3"));
    problem.entities = List.of(new HardEntity("Entity 0"), new HardEntity("Entity 1"));
    solve(config, problem);
    assertThat(OBSERVATION.get().moves)
        .containsExactly("1:value=0", "0:value=0", "1:value=1", "1:value=2", "1:value=3");
    assertThat(OBSERVATION.get().widenings).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void sortedOuterCartesianPreservesMimicAndGlobalInterleaving(String moveThreads) {
    var product =
        product(false)
            .withSelectionOrder(SelectionOrder.SORTED)
            .withCacheType(SelectionCacheType.PHASE)
            .withComparatorClass(InterleavingComparator.class);
    solve(
        multiConfig(phase(product, 4), moveThreads),
        TestdataMultiVarSolution.generateUninitializedSolution(2, 2));
    assertThat(OBSERVATION.get().moves)
        .containsExactly(
            "1:primaryValue=0+1:secondaryValue=0", "1:primaryValue=0+1:secondaryValue=1",
            "0:primaryValue=0+0:secondaryValue=0", "0:primaryValue=0+0:secondaryValue=1",
            "1:primaryValue=1+1:secondaryValue=0", "1:primaryValue=1+1:secondaryValue=1",
            "0:primaryValue=1+0:secondaryValue=0", "0:primaryValue=1+0:secondaryValue=1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void sortedFirstCartesianChildRestoresItsRecorderAndGlobalTraversalOrder(String moveThreads) {
    solve(
        multiConfig(phase(product(true), 4), moveThreads),
        TestdataMultiVarSolution.generateUninitializedSolution(2, 2));
    assertThat(OBSERVATION.get().moves)
        .containsExactly(
            "1:primaryValue=0+1:secondaryValue=0", "1:primaryValue=0+1:secondaryValue=1",
            "0:primaryValue=0+0:secondaryValue=0", "0:primaryValue=0+0:secondaryValue=1",
            "1:primaryValue=1+1:secondaryValue=0", "1:primaryValue=1+1:secondaryValue=1",
            "0:primaryValue=1+0:secondaryValue=0", "0:primaryValue=1+0:secondaryValue=1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void nestedSortedCompositeRetainsLeafProfilesAndUnionBranchOrder(String moveThreads) {
    var sortedUnion =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new ChangeMoveSelectorConfig()
                    .withValueSelectorConfig(new ValueSelectorConfig("primaryValue")))
            .withSelectionOrder(SelectionOrder.SORTED)
            .withCacheType(SelectionCacheType.PHASE)
            .withComparatorClass(InterleavingComparator.class);
    var union =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                sortedUnion,
                new ChangeMoveSelectorConfig()
                    .withValueSelectorConfig(new ValueSelectorConfig("secondaryValue")));
    solve(
        multiConfig(phase(union, 2), moveThreads),
        TestdataMultiVarSolution.generateUninitializedSolution(2, 2));
    assertThat(OBSERVATION.get().moves)
        .containsExactly(
            "1:primaryValue=0",
            "0:primaryValue=0",
            "1:primaryValue=1",
            "0:primaryValue=1",
            "0:secondaryValue=0",
            "0:secondaryValue=1",
            "1:secondaryValue=0",
            "1:secondaryValue=1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void originalUnionKeepsCustomBranchesLazyBeforeItsSortedBranch(String moveThreads) {
    var union =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new MoveIteratorFactoryConfig()
                    .withMoveIteratorFactoryClass(CountingMoveFactory.class),
                sortedChange(InterleavingComparator.class));
    var construction = phase(union, 3).withForagerConfig(recordingForager(2));
    solve(
        basicConfig(construction, moveThreads).withMoveThreadBufferSize(1),
        TestdataSolution.generateUninitializedSolution(3, 1));
    assertThat(OBSERVATION.get().moves).hasSize(2);
    assertThat(OBSERVATION.get().generated).isBetween(2, 3);
    assertThat(OBSERVATION.get().meterCalls).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void filteredOriginalUnionKeepsCustomBranchesLazyBeforeItsSortedBranch(String moveThreads) {
    var union =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new MoveIteratorFactoryConfig()
                    .withMoveIteratorFactoryClass(CountingMoveFactory.class),
                sortedChange(InterleavingComparator.class))
            .withFilterClass(ExcludeLastValue.class);
    var construction = phase(union, 3).withForagerConfig(recordingForager(2));
    solve(
        basicConfig(construction, moveThreads).withMoveThreadBufferSize(1),
        TestdataSolution.generateUninitializedSolution(3, 1));
    assertThat(OBSERVATION.get().moves).hasSize(2);
    assertThat(OBSERVATION.get().generated).isBetween(2, 8);
    assertThat(OBSERVATION.get().meterCalls).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void explicitOriginalUnionOrderAppliesWithinItsChildNeighborhood(String moveThreads) {
    var union =
        new UnionMoveSelectorConfig()
            .withSelectionOrder(SelectionOrder.ORIGINAL)
            .withMoveSelectors(new ChangeMoveSelectorConfig());
    var construction = phase(union, 3).withForagerConfig(recordingForager(1));
    solve(
        basicConfig(construction, moveThreads).withNearbyDistanceMeterClass(InverseMeter.class),
        TestdataSolution.generateUninitializedSolution(3, 1));
    assertThat(OBSERVATION.get().moves).containsExactly("0:value=0");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void canonicalOptionalTailKeepsItsOwnOriginCachedOrdinal(String moveThreads) {
    var primary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("optionalOrigin"))
            .withValueSelectorConfig(new ValueSelectorConfig("a"))
            .withFilterClass(OnlyNullOnFirstEntity.class);
    var secondary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("optionalOrigin"))
            .withValueSelectorConfig(new ValueSelectorConfig("b"))
            .withFilterClass(OnlyNullOnFirstEntity.class);
    var product =
        new CartesianProductMoveSelectorConfig()
            .withMoveSelectors(primary, secondary)
            .withSelectionOrder(SelectionOrder.SORTED)
            .withCacheType(SelectionCacheType.PHASE)
            .withComparatorClass(NullFirstComparator.class);
    var config =
        new SolverConfig()
            .withSolutionClass(OptionalSolution.class)
            .withEntityClasses(OptionalEntity.class)
            .withEasyScoreCalculatorClass(OptionalCalculator.class)
            .withNearbyDistanceMeterClass(OptionalMeter.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(moveThreads)
            .withPhases(phase(product, 1));
    var problem = new OptionalSolution();
    problem.values = List.of(new TestdataValue("Value 0"));
    problem.entities = List.of(new OptionalEntity("Entity 0"), new OptionalEntity("Entity 1"));
    solve(config, problem);
    assertThat(OBSERVATION.get().moves).containsExactly("no-change", "1:a=0+1:b=0", "no-change");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void nestedDependentProductCapturesRecorderSnapshotsBeforeDeferredSortedEmission(
      String moveThreads) {
    var primary =
        sortedChange(InterleavingComparator.class)
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("nestedPrimary"))
            .withValueSelectorConfig(new ValueSelectorConfig("value"));
    var secondary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("nestedSecondary"))
            .withValueSelectorConfig(new ValueSelectorConfig("secondary"));
    var inner = new CartesianProductMoveSelectorConfig().withMoveSelectors(primary, secondary);
    var tertiary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("nestedSecondary"))
            .withValueSelectorConfig(new ValueSelectorConfig("tertiary"));
    var outer = new CartesianProductMoveSelectorConfig().withMoveSelectors(inner, tertiary);
    var config =
        new SolverConfig()
            .withSolutionClass(HardSolution.class)
            .withEntityClasses(HardEntity.class)
            .withEasyScoreCalculatorClass(HardCalculator.class)
            .withNearbyDistanceMeterClass(HardMeter.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(moveThreads)
            .withPhases(phase(outer, 32));
    var problem = new HardSolution();
    problem.values = List.of(new TestdataValue("Value 0"), new TestdataValue("Value 1"));
    problem.entities = List.of(new HardEntity("Entity 0"), new HardEntity("Entity 1"));
    solve(config, problem);
    assertThat(OBSERVATION.get().moves).hasSize(32);
    assertThat(OBSERVATION.get().moves.getFirst())
        .isEqualTo("1:value=0+0:secondary=0+0:tertiary=0");
    assertThat(OBSERVATION.get().moves)
        .allSatisfy(
            description -> {
              var children = description.split("\\+");
              assertThat(children[1].charAt(0)).isEqualTo(children[2].charAt(0));
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void nestedDependentProductKeepsReplayOriginsFiniteAcrossSortedWideningRounds(
      String moveThreads) {
    var primary =
        sortedChange(InterleavingComparator.class)
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("nestedPrimary"))
            .withValueSelectorConfig(new ValueSelectorConfig("value"));
    var secondary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("nestedSecondary"))
            .withValueSelectorConfig(new ValueSelectorConfig("secondary"));
    var inner = new CartesianProductMoveSelectorConfig().withMoveSelectors(primary, secondary);
    var tertiary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("nestedSecondary"))
            .withValueSelectorConfig(new ValueSelectorConfig("tertiary"));
    var outer = new CartesianProductMoveSelectorConfig().withMoveSelectors(inner, tertiary);
    var config =
        new SolverConfig()
            .withSolutionClass(HardSolution.class)
            .withEntityClasses(HardEntity.class)
            .withEasyScoreCalculatorClass(HardCalculator.class)
            .withNearbyDistanceMeterClass(HardMeter.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(moveThreads)
            .withPhases(phase(outer, 40));
    var problem = new HardSolution();
    problem.values =
        java.util.stream.IntStream.range(0, 8)
            .mapToObj(i -> new TestdataValue("Value " + i))
            .toList();
    problem.entities = List.of(new HardEntity("Entity 0"), new HardEntity("Entity 1"));
    solve(config, problem);
    assertThat(OBSERVATION.get().moves).hasSize(680).doesNotHaveDuplicates();
    assertThat(OBSERVATION.get().widenings).isEqualTo(4);
    assertThat(OBSERVATION.get().moves.getFirst())
        .isEqualTo("1:value=0+0:secondary=0+0:tertiary=0");
    assertThat(OBSERVATION.get().moves)
        .allSatisfy(
            description -> {
              var children = description.split("\\+");
              assertThat(children[1].charAt(0)).isEqualTo(children[2].charAt(0));
            });
  }

  private static ChangeMoveSelectorConfig sortedChange(Class<? extends Comparator> comparator) {
    return new ChangeMoveSelectorConfig()
        .withSelectionOrder(SelectionOrder.SORTED)
        .withCacheType(SelectionCacheType.PHASE)
        .withComparatorClass(comparator);
  }

  private static CartesianProductMoveSelectorConfig product(boolean sortedFirstChild) {
    var primary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("sortedOrigin"))
            .withValueSelectorConfig(new ValueSelectorConfig("primaryValue"));
    if (sortedFirstChild) {
      primary
          .withSelectionOrder(SelectionOrder.SORTED)
          .withCacheType(SelectionCacheType.PHASE)
          .withComparatorClass(InterleavingComparator.class);
    }
    return new CartesianProductMoveSelectorConfig()
        .withMoveSelectors(
            primary,
            new ChangeMoveSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig().withMimicSelectorRef("sortedOrigin"))
                .withValueSelectorConfig(new ValueSelectorConfig("secondaryValue")));
  }

  private static ConstructionHeuristicPhaseConfig phase(MoveSelectorConfig<?> move, int size) {
    return new ConstructionHeuristicPhaseConfig()
        .withNearbySelectionAutoConfigurationEnabled(true)
        .withEntityPlacerConfig(new PooledEntityPlacerConfig().withMoveSelectorConfig(move))
        .withNearbySelectionSize(size)
        .withForagerConfig(recordingForager(Integer.MAX_VALUE))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
  }

  private static ConstructionHeuristicForagerConfig recordingForager(int stopAfter) {
    return new ConstructionHeuristicForagerConfig()
        .withForagerClass(RecordingForager.class)
        .withCustomProperties(Map.of("stopAfter", Integer.toString(stopAfter)));
  }

  private static SolverConfig basicConfig(ConstructionHeuristicPhaseConfig phase, String threads) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(ZeroCalculator.class)
        .withNearbyDistanceMeterClass(BasicMeter.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withMoveThreadCount(threads)
        .withPhases(phase);
  }

  private static SolverConfig multiConfig(ConstructionHeuristicPhaseConfig phase, String threads) {
    return new SolverConfig()
        .withSolutionClass(TestdataMultiVarSolution.class)
        .withEntityClasses(TestdataMultiVarEntity.class)
        .withEasyScoreCalculatorClass(MultiCalculator.class)
        .withNearbyDistanceMeterClass(MultiMeter.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withMoveThreadCount(threads)
        .withPhases(phase);
  }

  private static <Solution_> Solution_ solve(SolverConfig config, Solution_ problem) {
    OBSERVATION.set(new Observation());
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> stepScope) {
            if (stepScope instanceof ConstructionHeuristicStepScope<Solution_> construction) {
              OBSERVATION.get().widenings += construction.getNearbyWideningCount();
            }
          }
        });
    return solver.solve(problem);
  }

  private static int index(Object object) {
    var code = ((TestdataObject) object).getCode();
    return Integer.parseInt(code.substring(code.lastIndexOf(' ') + 1));
  }

  private static ChangeMove<?> first(Move<?> move) {
    return move instanceof SelectorBasedCompositeMove<?> composite
        ? first(composite.getMoves()[0])
        : (ChangeMove<?>) move;
  }

  private static String description(Move<?> move) {
    if (move instanceof SelectorBasedNoChangeMove<?>) {
      return "no-change";
    }
    if (move instanceof SelectorBasedCompositeMove<?> composite) {
      return java.util.Arrays.stream(composite.getMoves())
          .map(SortedNearbyConstructionHeuristicTest::description)
          .collect(java.util.stream.Collectors.joining("+"));
    }
    var change = (ChangeMove<?>) move;
    return index(change.getEntity())
        + ":"
        + change.getVariableDescriptor().getVariableName()
        + "="
        + (change.getToPlanningValue() == null ? "null" : index(change.getToPlanningValue()));
  }

  private static final class Observation {
    private final List<String> moves = new ArrayList<>();
    private int sorterCalls;
    private long widenings;
    private int generated;
    private int meterCalls;
  }

  public static final class RecordingForager extends DefaultConstructionHeuristicForager<Object> {
    private int stopAfter = Integer.MAX_VALUE;

    public RecordingForager() {
      super(ConstructionHeuristicPickEarlyType.NEVER);
    }

    public void setStopAfter(String stopAfter) {
      this.stopAfter = Integer.parseInt(stopAfter);
    }

    @Override
    public void addMove(ConstructionHeuristicMoveScope<Object> moveScope) {
      super.addMove(moveScope);
      OBSERVATION.get().moves.add(description(moveScope.getMove()));
    }

    @Override
    public boolean isQuitEarly() {
      return selectedMoveCount >= stopAfter;
    }
  }

  public static final class ReverseEntityComparator implements Comparator<Move<?>> {
    @Override
    public int compare(Move<?> left, Move<?> right) {
      var a = first(left);
      var b = first(right);
      var comparison = Integer.compare(index(b.getEntity()), index(a.getEntity()));
      return comparison != 0
          ? comparison
          : Integer.compare(index(b.getToPlanningValue()), index(a.getToPlanningValue()));
    }
  }

  public static final class InterleavingComparator implements Comparator<Move<?>> {
    @Override
    public int compare(Move<?> left, Move<?> right) {
      var a = first(left);
      var b = first(right);
      var comparison =
          Integer.compare(index(a.getToPlanningValue()), index(b.getToPlanningValue()));
      return comparison != 0
          ? comparison
          : Integer.compare(index(b.getEntity()), index(a.getEntity()));
    }
  }

  public static final class EqualComparator implements Comparator<Move<?>> {
    @Override
    public int compare(Move<?> left, Move<?> right) {
      return 0;
    }
  }

  public static final class PopulationDependentSorter
      implements SelectionSorter<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public void sort(TestdataSolution solution, List<Move<TestdataSolution>> moves) {
      OBSERVATION.get().sorterCalls++;
      Comparator<Move<?>> comparator = new ReverseEntityComparator();
      if (moves.size() < 9) {
        comparator = comparator.reversed();
      }
      moves.sort(comparator);
    }

    @Override
    public SortedSet<Move<TestdataSolution>> sort(
        TestdataSolution solution, Set<Move<TestdataSolution>> moves) {
      throw new UnsupportedOperationException("Only move-list sorting is supported.");
    }
  }

  public static final class ExcludeLastValue
      implements SelectionFilter<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public boolean accept(ScoreDirector<TestdataSolution> director, Move<TestdataSolution> move) {
      return index(first(move).getToPlanningValue()) < 2;
    }
  }

  public static final class ZeroCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class MultiCalculator
      implements EasyScoreCalculator<TestdataMultiVarSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataMultiVarSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class BasicMeter
      implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataEntity entity, TestdataValue value) {
      OBSERVATION.get().meterCalls++;
      return index(value);
    }
  }

  public static final class MultiMeter
      implements NearbyDistanceMeter<TestdataMultiVarEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataMultiVarEntity entity, TestdataValue value) {
      return index(value);
    }
  }

  @PlanningEntity
  public static final class HardEntity extends TestdataObject {
    @PlanningVariable(valueRangeProviderRefs = "values")
    public TestdataValue value;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public TestdataValue secondary;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public TestdataValue tertiary;

    public HardEntity() {}

    private HardEntity(String code) {
      super(code);
    }
  }

  @PlanningSolution
  public static final class HardSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    public List<TestdataValue> values;

    @PlanningEntityCollectionProperty public List<HardEntity> entities;

    @PlanningScore public HardSoftScore score;
  }

  public static final class HardCalculator
      implements EasyScoreCalculator<HardSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(HardSolution solution) {
      return HardSoftScore.ofHard(
          -solution.entities.stream()
              .filter(
                  entity -> index(entity) == 1 && entity.value != null && index(entity.value) < 3)
              .count());
    }
  }

  public static final class HardMeter implements NearbyDistanceMeter<HardEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(HardEntity entity, TestdataValue value) {
      return index(value);
    }
  }

  public static final class InverseMeter
      implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataEntity entity, TestdataValue value) {
      return -index(value);
    }
  }

  public static final class CountingMoveFactory
      implements MoveIteratorFactory<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public long getSize(ScoreDirector<TestdataSolution> director) {
      return 10_000;
    }

    @Override
    public Iterator<Move<TestdataSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataSolution> director) {
      var descriptor =
          ((VariableDescriptorAwareScoreDirector<TestdataSolution>) director)
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataEntity.class)
              .getGenuineVariableDescriptor("value");
      var solution = director.getWorkingSolution();
      return new Iterator<>() {
        private int index;

        @Override
        public boolean hasNext() {
          return index < 10_000;
        }

        @Override
        public Move<TestdataSolution> next() {
          OBSERVATION.get().generated++;
          assertThat(OBSERVATION.get().generated).isLessThan(100);
          return new SelectorBasedChangeMove<>(
              descriptor,
              solution.getEntityList().getFirst(),
              solution.getValueList().get(index++ % solution.getValueList().size()));
        }
      };
    }

    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      throw new UnsupportedOperationException("Only original iteration is supported.");
    }
  }

  public static final class NullFirstComparator implements Comparator<Move<?>> {
    @Override
    public int compare(Move<?> left, Move<?> right) {
      var a = first(left);
      var b = first(right);
      var comparison = Integer.compare(index(a.getEntity()), index(b.getEntity()));
      if (comparison != 0) {
        return comparison;
      }
      return Integer.compare(
          a.getToPlanningValue() == null ? -1 : index(a.getToPlanningValue()),
          b.getToPlanningValue() == null ? -1 : index(b.getToPlanningValue()));
    }
  }

  public static final class OnlyNullOnFirstEntity
      implements SelectionFilter<OptionalSolution, Move<OptionalSolution>> {
    @Override
    public boolean accept(ScoreDirector<OptionalSolution> director, Move<OptionalSolution> move) {
      var change = first(move);
      return index(change.getEntity()) != 0 || change.getToPlanningValue() == null;
    }
  }

  @PlanningEntity
  public static final class OptionalEntity extends TestdataObject {
    @PlanningVariable(valueRangeProviderRefs = "values", allowsUnassigned = true)
    public TestdataValue a;

    @PlanningVariable(valueRangeProviderRefs = "values", allowsUnassigned = true)
    public TestdataValue b;

    public OptionalEntity() {}

    private OptionalEntity(String code) {
      super(code);
    }
  }

  @PlanningSolution
  public static final class OptionalSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    public List<TestdataValue> values;

    @PlanningEntityCollectionProperty public List<OptionalEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  public static final class OptionalCalculator
      implements EasyScoreCalculator<OptionalSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(OptionalSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class OptionalMeter
      implements NearbyDistanceMeter<OptionalEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(OptionalEntity entity, TestdataValue value) {
      return index(value);
    }
  }
}
