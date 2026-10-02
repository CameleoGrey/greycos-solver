package greycos.solver.core.impl.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.decider.forager.DefaultConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListEntity;
import greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListSolution;
import greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.SAME_THREAD)
class AutomaticNearbyConstructionHeuristicTest {

  private static final ThreadLocal<Observations> OBSERVATIONS = new ThreadLocal<>();
  private static final AtomicLong REPAIR_METER_CALLS = new AtomicLong();
  private static final AtomicLong NESTED_METER_CALLS = new AtomicLong();

  @Test
  void customRandomForagerStopsAfterFirstMoveWhenAutomaticNearbyIsDisabled() {
    assertCustomRandomForager(true, 1, null);
  }

  @Test
  void customRandomForagerStopsAfterThirdMoveWithoutNearbyProfiles() {
    assertCustomRandomForager(false, 3, null);
  }

  @Test
  void customRandomForagerTakesPrecedenceOverConfiguredBuiltinEarlyRule() {
    assertCustomRandomForager(true, 3, ConstructionHeuristicPickEarlyType.NEVER);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void finiteExplicitRandomValuesPreserveCustomEarlyPickWithoutNearbySnapshots(Boolean enabled) {
    var values =
        new ValueSelectorConfig("value")
            .withSelectionOrder(SelectionOrder.RANDOM)
            .withSelectedCountLimit(1000);
    var phase =
        queuedEntityPhase(values)
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig()
                    .withForagerClass(StopAfterForager.class)
                    .withCustomProperties(Map.of("stopAfter", "3")));
    if (enabled != null) {
      phase.withNearbySelectionAutoConfigurationEnabled(enabled);
    }
    var outcome =
        solve(
            basicConfig(phase).withEasyScoreCalculatorClass(ZeroBasicCalculator.class),
            basicProblem(5, 1));
    assertThat(outcome.observations().selectedMoves).containsExactly(3L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(3);
    assertThat(outcome.observations().meterCalls).isZero();
    assertThat(outcome.solution().getEntityList().getFirst().getValue()).isNotNull();
    assertThat(values.getSelectedCountLimit()).isEqualTo(1000L);
  }

  private static void assertCustomRandomForager(
      boolean disabled, int stopAfter, ConstructionHeuristicPickEarlyType pickEarlyType) {
    var forager =
        new ConstructionHeuristicForagerConfig()
            .withForagerClass(StopAfterForager.class)
            .withCustomProperties(Map.of("stopAfter", Integer.toString(stopAfter)));
    if (pickEarlyType != null) {
      forager.withPickEarlyType(pickEarlyType);
    }
    var phase =
        queuedEntityPhase(
                new ValueSelectorConfig("value").withSelectionOrder(SelectionOrder.RANDOM))
            .withForagerConfig(forager)
            .withNearbySelectionAutoConfigurationEnabled(!disabled);
    var config =
        withoutGlobalNearby(basicConfig(phase))
            .withEasyScoreCalculatorClass(ZeroBasicCalculator.class);
    var outcome = solve(config, basicProblem(5, 1));
    assertThat(outcome.observations().selectedMoves).containsExactly((long) stopAfter);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(stopAfter);
    assertThat(outcome.solution().getEntityList().getFirst().getValue()).isNotNull();
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void allNullOptionalCartesianChoiceRemainsAvailable() {
    var problem = pairProblem();
    problem.entities.getFirst().required = problem.values.getFirst();
    var outcome = solve(pairConfig(), problem);
    var entity = outcome.solution().entities.getFirst();
    assertThat(entity.optionalA).isNull();
    assertThat(entity.optionalB).isNull();
    assertThat(entity.required).isNotNull();
    assertThat(outcome.solution().score).isEqualTo(pairScore(outcome.solution()));
    assertThat(outcome.solution().score).isEqualTo(HardSoftScore.ZERO);
  }

  @Test
  void partialNullCartesianChoiceInitializesMandatoryVariableWithoutHardPenalty() {
    var outcome = solve(pairConfig(), pairProblem());
    var entity = outcome.solution().entities.getFirst();
    assertThat(entity.required).isNotNull();
    assertThat(entity.optionalA).isNull();
    assertThat(entity.optionalB).isNull();
    assertThat(outcome.solution().score).isEqualTo(pairScore(outcome.solution()));
    assertThat(outcome.solution().score).isEqualTo(HardSoftScore.ZERO);
  }

  @Test
  void pooledCartesianKeepsAllThreeVariablesOnTheSameEntity() {
    var problem = tripleProblem(2);
    var config =
        tripleConfig(
            new ConstructionHeuristicPhaseConfig()
                .withNearbySelectionAutoConfigurationEnabled(true)
                .withConstructionHeuristicType(ConstructionHeuristicType.ALLOCATE_FROM_POOL)
                .withNearbySelectionSize(1));
    var outcome = solve(config, problem);
    assertThat(outcome.observations().tripleAssignedCounts)
        .containsExactly(List.of(3, 0), List.of(3, 3));
    assertTripleInitialized(outcome.solution());
  }

  @Test
  void nonfirstNestedUnionRetainsEveryVariableBranchDuringPooledConstruction() {
    var primary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("tripleOrigin"))
            .withValueSelectorConfig(new ValueSelectorConfig("primary"));
    var secondary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("tripleOrigin"))
            .withValueSelectorConfig(new ValueSelectorConfig("secondary"));
    var tertiary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("tripleOrigin"))
            .withValueSelectorConfig(new ValueSelectorConfig("tertiary"));
    var product =
        new CartesianProductMoveSelectorConfig()
            .withMoveSelectors(
                primary, new UnionMoveSelectorConfig().withMoveSelectors(secondary, tertiary));
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withEntityPlacerConfig(new PooledEntityPlacerConfig().withMoveSelectorConfig(product))
            .withNearbySelectionSize(1);
    var outcome = solve(tripleConfig(phase), tripleProblem(1));
    assertTripleInitialized(outcome.solution());
    assertThat(outcome.observations().tripleAssignedCounts).containsExactly(List.of(2), List.of(3));
  }

  @Test
  void independentNonfirstRecorderIsRestoredBeforeItsMimicChild() {
    var primary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("independentPrimary"))
            .withValueSelectorConfig(new ValueSelectorConfig("primary"));
    var secondary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("independentSecondary"))
            .withValueSelectorConfig(new ValueSelectorConfig("secondary"));
    var tertiary =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("independentSecondary"))
            .withValueSelectorConfig(new ValueSelectorConfig("tertiary"));
    var product =
        new CartesianProductMoveSelectorConfig().withMoveSelectors(primary, secondary, tertiary);
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withEntityPlacerConfig(new PooledEntityPlacerConfig().withMoveSelectorConfig(product))
            .withNearbySelectionSize(1);
    var outcome = solve(tripleConfig(phase), tripleProblem(2));
    assertThat(outcome.observations().tripleAssignedCounts.getFirst()).isEqualTo(List.of(3, 0));
    assertThat(outcome.observations().meterCalls).isPositive();
    assertThat(outcome.observations().selectedMoves).allMatch(count -> count > 0 && count <= 2);
    assertTripleInitialized(outcome.solution());
  }

  @ParameterizedTest
  @MethodSource("repairConfigurations")
  void basicRuinRecreateUsesEnclosingProfileOnlyWhenEnabled(
      Boolean repairEnabled, String moveThreadCount) {
    REPAIR_METER_CALLS.set(0);
    var outcome =
        solve(
            basicRepairConfig(repairEnabled, moveThreadCount),
            TestdataSolution.generateSolution(3, 3));
    assertMeterCalls(REPAIR_METER_CALLS.get(), repairEnabled);
    assertBasicScore(outcome.solution());
  }

  @ParameterizedTest
  @MethodSource("independentRepairConfigurations")
  void basicInitialConstructionAndRepairFlagsAreIndependent(
      boolean initialEnabled, Boolean repairEnabled, String moveThreadCount) {
    var config = basicRepairConfig(repairEnabled, moveThreadCount);
    config.withPhases(
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(initialEnabled),
        config.getPhaseConfigList().getFirst());
    REPAIR_METER_CALLS.set(0);
    var outcome = solve(config, basicProblem(3, 3));
    assertMeterCalls(outcome.observations().repairMeterCallsAfterConstruction, initialEnabled);
    assertMeterCalls(
        REPAIR_METER_CALLS.get() - outcome.observations().repairMeterCallsAfterConstruction,
        repairEnabled);
    assertBasicScore(outcome.solution());
  }

  private static SolverConfig basicRepairConfig(Boolean repairEnabled, String moveThreadCount) {
    var nearby =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("repairOrigin"))
            .withNearbyDistanceMeterClass(RepairBasicMeter.class);
    var parentChange =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("repairOrigin"))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value").withNearbySelectionConfig(nearby))
            .withFixedProbabilityWeight(0.0);
    var ruin =
        new RuinRecreateMoveSelectorConfig()
            .withMinimumRuinedCount(1)
            .withMaximumRuinedCount(1)
            .withFixedProbabilityWeight(1.0);
    if (repairEnabled != null) {
      ruin.withNearbySelectionAutoConfigurationEnabled(repairEnabled);
    }
    return commonConfig(TestdataSolution.class, TestdataEntity.class)
        .withEasyScoreCalculatorClass(BasicCalculator.class)
        .withMoveThreadCount(moveThreadCount)
        .withPhases(repairLocalSearch(parentChange, ruin));
  }

  @ParameterizedTest
  @MethodSource("repairConfigurations")
  void listRuinRecreateUsesEnclosingProfileOnlyWhenEnabled(
      Boolean repairEnabled, String moveThreadCount) {
    REPAIR_METER_CALLS.set(0);
    var outcome =
        solve(
            listRepairConfig(repairEnabled, moveThreadCount),
            TestdataListSolution.generateInitializedSolution(6, 3));
    assertMeterCalls(REPAIR_METER_CALLS.get(), repairEnabled);
    assertListIntegrity(outcome.solution());
    assertThat(outcome.solution().getScore()).isEqualTo(listScore(outcome.solution()));
  }

  @ParameterizedTest
  @MethodSource("independentRepairConfigurations")
  void listInitialConstructionAndRepairFlagsAreIndependent(
      boolean initialEnabled, Boolean repairEnabled, String moveThreadCount) {
    var config = listRepairConfig(repairEnabled, moveThreadCount);
    config.withPhases(
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(initialEnabled),
        config.getPhaseConfigList().getFirst());
    REPAIR_METER_CALLS.set(0);
    var outcome = solve(config, TestdataListSolution.generateUninitializedSolution(6, 3));
    assertMeterCalls(outcome.observations().repairMeterCallsAfterConstruction, initialEnabled);
    assertMeterCalls(
        REPAIR_METER_CALLS.get() - outcome.observations().repairMeterCallsAfterConstruction,
        repairEnabled);
    assertListIntegrity(outcome.solution());
    assertThat(outcome.solution().getScore()).isEqualTo(listScore(outcome.solution()));
  }

  private static SolverConfig listRepairConfig(Boolean repairEnabled, String moveThreadCount) {
    var nearby =
        new NearbySelectionConfig()
            .withOriginValueSelectorConfig(
                new ValueSelectorConfig().withMimicSelectorRef("repairListOrigin"))
            .withNearbyDistanceMeterClass(RepairListMeter.class);
    var parentChange =
        new ListChangeMoveSelectorConfig()
            .withValueSelectorConfig(
                new ValueSelectorConfig("valueList").withId("repairListOrigin"))
            .withDestinationSelectorConfig(
                new DestinationSelectorConfig().withNearbySelectionConfig(nearby))
            .withFixedProbabilityWeight(0.0);
    var ruin =
        new ListRuinRecreateMoveSelectorConfig()
            .withMinimumRuinedCount(1)
            .withMaximumRuinedCount(1)
            .withFixedProbabilityWeight(1.0);
    if (repairEnabled != null) {
      ruin.withNearbySelectionAutoConfigurationEnabled(repairEnabled);
    }
    return commonConfig(
            TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
        .withEasyScoreCalculatorClass(ListCalculator.class)
        .withMoveThreadCount(moveThreadCount)
        .withPhases(repairLocalSearch(parentChange, ruin));
  }

  private static LocalSearchPhaseConfig repairLocalSearch(
      MoveSelectorConfig<?> parentChange, MoveSelectorConfig<?> ruin) {
    return new LocalSearchPhaseConfig()
        .withAcceptorConfig(
            new LocalSearchAcceptorConfig()
                .withAcceptorTypeList(List.of(AcceptorType.HILL_CLIMBING)))
        .withMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectors(parentChange, ruin))
        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
  }

  private static Stream<Arguments> repairConfigurations() {
    return Stream.<Boolean>of(null, false, true)
        .flatMap(enabled -> Stream.of("NONE", "2").map(threads -> Arguments.of(enabled, threads)));
  }

  private static Stream<Arguments> independentRepairConfigurations() {
    return Stream.of("NONE", "2")
        .flatMap(
            threads ->
                Stream.of(
                    Arguments.of(true, null, threads),
                    Arguments.of(true, false, threads),
                    Arguments.of(false, true, threads)));
  }

  private static void assertMeterCalls(long meterCalls, Boolean enabled) {
    if (Boolean.TRUE.equals(enabled)) {
      assertThat(meterCalls).isPositive();
    } else {
      assertThat(meterCalls).isZero();
    }
  }

  @Test
  void globalProfileOrdersGeneratedValuesByDistance() {
    var outcome =
        solve(
            basicConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)),
            basicProblem(3, 3));

    assertThat(outcome.solution().getEntityList())
        .extracting(entity -> index(entity.getValue()))
        .containsExactly(2, 1, 0);
    assertThat(outcome.observations().meterCalls).isEqualTo(9);
    assertThat(outcome.observations().selectedMoves).containsExactly(3L, 3L, 3L);
    assertBasicScore(outcome.solution());
  }

  @Test
  void constructionOnlyGlobalProfileScoresFortyOfNinetySixValues() {
    var outcome =
        solve(
            basicConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)),
            basicProblem(96, 1));

    assertThat(index(outcome.solution().getEntityList().getFirst().getValue())).isEqualTo(95);
    assertThat(outcome.observations().meterCalls).isEqualTo(96);
    assertThat(outcome.observations().selectedMoves).containsExactly(40L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(40);
    assertBasicScore(outcome.solution());
  }

  @ParameterizedTest
  @MethodSource("constructionConfigurations")
  void basicConstructionUsesGlobalAndUpcomingProfilesOnlyWhenEnabled(
      Boolean enabled, boolean globalProfile) {
    var phase = new ConstructionHeuristicPhaseConfig();
    if (enabled != null) {
      phase.withNearbySelectionAutoConfigurationEnabled(enabled);
    }
    var config = basicConfig(phase);
    if (!globalProfile) {
      var nearby =
          new NearbySelectionConfig()
              .withOriginEntitySelectorConfig(
                  new EntitySelectorConfig().withMimicSelectorRef("upcomingOrigin"))
              .withNearbyDistanceMeterClass(BasicMeter.class);
      var change =
          new ChangeMoveSelectorConfig()
              .withEntitySelectorConfig(new EntitySelectorConfig().withId("upcomingOrigin"))
              .withValueSelectorConfig(
                  new ValueSelectorConfig("value").withNearbySelectionConfig(nearby));
      withoutGlobalNearby(config).withPhases(phase, upcomingLocalSearch(change));
    }
    var outcome = solve(config, basicProblem(96, 1));
    long expectedMoves = Boolean.TRUE.equals(enabled) ? 40 : 96;
    assertMeterCalls(outcome.observations().meterCalls, enabled);
    assertThat(outcome.observations().selectedMoves).containsExactly(expectedMoves);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(expectedMoves);
    assertBasicScore(outcome.solution());
    assertThat(phase.getNearbySelectionAutoConfigurationEnabled()).isEqualTo(enabled);
  }

  @ParameterizedTest
  @MethodSource("constructionConfigurations")
  void listConstructionUsesGlobalAndUpcomingProfilesOnlyWhenEnabled(
      Boolean enabled, boolean globalProfile) {
    var phase = new ConstructionHeuristicPhaseConfig().withNearbySelectionSize(1);
    if (enabled != null) {
      phase.withNearbySelectionAutoConfigurationEnabled(enabled);
    }
    var config = listConfig(phase);
    if (!globalProfile) {
      var nearby =
          new NearbySelectionConfig()
              .withOriginValueSelectorConfig(
                  new ValueSelectorConfig().withMimicSelectorRef("upcomingListOrigin"))
              .withNearbyDistanceMeterClass(ListDestinationMeter.class);
      var change =
          new ListChangeMoveSelectorConfig()
              .withValueSelectorConfig(
                  new ValueSelectorConfig("valueList").withId("upcomingListOrigin"))
              .withDestinationSelectorConfig(
                  new DestinationSelectorConfig().withNearbySelectionConfig(nearby));
      withoutGlobalNearby(config).withPhases(phase, upcomingLocalSearch(change));
    }
    var outcome = solve(config, TestdataListSolution.generateUninitializedSolution(6, 3));
    assertMeterCalls(outcome.observations().meterCalls, enabled);
    if (Boolean.TRUE.equals(enabled)) {
      assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L, 1L, 1L, 1L);
      assertThat(outcome.observations().moveEvaluationCount).isEqualTo(6);
    } else {
      assertThat(outcome.observations().selectedMoves).containsExactly(3L, 4L, 5L, 6L, 7L, 8L);
      assertThat(outcome.observations().moveEvaluationCount).isEqualTo(33);
    }
    assertListIntegrity(outcome.solution());
    assertThat(outcome.solution().getScore()).isEqualTo(listScore(outcome.solution()));
    assertThat(phase.getNearbySelectionAutoConfigurationEnabled()).isEqualTo(enabled);
    assertThat(phase.getNearbySelectionSize()).isEqualTo(1);
  }

  private static Stream<Arguments> constructionConfigurations() {
    return Stream.<Boolean>of(null, false, true)
        .flatMap(enabled -> Stream.of(false, true).map(global -> Arguments.of(enabled, global)));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = false)
  void nearbySelectionSizeDoesNotEnableAutomaticBasicConstruction(Boolean enabled) {
    var phase = new ConstructionHeuristicPhaseConfig().withNearbySelectionSize(7);
    if (enabled != null) {
      phase.withNearbySelectionAutoConfigurationEnabled(enabled);
    }
    var outcome = solve(basicConfig(phase), basicProblem(96, 1));
    assertThat(outcome.observations().meterCalls).isZero();
    assertThat(outcome.observations().selectedMoves).containsExactly(96L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(96);
    assertBasicScore(outcome.solution());
  }

  @Test
  void automaticListConstructionWithoutProfilesPreservesExhaustiveConstruction() {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withNearbySelectionSize(1);
    var outcome =
        solve(
            withoutGlobalNearby(listConfig(phase)),
            TestdataListSolution.generateUninitializedSolution(6, 3));
    assertThat(outcome.observations().meterCalls).isZero();
    assertThat(outcome.observations().selectedMoves).containsExactly(3L, 4L, 5L, 6L, 7L, 8L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(33);
    assertListIntegrity(outcome.solution());
    assertThat(outcome.solution().getScore()).isEqualTo(listScore(outcome.solution()));
  }

  @ParameterizedTest
  @MethodSource("constructionConfigurations")
  void nestedConstructionUsesItsOwnFlag(Boolean enabled, boolean partitioned) {
    var construction = new ConstructionHeuristicPhaseConfig().withNearbySelectionSize(1);
    if (enabled != null) {
      construction.withNearbySelectionAutoConfigurationEnabled(enabled);
    }
    PhaseConfig<?> nested =
        partitioned
            ? new PartitionedSearchPhaseConfig()
                .withSolutionPartitionerClass(OnePartition.class)
                .withPhaseConfigList(List.of(construction))
            : new IslandModelPhaseConfig()
                .withIslandCount(1)
                .withPhaseConfigList(List.of(construction));
    var config =
        basicConfig(construction)
            .withNearbyDistanceMeterClass(NestedBasicMeter.class)
            .withPhases(nested);
    NESTED_METER_CALLS.set(0);
    var outcome = solve(config, basicProblem(96, 1));
    assertMeterCalls(NESTED_METER_CALLS.get(), enabled);
    assertBasicScore(outcome.solution());
  }

  @Test
  void automaticNearbyWithoutAnyProfilePreservesExhaustiveConstruction() {
    var config =
        withoutGlobalNearby(
            basicConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)));
    var outcome = solve(config, basicProblem(96, 1));

    assertThat(outcome.observations().meterCalls).isZero();
    assertThat(outcome.observations().selectedMoves).containsExactly(96L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(96);
    assertBasicScore(outcome.solution());
  }

  @Test
  void configuredInitialSizeRestrictsFirstBatch() {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withNearbySelectionSize(7);
    var outcome = solve(basicConfig(phase), basicProblem(96, 1));

    assertThat(outcome.observations().meterCalls).isEqualTo(96);
    assertThat(outcome.observations().selectedMoves).containsExactly(7L);
    assertBasicScore(outcome.solution());
  }

  @Test
  void wideningScoresOnlyNewRanksAcrossThreeBatches() {
    var problem = hardProblem(170);
    for (var value : problem.values) {
      value.hard0 = value.rank < 130 ? -1 : 0;
      value.soft = -value.rank;
    }
    var outcome =
        solve(
            hardConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)),
            problem);

    assertThat(outcome.solution().entities.getFirst().value.rank).isEqualTo(130);
    assertThat(outcome.observations().selectedMoves).containsExactly(160L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(160);
    assertThat(outcome.observations().scoredRanks)
        .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 160).boxed().toList());
    assertThat(outcome.observations().meterCalls).isEqualTo(170);
    assertHardScore(outcome.solution());
  }

  @Test
  void moveThreadsPreserveProgressiveBatchCountsAndBestAssignment() {
    var problem = hardProblem(170);
    for (var value : problem.values) {
      value.hard0 = value.rank < 130 ? -1 : 0;
      value.soft = -value.rank;
    }
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withMoveThreadCount("2");
    var outcome = solve(hardConfig(phase), problem);

    assertThat(outcome.solution().entities.getFirst().value.rank).isEqualTo(130);
    assertThat(outcome.observations().selectedMoves).containsExactly(160L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(160);
    assertThat(outcome.observations().meterCalls).isEqualTo(170);
    assertHardScore(outcome.solution());
  }

  @Test
  void allHardWorseningCandidatesExhaustFiniteRange() {
    var problem = hardProblem(173);
    for (var value : problem.values) {
      value.hard0 = -1;
      value.soft = -value.rank;
    }
    var outcome =
        solve(
            hardConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)),
            problem);

    assertThat(outcome.solution().entities.getFirst().value.rank).isZero();
    assertThat(outcome.observations().selectedMoves).containsExactly(173L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(173);
    assertThat(outcome.observations().scoredRanks)
        .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 173).boxed().toList());
    assertThat(outcome.observations().meterCalls).isEqualTo(173);
    assertHardScore(outcome.solution());
  }

  @Test
  void softWorseningDoesNotWiden() {
    var problem = hardProblem(95);
    for (var value : problem.values) {
      value.soft = -value.rank - 1;
    }
    var outcome =
        solve(
            hardConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)),
            problem);

    assertThat(outcome.solution().entities.getFirst().value.rank).isZero();
    assertThat(outcome.observations().selectedMoves).containsExactly(40L);
    assertThat(outcome.observations().scoredRanks)
        .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 40).boxed().toList());
    assertHardScore(outcome.solution());
  }

  @Test
  void hardPrefixComparisonIsLexicographic() {
    var problem = hardProblem(95);
    for (var value : problem.values) {
      value.hard0 = 1;
      value.hard1 = -100;
      value.soft = -value.rank;
    }
    var outcome =
        solve(
            hardConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)),
            problem);

    assertThat(outcome.observations().selectedMoves).containsExactly(40L);
    assertThat(outcome.solution().score)
        .isEqualTo(BendableScore.of(new long[] {1, -100}, new long[] {0}));
    assertHardScore(outcome.solution());
  }

  @Test
  void laterHardImprovementDoesNotHideEarlierHardWorsening() {
    var problem = hardProblem(95);
    for (var value : problem.values) {
      value.hard0 = value.rank < 40 ? -1 : 0;
      value.hard1 = value.rank < 40 ? 100 : -1;
      value.soft = -value.rank;
    }
    problem.values.get(60).hard1 = 0;
    var outcome =
        solve(
            hardConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)),
            problem);

    assertThat(outcome.solution().entities.getFirst().value.rank).isEqualTo(60);
    assertThat(outcome.observations().selectedMoves).containsExactly(80L);
    assertHardScore(outcome.solution());
  }

  @Test
  void structuralFlawDoesNotStopWideningDespiteImprovedHardScore() {
    var problem = hardProblem(95);
    for (var value : problem.values) {
      value.structural = value.rank < 40 ? -1 : 0;
      value.hard0 = value.rank < 40 ? 1 : 0;
      value.soft = -value.rank;
    }
    var outcome =
        solve(
            hardConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)),
            problem);

    assertThat(outcome.solution().entities.getFirst().value.rank).isEqualTo(40);
    // The forager excludes structurally flawed moves from its selected count.
    assertThat(outcome.observations().selectedMoves).containsExactly(40L);
    assertThat(outcome.observations().moveEvaluationCount).isEqualTo(80);
    assertThat(outcome.observations().scoredRanks)
        .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 80).boxed().toList());
    assertHardScore(outcome.solution());
  }

  @Test
  void optionalNullCandidateDoesNotHideFartherHardImprovement() {
    var problem = new OptionalHardSolution();
    problem.values = hardProblem(95).values;
    problem.entities = List.of(new OptionalHardEntity());
    for (var value : problem.values) {
      value.hard0 = value.rank == 60 ? 1 : -1;
      value.soft = -value.rank;
    }
    var config =
        commonConfig(OptionalHardSolution.class, OptionalHardEntity.class)
            .withEasyScoreCalculatorClass(OptionalHardCalculator.class)
            .withNearbyDistanceMeterClass(OptionalHardMeter.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true));
    var outcome = solve(config, problem);

    assertThat(outcome.solution().entities.getFirst().value.rank).isEqualTo(60);
    assertThat(outcome.observations().selectedMoves).containsExactly(81L);
    assertThat(outcome.observations().scoredRanks)
        .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 80).boxed().toList());
    assertThat(outcome.observations().meterCalls).isEqualTo(95);
    assertThat(outcome.solution().score).isEqualTo(optionalHardScore(outcome.solution()));
  }

  @Test
  void optionalNullIsScoredOnceAfterAllHardWorseningCandidates() {
    var problem = new OptionalHardSolution();
    problem.values = hardProblem(95).values;
    problem.entities = List.of(new OptionalHardEntity());
    for (var value : problem.values) {
      value.hard0 = -1;
    }
    var config =
        commonConfig(OptionalHardSolution.class, OptionalHardEntity.class)
            .withEasyScoreCalculatorClass(OptionalHardCalculator.class)
            .withNearbyDistanceMeterClass(OptionalHardMeter.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true));
    var outcome = solve(config, problem);

    assertThat(outcome.solution().entities.getFirst().value).isNull();
    assertThat(outcome.observations().selectedMoves).containsExactly(96L);
    assertThat(outcome.observations().scoredRanks)
        .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 95).boxed().toList());
    assertThat(outcome.observations().meterCalls).isEqualTo(95);
    assertThat(outcome.solution().score).isEqualTo(optionalHardScore(outcome.solution()));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void explicitNearbySelectionOverridesGlobalProfileAndPhaseSize(Boolean enabled) {
    var nearby =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("queuedEntity"))
            .withNearbyDistanceMeterClass(OriginalBasicMeter.class);
    var values =
        new ValueSelectorConfig("value")
            .withNearbySelectionConfig(nearby)
            .withSelectedCountLimit(1L);
    var phase = queuedEntityPhase(values).withNearbySelectionSize(7);
    if (enabled != null) {
      phase.withNearbySelectionAutoConfigurationEnabled(enabled);
    }
    var outcome =
        solve(
            basicConfig(phase).withEasyScoreCalculatorClass(ZeroBasicCalculator.class),
            basicProblem(96, 1));

    assertThat(index(outcome.solution().getEntityList().getFirst().getValue())).isZero();
    assertThat(outcome.observations().selectedMoves).containsExactly(1L);
    assertThat(outcome.observations().meterCalls).isEqualTo(96);
    assertThat(values.getNearbySelectionConfig()).isSameAs(nearby);
    assertThat(values.getSelectedCountLimit()).isEqualTo(1L);
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = false)
  void explicitListNearbySelectionWorksWithAutomaticInferenceOff(Boolean enabled) {
    var nearby =
        new NearbySelectionConfig()
            .withOriginValueSelectorConfig(
                new ValueSelectorConfig().withMimicSelectorRef("manualQueuedValue"))
            .withNearbyDistanceMeterClass(ListDestinationMeter.class);
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withEntityPlacerConfig(
                new QueuedValuePlacerConfig()
                    .withValueSelectorConfig(
                        new ValueSelectorConfig("valueList").withId("manualQueuedValue"))
                    .withMoveSelectorConfig(
                        new ListChangeMoveSelectorConfig()
                            .withValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("manualQueuedValue"))
                            .withDestinationSelectorConfig(
                                new DestinationSelectorConfig().withNearbySelectionConfig(nearby))
                            .withSelectedCountLimit(1L)));
    if (enabled != null) {
      phase.withNearbySelectionAutoConfigurationEnabled(enabled);
    }
    var outcome =
        solve(listConfig(phase), TestdataListSolution.generateUninitializedSolution(6, 3));
    assertThat(outcome.observations().meterCalls).isPositive();
    assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L, 1L, 1L, 1L);
    assertListIntegrity(outcome.solution());
    assertThat(outcome.solution().getScore()).isEqualTo(listScore(outcome.solution()));
  }

  @Test
  void explicitSelectionOrderAndCountLimitOverrideAutomaticDefaults() {
    var values =
        new ValueSelectorConfig("value")
            .withSelectionOrder(SelectionOrder.ORIGINAL)
            .withSelectedCountLimit(7L);
    var phase = queuedEntityPhase(values).withNearbySelectionAutoConfigurationEnabled(true);
    var outcome =
        solve(
            basicConfig(phase).withEasyScoreCalculatorClass(ZeroBasicCalculator.class),
            basicProblem(96, 1));

    assertThat(index(outcome.solution().getEntityList().getFirst().getValue())).isZero();
    assertThat(outcome.observations().selectedMoves).containsExactly(7L);
    assertThat(outcome.observations().meterCalls).isEqualTo(7);
    assertThat(values.getNearbySelectionConfig()).isNull();
    assertThat(values.getSelectionOrder()).isEqualTo(SelectionOrder.ORIGINAL);
    assertThat(values.getSelectedCountLimit()).isEqualTo(7L);
    assertThat(phase.getNearbySelectionAutoConfigurationEnabled()).isTrue();
    assertThat(phase.getNearbySelectionSize()).isNull();
  }

  @Test
  void explicitOriginalOrderIsPreservedWithinAutomaticNeighborhood() {
    var values = new ValueSelectorConfig("value").withSelectionOrder(SelectionOrder.ORIGINAL);
    var outcome =
        solve(
            basicConfig(queuedEntityPhase(values).withNearbySelectionAutoConfigurationEnabled(true))
                .withEasyScoreCalculatorClass(ZeroBasicCalculator.class),
            basicProblem(96, 1));

    assertThat(index(outcome.solution().getEntityList().getFirst().getValue())).isEqualTo(56);
    assertThat(outcome.observations().selectedMoves).containsExactly(40L);
    assertThat(outcome.observations().meterCalls).isEqualTo(96);
    assertThat(values.getSelectionOrder()).isEqualTo(SelectionOrder.ORIGINAL);
    assertThat(values.getNearbySelectionConfig()).isNull();
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void explicitCountLimitStopsWideningAtAdmittedRange() {
    var problem = hardProblem(170);
    for (var value : problem.values) {
      value.hard0 = -1;
      value.soft = -value.rank;
    }
    var values = new ValueSelectorConfig("value").withSelectedCountLimit(5L);
    var outcome =
        solve(
            hardConfig(queuedEntityPhase(values).withNearbySelectionAutoConfigurationEnabled(true)),
            problem);

    assertThat(outcome.observations().selectedMoves).containsExactly(5L);
    assertThat(outcome.observations().meterCalls).isEqualTo(5);
    assertThat(outcome.solution().entities.getFirst().value.rank).isZero();
    assertThat(values.getSelectedCountLimit()).isEqualTo(5L);
    assertHardScore(outcome.solution());
  }

  @Test
  void explicitEarlyPickOverridesBatchEvaluation() {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig()
                    .withPickEarlyType(ConstructionHeuristicPickEarlyType.FIRST_FEASIBLE_SCORE));
    var outcome = solve(basicConfig(phase), basicProblem(96, 1));

    assertThat(outcome.observations().selectedMoves).containsExactly(1L);
    assertThat(index(outcome.solution().getEntityList().getFirst().getValue())).isEqualTo(95);
    assertBasicScore(outcome.solution());
  }

  @Test
  void repeatedSolvesPreserveCallerConfiguration() {
    var values = new ValueSelectorConfig("value");
    var phase = queuedEntityPhase(values).withNearbySelectionAutoConfigurationEnabled(true);
    var config = basicConfig(phase);
    var factory = SolverFactory.<TestdataSolution>create(config);
    var first = solve(factory, basicProblem(96, 1));
    var second = solve(factory, basicProblem(96, 1));

    assertThat(first.observations().selectedMoves).containsExactly(40L);
    assertThat(second.observations().selectedMoves).containsExactly(40L);
    assertThat(first.observations().meterCalls).isEqualTo(96);
    assertThat(second.observations().meterCalls).isEqualTo(96);
    assertThat(values.getNearbySelectionConfig()).isNull();
    assertThat(values.getSelectionOrder()).isNull();
    assertThat(values.getSelectedCountLimit()).isNull();
    assertThat(config.getPhaseConfigList()).containsExactly(phase);
    assertBasicScore(first.solution());
    assertBasicScore(second.solution());
  }

  @Test
  void reusedSolverRebuildsNearbyCandidatesForEachProblem() {
    var config =
        basicConfig(
            new ConstructionHeuristicPhaseConfig()
                .withNearbySelectionAutoConfigurationEnabled(true));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var first = solve(solver, basicProblem(96, 1));
    var second = solve(solver, basicProblem(63, 1));

    assertThat(index(first.solution().getEntityList().getFirst().getValue())).isEqualTo(95);
    assertThat(index(second.solution().getEntityList().getFirst().getValue())).isEqualTo(62);
    assertThat(first.observations().selectedMoves).containsExactly(40L);
    assertThat(second.observations().selectedMoves).containsExactly(40L);
    assertThat(first.observations().meterCalls).isEqualTo(96);
    assertThat(second.observations().meterCalls).isEqualTo(63);
    assertBasicScore(first.solution());
    assertBasicScore(second.solution());
  }

  @Test
  void nestedUnionReceivesAutomaticProfile() {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withEntityPlacerConfig(
                new QueuedEntityPlacerConfig()
                    .withEntitySelectorConfig(new EntitySelectorConfig().withId("queuedEntity"))
                    .withMoveSelectorConfigs(
                        new UnionMoveSelectorConfig()
                            .withMoveSelectors(
                                new ChangeMoveSelectorConfig()
                                    .withEntitySelectorConfig(
                                        new EntitySelectorConfig()
                                            .withMimicSelectorRef("queuedEntity"))
                                    .withValueSelectorConfig(new ValueSelectorConfig("value")))));
    var outcome = solve(basicConfig(phase), basicProblem(96, 1));

    assertThat(outcome.observations().selectedMoves).containsExactly(40L);
    assertThat(outcome.observations().meterCalls).isEqualTo(96);
    assertBasicScore(outcome.solution());
  }

  @Test
  void typedEntitySwapProfileSeedsUninitializedValues() {
    var origin = new EntitySelectorConfig().withId("swapOrigin");
    var nearby =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("swapOrigin"))
            .withNearbyDistanceMeterClass(EntitySwapMeter.class);
    var swap =
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(origin)
            .withSecondaryEntitySelectorConfig(
                new EntitySelectorConfig().withNearbySelectionConfig(nearby));
    var config =
        withoutGlobalNearby(
                basicConfig(
                    new ConstructionHeuristicPhaseConfig()
                        .withNearbySelectionAutoConfigurationEnabled(true)))
            .withEasyScoreCalculatorClass(ZeroBasicCalculator.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true),
                upcomingLocalSearch(swap));
    var outcome = solve(config, basicProblem(96, 3));

    assertThat(outcome.solution().getEntityList()).allMatch(entity -> entity.getValue() != null);
    assertThat(outcome.observations().selectedMoves).containsExactly(40L, 40L, 40L);
    assertThat(outcome.observations().meterCalls).isEqualTo(3);
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(origin.getId()).isEqualTo("swapOrigin");
    assertThat(nearby.getOriginEntitySelectorConfig().getMimicSelectorRef())
        .isEqualTo("swapOrigin");
  }

  @Test
  void globalTypedEntitySwapMeterSeedsConstructionWithoutLocalSearch() {
    var config =
        basicConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true))
            .withNearbyDistanceMeterClass(EntitySwapMeter.class)
            .withEasyScoreCalculatorClass(ZeroBasicCalculator.class);
    var outcome = solve(config, basicProblem(96, 3));

    assertThat(outcome.solution().getEntityList()).allMatch(entity -> entity.getValue() != null);
    assertThat(outcome.observations().selectedMoves).containsExactly(40L, 40L, 40L);
    assertThat(outcome.observations().meterCalls).isEqualTo(3);
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void pooledEntityPlacerKeepsEntityValueDistanceDirection() {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withConstructionHeuristicType(ConstructionHeuristicType.ALLOCATE_FROM_POOL)
            .withNearbySelectionSize(1);
    var outcome = solve(basicConfig(phase), basicProblem(96, 3));

    assertThat(outcome.solution().getEntityList())
        .extracting(entity -> index(entity.getValue()))
        .containsExactly(95, 94, 93);
    assertThat(outcome.observations().selectedMoves).containsExactly(3L, 2L, 1L);
    assertBasicScore(outcome.solution());
  }

  @Test
  void scalarQueuedValuePlacerRanksEntitiesUsingEntityToValueMeter() {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withConstructionHeuristicType(ConstructionHeuristicType.ALLOCATE_TO_VALUE_FROM_QUEUE)
            .withNearbySelectionSize(1);
    var outcome = solve(basicConfig(phase), basicProblem(3, 3));

    assertThat(outcome.solution().getEntityList())
        .extracting(entity -> index(entity.getValue()))
        .containsExactly(2, 1, 0);
    assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L);
    assertBasicScore(outcome.solution());
  }

  @Test
  void globalListDestinationProfileSeedsEmptyRoutesAndMaintainsShadows() {
    var config =
        listConfig(
            new ConstructionHeuristicPhaseConfig()
                .withNearbySelectionAutoConfigurationEnabled(true)
                .withNearbySelectionSize(1));
    var outcome = solve(config, TestdataListSolution.generateUninitializedSolution(6, 3));

    assertListIntegrity(outcome.solution());
    assertThat(outcome.solution().getEntityList())
        .extracting(entity -> entity.getValueList().size())
        .containsExactly(2, 2, 2);
    assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L, 1L, 1L, 1L);
    assertThat(outcome.observations().meterCalls).isEqualTo(33);
    assertThat(outcome.solution().getScore()).isEqualTo(listScore(outcome.solution()));
  }

  @Test
  void manualUpcomingListDestinationProfileIsUsedDuringConstruction() {
    var origin = new ValueSelectorConfig("valueList").withId("listOrigin");
    var nearby =
        new NearbySelectionConfig()
            .withOriginValueSelectorConfig(
                new ValueSelectorConfig().withMimicSelectorRef("listOrigin"))
            .withNearbyDistanceMeterClass(ListDestinationMeter.class);
    var move =
        new ListChangeMoveSelectorConfig()
            .withValueSelectorConfig(origin)
            .withDestinationSelectorConfig(
                new DestinationSelectorConfig().withNearbySelectionConfig(nearby));
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withNearbySelectionSize(1);
    var config =
        withoutGlobalNearby(listConfig(phase)).withPhases(phase, upcomingLocalSearch(move));
    var outcome = solve(config, TestdataListSolution.generateUninitializedSolution(6, 3));

    assertListIntegrity(outcome.solution());
    assertThat(outcome.observations().meterCalls).isEqualTo(33);
    assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L, 1L, 1L, 1L);
    assertThat(outcome.solution().getScore()).isEqualTo(listScore(outcome.solution()));
    assertThat(move.getDestinationSelectorConfig().getNearbySelectionConfig()).isSameAs(nearby);
    assertThat(nearby.getOriginValueSelectorConfig().getMimicSelectorRef()).isEqualTo("listOrigin");
  }

  @Test
  void typedValueSwapOnlyProfileUsesAssignedAnchorsAndEmptyRouteSeeds() {
    var origin = new ValueSelectorConfig("valueList").withId("valueSwapOrigin");
    var swap =
        new ListSwapMoveSelectorConfig()
            .withValueSelectorConfig(origin)
            .withSecondaryValueSelectorConfig(
                new ValueSelectorConfig("valueList")
                    .withNearbySelectionConfig(
                        new NearbySelectionConfig()
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("valueSwapOrigin"))
                            .withNearbyDistanceMeterClass(ListValueSwapMeter.class)));
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withNearbySelectionSize(1);
    var config =
        withoutGlobalNearby(listConfig(phase))
            .withEasyScoreCalculatorClass(ZeroListCalculator.class)
            .withPhases(phase, upcomingLocalSearch(swap));
    var outcome = solve(config, TestdataListSolution.generateUninitializedSolution(6, 2));

    assertListIntegrity(outcome.solution());
    assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L, 1L, 1L, 1L);
    assertThat(outcome.observations().meterCalls).isEqualTo(15);
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void globalTypedValueSwapMeterSeedsEmptyListRoutesWithoutLocalSearch() {
    var config =
        listConfig(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)
                    .withNearbySelectionSize(1))
            .withNearbyDistanceMeterClass(ListValueSwapMeter.class)
            .withEasyScoreCalculatorClass(ZeroListCalculator.class);
    var outcome = solve(config, TestdataListSolution.generateUninitializedSolution(6, 2));

    assertListIntegrity(outcome.solution());
    assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L, 1L, 1L, 1L);
    assertThat(outcome.observations().meterCalls).isEqualTo(15);
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void upcomingIslandLocalSearchProvidesConstructionProfile() {
    var nearby =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("islandOrigin"))
            .withNearbyDistanceMeterClass(BasicMeter.class);
    var change =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("islandOrigin"))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value").withNearbySelectionConfig(nearby));
    var island =
        new IslandModelPhaseConfig()
            .withIslandCount(1)
            .withPhaseConfigList(List.of(upcomingLocalSearch(change)))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(0));
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withNearbySelectionSize(1);
    var config = withoutGlobalNearby(basicConfig(phase)).withPhases(phase, island);
    var outcome = solve(config, basicProblem(96, 1));

    assertThat(outcome.observations().selectedMoves).containsExactly(1L);
    assertThat(outcome.observations().meterCalls).isEqualTo(96);
    assertThat(index(outcome.solution().getEntityList().getFirst().getValue())).isEqualTo(95);
    assertBasicScore(outcome.solution());
  }

  @Test
  void listEntityRangesExcludeIneligibleNearbyRoutes() {
    var problem = TestdataListEntityProvidingSolution.generateSolution(6, 3, false);
    var config =
        commonConfig(
                TestdataListEntityProvidingSolution.class,
                TestdataListEntityProvidingEntity.class,
                TestdataListEntityProvidingValue.class)
            .withEasyScoreCalculatorClass(RangedListCalculator.class)
            .withNearbyDistanceMeterClass(RangedListMeter.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)
                    .withNearbySelectionSize(1));
    var outcome = solve(config, problem);

    assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L, 1L, 1L, 1L);
    assertThat(outcome.solution().getValueList()).allMatch(value -> value.getEntity() != null);
    for (var entity : outcome.solution().getEntityList()) {
      assertThat(entity.getValueList()).containsExactlyInAnyOrderElementsOf(entity.getValueRange());
      for (var value : entity.getValueList()) {
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(entity.getValueList().indexOf(value));
      }
    }
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void pinnedListRoutesAreExcludedBeforeNearbyRanking() {
    var problem = TestdataPinnedListSolution.generateUninitializedSolution(6, 3);
    var pinned = problem.getEntityList().getFirst();
    pinned.setValueList(new ArrayList<>(List.of(problem.getValueList().getFirst())));
    pinned.setPinned(true);
    var config =
        commonConfig(
                TestdataPinnedListSolution.class,
                TestdataPinnedListEntity.class,
                TestdataPinnedListValue.class)
            .withEasyScoreCalculatorClass(PinnedListCalculator.class)
            .withNearbyDistanceMeterClass(PinnedListMeter.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)
                    .withNearbySelectionSize(1));
    var outcome = solve(config, problem);

    assertThat(outcome.solution().getEntityList().getFirst().getValueList())
        .extracting(TestdataPinnedListValue::getCode)
        .containsExactly("Generated Value 0");
    assertThat(outcome.solution().getValueList()).allMatch(value -> value.getEntity() != null);
    assertThat(outcome.observations().selectedMoves).containsExactly(1L, 1L, 1L, 1L, 1L);
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void mixedVariableProfilesApplyOnlyToCompatibleValueTypes() {
    var problem = TestdataMultiVarSolution.generateUninitializedSolution(1, 96);
    var config =
        commonConfig(TestdataMultiVarSolution.class, TestdataMultiVarEntity.class)
            .withEasyScoreCalculatorClass(MultiVariableCalculator.class)
            .withNearbyDistanceMeterClass(MultiVariableMeter.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withNearbySelectionAutoConfigurationEnabled(true)
                    .withNearbySelectionSize(1));
    var outcome = solve(config, problem);

    var entity = outcome.solution().getMultiVarEntityList().getFirst();
    assertThat(index(entity.getPrimaryValue())).isEqualTo(95);
    assertThat(index(entity.getSecondaryValue())).isEqualTo(95);
    assertThat(entity.getTertiaryValueAllowedUnassigned()).isNotNull();
    assertThat(outcome.observations().meterCalls).isEqualTo(192);
    assertThat(outcome.observations().selectedMoves).containsExactly(1L);
    assertThat(outcome.solution().getScore()).isEqualTo(multiVariableScore(outcome.solution()));
  }

  @Test
  void manualVariableProfileDoesNotLeakToAnotherVariableOfTheSameType() {
    var origin = new EntitySelectorConfig().withId("multiOrigin");
    var nearby =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("multiOrigin"))
            .withNearbyDistanceMeterClass(MultiVariableMeter.class);
    var change =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(origin)
            .withValueSelectorConfig(
                new ValueSelectorConfig("primaryValue").withNearbySelectionConfig(nearby));
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withNearbySelectionSize(1);
    var config =
        commonConfig(TestdataMultiVarSolution.class, TestdataMultiVarEntity.class)
            .withEasyScoreCalculatorClass(MultiVariableCalculator.class)
            .withPhases(phase, upcomingLocalSearch(change));
    var outcome = solve(config, TestdataMultiVarSolution.generateUninitializedSolution(1, 96));

    var entity = outcome.solution().getMultiVarEntityList().getFirst();
    assertThat(index(entity.getPrimaryValue())).isEqualTo(95);
    assertThat(index(entity.getSecondaryValue())).isZero();
    assertThat(entity.getTertiaryValueAllowedUnassigned()).isNotNull();
    assertThat(outcome.observations().meterCalls).isEqualTo(96);
    assertThat(outcome.observations().selectedMoves).containsExactly(1L);
    assertThat(outcome.solution().getScore()).isEqualTo(multiVariableScore(outcome.solution()));
  }

  private static ConstructionHeuristicPhaseConfig queuedEntityPhase(ValueSelectorConfig values) {
    return new ConstructionHeuristicPhaseConfig()
        .withEntityPlacerConfig(
            new QueuedEntityPlacerConfig()
                .withEntitySelectorConfig(new EntitySelectorConfig().withId("queuedEntity"))
                .withMoveSelectorConfigs(
                    new ChangeMoveSelectorConfig()
                        .withEntitySelectorConfig(
                            new EntitySelectorConfig().withMimicSelectorRef("queuedEntity"))
                        .withValueSelectorConfig(values)));
  }

  private static LocalSearchPhaseConfig upcomingLocalSearch(MoveSelectorConfig<?> move) {
    return new LocalSearchPhaseConfig()
        .withMoveSelectorConfig(move)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(0));
  }

  private static SolverConfig commonConfig(Class<?> solutionClass, Class<?>... entityClasses) {
    return new SolverConfig()
        .withSolutionClass(solutionClass)
        .withEntityClasses(entityClasses)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withRandomSeed(0L);
  }

  private static SolverConfig withoutGlobalNearby(SolverConfig config) {
    config.setNearbyDistanceMeterClass(null);
    return config;
  }

  private static PairSolution pairProblem() {
    var solution = new PairSolution();
    solution.values = List.of(new TestdataValue("Value 0"), new TestdataValue("Value 1"));
    solution.entities = List.of(new PairEntity());
    return solution;
  }

  private static SolverConfig pairConfig() {
    return commonConfig(PairSolution.class, PairEntity.class)
        .withEasyScoreCalculatorClass(PairCalculator.class)
        .withNearbyDistanceMeterClass(PairMeter.class)
        .withPhases(
            new ConstructionHeuristicPhaseConfig()
                .withNearbySelectionAutoConfigurationEnabled(true)
                .withNearbySelectionSize(1));
  }

  private static HardSoftScore pairScore(PairSolution solution) {
    var entity = solution.entities.getFirst();
    var penalty = (entity.optionalA == null ? 0 : 1) + (entity.optionalB == null ? 0 : 1);
    return HardSoftScore.ofHard(-penalty);
  }

  private static TripleSolution tripleProblem(int entityCount) {
    var solution = new TripleSolution();
    solution.values = IntStream.range(0, 3).mapToObj(i -> new TestdataValue("Value " + i)).toList();
    solution.entities =
        IntStream.range(0, entityCount).mapToObj(i -> new TripleEntity("Entity " + i)).toList();
    return solution;
  }

  private static SolverConfig tripleConfig(ConstructionHeuristicPhaseConfig phase) {
    return commonConfig(TripleSolution.class, TripleEntity.class)
        .withEasyScoreCalculatorClass(TripleCalculator.class)
        .withNearbyDistanceMeterClass(TripleMeter.class)
        .withPhases(phase);
  }

  private static int assignedCount(TripleEntity entity) {
    return (entity.primary == null ? 0 : 1)
        + (entity.secondary == null ? 0 : 1)
        + (entity.tertiary == null ? 0 : 1);
  }

  private static void assertTripleInitialized(TripleSolution solution) {
    assertThat(solution.entities).allMatch(entity -> assignedCount(entity) == 3);
    assertThat(solution.score).isEqualTo(SimpleScore.ZERO);
  }

  private static SolverConfig basicConfig(ConstructionHeuristicPhaseConfig phase) {
    return commonConfig(TestdataSolution.class, TestdataEntity.class)
        .withEasyScoreCalculatorClass(BasicCalculator.class)
        .withNearbyDistanceMeterClass(BasicMeter.class)
        .withPhases(phase);
  }

  private static SolverConfig hardConfig(ConstructionHeuristicPhaseConfig phase) {
    return commonConfig(HardSolution.class, HardEntity.class)
        .withEasyScoreCalculatorClass(HardCalculator.class)
        .withNearbyDistanceMeterClass(HardMeter.class)
        .withPhases(phase);
  }

  private static SolverConfig listConfig(ConstructionHeuristicPhaseConfig phase) {
    return commonConfig(
            TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
        .withEasyScoreCalculatorClass(ListCalculator.class)
        .withNearbyDistanceMeterClass(ListDestinationMeter.class)
        .withPhases(phase);
  }

  private static TestdataSolution basicProblem(int valueCount, int entityCount) {
    return TestdataSolution.generateUninitializedSolution(valueCount, entityCount);
  }

  private static HardSolution hardProblem(int valueCount) {
    var problem = new HardSolution();
    problem.values = IntStream.range(0, valueCount).mapToObj(RankedValue::new).toList();
    problem.entities = List.of(new HardEntity());
    return problem;
  }

  private static <Solution_> Outcome<Solution_> solve(SolverConfig config, Solution_ problem) {
    return solve(SolverFactory.<Solution_>create(config), problem);
  }

  private static <Solution_> Outcome<Solution_> solve(
      SolverFactory<Solution_> factory, Solution_ problem) {
    return solve((DefaultSolver<Solution_>) factory.buildSolver(), problem);
  }

  private static <Solution_> Outcome<Solution_> solve(
      DefaultSolver<Solution_> solver, Solution_ problem) {
    var observations = new Observations();
    if (problem instanceof TestdataSolution basicProblem) {
      observations.basicValueCount = basicProblem.getValueList().size();
    }
    OBSERVATIONS.set(observations);
    var listener =
        new PhaseLifecycleListenerAdapter<Solution_>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> stepScope) {
            if (stepScope instanceof ConstructionHeuristicStepScope<Solution_> chStepScope) {
              observations.selectedMoves.add(chStepScope.getSelectedMoveCount());
              if (stepScope.getScoreDirector().getWorkingSolution()
                  instanceof TripleSolution triple) {
                observations.tripleAssignedCounts.add(
                    triple.entities.stream()
                        .map(AutomaticNearbyConstructionHeuristicTest::assignedCount)
                        .toList());
              }
            }
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
            if (phaseScope instanceof ConstructionHeuristicPhaseScope<?>) {
              observations.repairMeterCallsAfterConstruction = REPAIR_METER_CALLS.get();
            }
            if (phaseScope.getPhaseIndex() == 0) {
              observations.moveEvaluationCount =
                  phaseScope.getSolverScope().getMoveEvaluationCount();
            }
          }
        };
    solver.addPhaseLifecycleListener(listener);
    try {
      return new Outcome<>(solver.solve(problem), observations);
    } finally {
      solver.removePhaseLifecycleListener(listener);
      OBSERVATIONS.remove();
    }
  }

  private static int index(TestdataObject object) {
    var code = object.getCode();
    return Integer.parseInt(code.substring(code.lastIndexOf(' ') + 1));
  }

  private static void countMeterCall() {
    OBSERVATIONS.get().meterCalls++;
  }

  private static void recordRank(RankedValue value) {
    var observations = OBSERVATIONS.get();
    if (observations != null && value != null) {
      observations.scoredRanks.add(value.rank);
    }
  }

  private static SimpleScore basicScore(TestdataSolution solution) {
    var score = 0;
    for (var entity : solution.getEntityList()) {
      if (entity.getValue() != null) {
        score -=
            Math.abs(solution.getValueList().size() - 1 - index(entity) - index(entity.getValue()));
      }
    }
    return SimpleScore.of(score);
  }

  private static void assertBasicScore(TestdataSolution solution) {
    assertThat(solution.getEntityList()).allMatch(entity -> entity.getValue() != null);
    assertThat(solution.getScore()).isEqualTo(basicScore(solution));
  }

  private static BendableScore rankedScore(RankedValue value) {
    if (value == null) {
      return BendableScore.zero(2, 1);
    }
    return new BendableScore(
        value.structural, new long[] {value.hard0, value.hard1}, new long[] {value.soft});
  }

  private static BendableScore hardScore(HardSolution solution) {
    return rankedScore(solution.entities.getFirst().value);
  }

  private static BendableScore optionalHardScore(OptionalHardSolution solution) {
    return rankedScore(solution.entities.getFirst().value);
  }

  private static void assertHardScore(HardSolution solution) {
    assertThat(solution.entities.getFirst().value).isNotNull();
    assertThat(solution.score).isEqualTo(hardScore(solution));
  }

  private static SimpleScore listScore(TestdataListSolution solution) {
    var score = 0;
    for (var entity : solution.getEntityList()) {
      for (var value : entity.getValueList()) {
        score -= Math.abs(index(entity) - index(value) % solution.getEntityList().size());
      }
    }
    return SimpleScore.of(score);
  }

  private static void assertListIntegrity(TestdataListSolution solution) {
    var assigned =
        solution.getEntityList().stream()
            .flatMap(entity -> entity.getValueList().stream())
            .toList();
    assertThat(assigned).containsExactlyInAnyOrderElementsOf(solution.getValueList());
    for (var entity : solution.getEntityList()) {
      for (var i = 0; i < entity.getValueList().size(); i++) {
        var value = entity.getValueList().get(i);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(i);
      }
    }
  }

  private static SimpleScore multiVariableScore(TestdataMultiVarSolution solution) {
    var score = 0;
    for (var entity : solution.getMultiVarEntityList()) {
      if (entity.getPrimaryValue() != null) {
        score -= solution.getValueList().size() - 1 - index(entity.getPrimaryValue());
      }
      if (entity.getSecondaryValue() != null) {
        score -= solution.getValueList().size() - 1 - index(entity.getSecondaryValue());
      }
      if (entity.getTertiaryValueAllowedUnassigned() == null) {
        score -= 1;
      }
    }
    return SimpleScore.of(score);
  }

  private record Outcome<Solution_>(Solution_ solution, Observations observations) {}

  private static final class Observations {
    private int basicValueCount;
    private long meterCalls;
    private long moveEvaluationCount;
    private long repairMeterCallsAfterConstruction;
    private final List<Long> selectedMoves = new ArrayList<>();
    private final Set<Integer> scoredRanks = new HashSet<>();
    private final List<List<Integer>> tripleAssignedCounts = new ArrayList<>();
  }

  public static final class StopAfterForager
      extends DefaultConstructionHeuristicForager<TestdataSolution> {
    private int stopAfter;

    public StopAfterForager() {
      super(ConstructionHeuristicPickEarlyType.NEVER);
    }

    public void setStopAfter(String stopAfter) {
      this.stopAfter = Integer.parseInt(stopAfter);
    }

    @Override
    public boolean isQuitEarly() {
      return selectedMoveCount >= stopAfter;
    }

    @Override
    public void addMove(ConstructionHeuristicMoveScope<TestdataSolution> moveScope) {
      super.addMove(moveScope);
      maxScoreMoveScope = moveScope;
    }
  }

  public static final class PairMeter implements NearbyDistanceMeter<PairEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(PairEntity origin, TestdataValue destination) {
      return index(destination);
    }
  }

  public static final class TripleMeter
      implements NearbyDistanceMeter<TripleEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TripleEntity origin, TestdataValue destination) {
      countMeterCall();
      return Math.abs(2 - index(origin) - index(destination));
    }
  }

  public static final class NestedBasicMeter
      implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataEntity origin, TestdataValue destination) {
      NESTED_METER_CALLS.incrementAndGet();
      return Math.abs(95 - index(origin) - index(destination));
    }
  }

  public static final class OnePartition implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> director, Integer runnablePartThreadLimit) {
      return List.of(((InnerScoreDirector<TestdataSolution, ?>) director).cloneWorkingSolution());
    }
  }

  public static final class RepairBasicMeter
      implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataEntity origin, TestdataValue destination) {
      assertThat(origin.getValue()).isNull();
      REPAIR_METER_CALLS.incrementAndGet();
      return Math.abs(2 - index(origin) - index(destination));
    }
  }

  public static final class RepairListMeter
      implements NearbyDistanceMeter<TestdataListValue, Object> {
    @Override
    public double getNearbyDistance(TestdataListValue origin, Object destination) {
      assertThat(origin.getEntity()).isNull();
      REPAIR_METER_CALLS.incrementAndGet();
      return destination instanceof TestdataListEntity entity
          ? Math.abs(index(entity) - index(origin) % 3)
          : 10 + Math.abs(index(origin) - index((TestdataListValue) destination));
    }
  }

  public static final class PairCalculator
      implements EasyScoreCalculator<PairSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(PairSolution solution) {
      return pairScore(solution);
    }
  }

  public static final class TripleCalculator
      implements EasyScoreCalculator<TripleSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TripleSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  @PlanningEntity
  public static final class PairEntity {
    @PlanningVariable(valueRangeProviderRefs = "values")
    public TestdataValue required;

    @PlanningVariable(valueRangeProviderRefs = "values", allowsUnassigned = true)
    public TestdataValue optionalA;

    @PlanningVariable(valueRangeProviderRefs = "values", allowsUnassigned = true)
    public TestdataValue optionalB;
  }

  @PlanningSolution
  public static final class PairSolution {
    @ValueRangeProvider(id = "values")
    @ProblemFactCollectionProperty
    public List<TestdataValue> values;

    @PlanningEntityCollectionProperty public List<PairEntity> entities;
    @PlanningScore public HardSoftScore score;
  }

  @PlanningEntity
  public static final class TripleEntity extends TestdataObject {
    @PlanningVariable(valueRangeProviderRefs = "values")
    public TestdataValue primary;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public TestdataValue secondary;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public TestdataValue tertiary;

    public TripleEntity() {}

    public TripleEntity(String code) {
      super(code);
    }
  }

  @PlanningSolution
  public static final class TripleSolution {
    @ValueRangeProvider(id = "values")
    @ProblemFactCollectionProperty
    public List<TestdataValue> values;

    @PlanningEntityCollectionProperty public List<TripleEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  public static final class BasicMeter
      implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataEntity origin, TestdataValue destination) {
      countMeterCall();
      // The furthest source-order value is the nearest for the first entity.
      return Math.abs(OBSERVATIONS.get().basicValueCount - 1 - index(origin) - index(destination));
    }
  }

  public static final class OriginalBasicMeter
      implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataEntity origin, TestdataValue destination) {
      countMeterCall();
      return index(destination);
    }
  }

  public static final class EntitySwapMeter
      implements NearbyDistanceMeter<TestdataEntity, TestdataEntity> {
    @Override
    public double getNearbyDistance(TestdataEntity origin, TestdataEntity destination) {
      countMeterCall();
      assertThat(destination.getValue()).isNotNull();
      return Math.abs(index(origin) - index(destination));
    }
  }

  public static final class HardMeter implements NearbyDistanceMeter<HardEntity, RankedValue> {
    @Override
    public double getNearbyDistance(HardEntity origin, RankedValue destination) {
      countMeterCall();
      return destination.rank;
    }
  }

  public static final class OptionalHardMeter
      implements NearbyDistanceMeter<OptionalHardEntity, RankedValue> {
    @Override
    public double getNearbyDistance(OptionalHardEntity origin, RankedValue destination) {
      countMeterCall();
      assertThat(destination).isNotNull();
      return destination.rank;
    }
  }

  public static final class ListDestinationMeter
      implements NearbyDistanceMeter<TestdataListValue, Object> {
    @Override
    public double getNearbyDistance(TestdataListValue origin, Object destination) {
      countMeterCall();
      if (destination instanceof TestdataListEntity entity) {
        return Math.abs(index(entity) - index(origin) % 3);
      }
      var value = (TestdataListValue) destination;
      return 10 + Math.abs(index(origin) - index(value));
    }
  }

  public static final class ListValueSwapMeter
      implements NearbyDistanceMeter<TestdataListValue, TestdataListValue> {
    @Override
    public double getNearbyDistance(TestdataListValue origin, TestdataListValue destination) {
      countMeterCall();
      assertThat(destination.getEntity()).isNotNull();
      return Math.abs(index(origin) - index(destination));
    }
  }

  public static final class RangedListMeter
      implements NearbyDistanceMeter<TestdataListEntityProvidingValue, Object> {
    @Override
    public double getNearbyDistance(TestdataListEntityProvidingValue origin, Object destination) {
      countMeterCall();
      var entity =
          destination instanceof TestdataListEntityProvidingEntity owner
              ? owner
              : ((TestdataListEntityProvidingValue) destination).getEntity();
      assertThat(entity.getValueRange()).contains(origin);
      // Deliberately prefer source-order routes that may have an incompatible entity range.
      return index(entity);
    }
  }

  public static final class PinnedListMeter
      implements NearbyDistanceMeter<TestdataPinnedListValue, Object> {
    @Override
    public double getNearbyDistance(TestdataPinnedListValue origin, Object destination) {
      countMeterCall();
      var entity =
          destination instanceof TestdataPinnedListEntity owner
              ? owner
              : ((TestdataPinnedListValue) destination).getEntity();
      assertThat(entity.isPinned()).isFalse();
      return index(entity);
    }
  }

  public static final class MultiVariableMeter
      implements NearbyDistanceMeter<TestdataMultiVarEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataMultiVarEntity origin, TestdataValue destination) {
      countMeterCall();
      return 95 - index(destination);
    }
  }

  public static final class BasicCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return basicScore(solution);
    }
  }

  public static final class ZeroBasicCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class HardCalculator
      implements EasyScoreCalculator<HardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(HardSolution solution) {
      recordRank(solution.entities.getFirst().value);
      return hardScore(solution);
    }
  }

  public static final class OptionalHardCalculator
      implements EasyScoreCalculator<OptionalHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(OptionalHardSolution solution) {
      recordRank(solution.entities.getFirst().value);
      return optionalHardScore(solution);
    }
  }

  public static final class ListCalculator
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      return listScore(solution);
    }
  }

  public static final class ZeroListCalculator
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class RangedListCalculator
      implements EasyScoreCalculator<TestdataListEntityProvidingSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListEntityProvidingSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class PinnedListCalculator
      implements EasyScoreCalculator<TestdataPinnedListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataPinnedListSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class MultiVariableCalculator
      implements EasyScoreCalculator<TestdataMultiVarSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataMultiVarSolution solution) {
      return multiVariableScore(solution);
    }
  }

  public static final class RankedValue {
    @PlanningId public final int rank;
    private long structural;
    private long hard0;
    private long hard1;
    private long soft;

    public RankedValue(int rank) {
      this.rank = rank;
    }
  }

  @PlanningEntity
  public static final class HardEntity {
    @PlanningId public String id = "hardEntity";

    @PlanningVariable(valueRangeProviderRefs = "values")
    public RankedValue value;

    public HardEntity() {}
  }

  @PlanningSolution
  public static final class HardSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    public List<RankedValue> values;

    @PlanningEntityCollectionProperty public List<HardEntity> entities;

    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 1)
    public BendableScore score;

    public HardSolution() {}
  }

  @PlanningEntity
  public static final class OptionalHardEntity {
    @PlanningVariable(valueRangeProviderRefs = "values", allowsUnassigned = true)
    public RankedValue value;

    public OptionalHardEntity() {}
  }

  @PlanningSolution
  public static final class OptionalHardSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    public List<RankedValue> values;

    @PlanningEntityCollectionProperty public List<OptionalHardEntity> entities;

    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 1)
    public BendableScore score;

    public OptionalHardSolution() {}
  }
}
