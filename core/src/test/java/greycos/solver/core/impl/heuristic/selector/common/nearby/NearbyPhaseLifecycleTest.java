package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.selector.Selector;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.entity.nearby.NearEntityNearbyEntitySelector;
import greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.value.nearby.NearEntityNearbyValueSelector;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.noshadows.TestdataPinnedNoShadowsListEntity;
import greycos.solver.core.testcotwin.list.pinned.noshadows.TestdataPinnedNoShadowsListSolution;
import greycos.solver.core.testcotwin.list.pinned.noshadows.TestdataPinnedNoShadowsListValue;
import greycos.solver.core.testutil.PlannerTestUtils;
import greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings({"unchecked", "rawtypes"})
class NearbyPhaseLifecycleTest {

  enum SelectorKind {
    ENTITY,
    VALUE,
    DESTINATION,
    SUB_LIST
  }

  @ParameterizedTest
  @EnumSource(SelectorKind.class)
  void demandBelongsToItsPhaseDirector(SelectorKind kind) {
    var fixture = fixture(kind);
    var selector = fixture.selector();
    InnerScoreDirector<TestdataListSolution, ?> initialDirector = mock(InnerScoreDirector.class);
    var initialManager =
        NearbyTestUtils.mockSupplyManager(initialDirector, mock(ListVariableState.class));
    var scope = SelectorTestUtils.solvingStarted(selector, initialDirector, new TestRandom(0));

    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      InnerScoreDirector<TestdataListSolution, ?> phaseDirector = mock(InnerScoreDirector.class);
      var phaseManager =
          NearbyTestUtils.mockSupplyManager(phaseDirector, mock(ListVariableState.class));
      scope.setScoreDirector(phaseDirector);
      var phaseScope = PlannerTestUtils.delegatingPhaseScope(scope);
      selector.phaseStarted(phaseScope);
      // Repeated initialization within a phase must retain its matrix.
      selector.phaseStarted(phaseScope);
      verify(phaseManager).demand(any());
      if (kind == SelectorKind.DESTINATION || kind == SelectorKind.SUB_LIST) {
        verify(phaseDirector, times(2)).getListVariableState(any());
      }

      // Cleanup must use the manager that accepted the demand, even if the scope has moved on.
      scope.setScoreDirector(initialDirector);
      selector.phaseEnded(phaseScope);
      selector.phaseEnded(phaseScope);
      verify(phaseManager).cancel(any());
    }
    selector.solvingEnded(scope);
    verify(initialManager, never()).demand(any());
    verify(initialManager, never()).cancel(any());
    verify(initialDirector, never()).getListVariableState(any());

    // A second solve must not retain the previous phase's resources.
    selector.solvingStarted(scope);
    var phaseScope = PlannerTestUtils.delegatingPhaseScope(scope);
    selector.phaseStarted(phaseScope);
    selector.phaseEnded(phaseScope);
    selector.solvingEnded(scope);
    verify(initialManager).demand(any());
    verify(initialManager).cancel(any());
  }

  @ParameterizedTest
  @EnumSource(SelectorKind.class)
  void childPhaseEndFailureStillReleasesDemand(SelectorKind kind) {
    var fixture = fixture(kind);
    InnerScoreDirector<TestdataListSolution, ?> director = mock(InnerScoreDirector.class);
    var manager = NearbyTestUtils.mockSupplyManager(director, mock(ListVariableState.class));
    var scope = SelectorTestUtils.solvingStarted(fixture.selector(), director, new TestRandom(0));
    var phaseScope = PlannerTestUtils.delegatingPhaseScope(scope);
    fixture.selector().phaseStarted(phaseScope);
    var failure = new IllegalStateException("child phase end failed");
    doThrow(failure).when(fixture.child()).phaseEnded(phaseScope);

    assertThatThrownBy(() -> fixture.selector().phaseEnded(phaseScope)).isSameAs(failure);
    verify(manager).cancel(any());
    assertThatCode(() -> fixture.selector().solvingEnded(scope)).doesNotThrowAnyException();
    verify(manager).cancel(any());
  }

  @ParameterizedTest
  @EnumSource(SelectorKind.class)
  void cancellationFailureClearsOwnedState(SelectorKind kind) {
    var selector = fixture(kind).selector();
    InnerScoreDirector<TestdataListSolution, ?> director = mock(InnerScoreDirector.class);
    var manager = NearbyTestUtils.mockSupplyManager(director, mock(ListVariableState.class));
    var scope = SelectorTestUtils.solvingStarted(selector, director, new TestRandom(0));
    var phaseScope = PlannerTestUtils.delegatingPhaseScope(scope);
    selector.phaseStarted(phaseScope);
    when(manager.cancel(any())).thenReturn(false);

    assertThatThrownBy(() -> selector.phaseEnded(phaseScope))
        .hasMessageContaining("nearby distance matrix demand is not active");
    assertThatCode(() -> selector.solvingEnded(scope)).doesNotThrowAnyException();
    verify(manager).cancel(any());
    when(manager.cancel(any())).thenReturn(true);
    selector.solvingStarted(scope);
    selector.phaseStarted(phaseScope);
    selector.phaseEnded(phaseScope);
    selector.solvingEnded(scope);
    verify(manager, times(2)).demand(any());
    verify(manager, times(2)).cancel(any());
  }

  @ParameterizedTest
  @MethodSource("phaseModes")
  void nearbySolvesWithPhaseModesAndCanBeReused(boolean subLists, EnvironmentMode[] modes) {
    var phases =
        Stream.of(modes)
            .map(mode -> localSearch(mode, subLists))
            .toArray(LocalSearchPhaseConfig[]::new);
    var config =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withSolutionClass(TestdataPinnedNoShadowsListSolution.class)
            .withEntityClasses(
                TestdataPinnedNoShadowsListEntity.class, TestdataPinnedNoShadowsListValue.class)
            .withEasyScoreCalculatorClass(ListSizeScoreCalculator.class)
            .withPhases(phases);
    if (!subLists) {
      config.withNearbyDistanceMeterClass(CodeDistanceMeter.class);
    }
    var solver = SolverFactory.<TestdataPinnedNoShadowsListSolution>create(config).buildSolver();
    for (int solveIndex = 0; solveIndex < 2; solveIndex++) {
      var problem = TestdataPinnedNoShadowsListSolution.generateInitializedSolution(6, 2);
      for (int i = 0; i < problem.getValueList().size(); i++) {
        problem.getValueList().get(i).setCode(Integer.toString(i));
      }
      for (int i = 0; i < problem.getEntityList().size(); i++) {
        problem.getEntityList().get(i).setCode(Integer.toString(100 + i));
      }

      var result = solver.solve(problem);
      assertThat(result.getScore()).isEqualTo(new ListSizeScoreCalculator().calculateScore(result));
      assertThat(result.getScore().score()).isGreaterThanOrEqualTo(18);
      assertThat(
              result.getEntityList().stream()
                  .flatMap(entity -> entity.getValueList().stream())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(result.getValueList());
      if (!subLists) {
        assertThat(result.getScore()).isEqualTo(SimpleScore.of(36));
      }
    }
  }

  private static Stream<Arguments> phaseModes() {
    return Stream.of(false, true)
        .flatMap(
            subLists ->
                Stream.of(
                    Arguments.of(subLists, new EnvironmentMode[] {EnvironmentMode.STEP_ASSERT}),
                    Arguments.of(
                        subLists,
                        new EnvironmentMode[] {
                          EnvironmentMode.NO_ASSERT, EnvironmentMode.STEP_ASSERT
                        }),
                    Arguments.of(
                        subLists,
                        new EnvironmentMode[] {
                          EnvironmentMode.STEP_ASSERT, EnvironmentMode.NO_ASSERT
                        })));
  }

  private static LocalSearchPhaseConfig localSearch(EnvironmentMode mode, boolean subLists) {
    var phase =
        new LocalSearchPhaseConfig()
            .withEnvironmentMode(mode)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(50));
    if (subLists) {
      phase.withMoveSelectorConfig(
          new SubListSwapMoveSelectorConfig()
              .withSubListSelectorConfig(
                  new SubListSelectorConfig()
                      .withId("origin")
                      .withMinimumSubListSize(1)
                      .withMaximumSubListSize(3))
              .withSecondarySubListSelectorConfig(
                  new SubListSelectorConfig()
                      .withMinimumSubListSize(1)
                      .withMaximumSubListSize(3)
                      .withNearbySelectionConfig(
                          new NearbySelectionConfig()
                              .withOriginSubListSelectorConfig(
                                  new SubListSelectorConfig().withMimicSelectorRef("origin"))
                              .withNearbyDistanceMeterClass(CodeDistanceMeter.class))));
    }
    return phase;
  }

  private static Fixture fixture(SelectorKind kind) {
    var descriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    var entityDescriptor = descriptor.getEntityDescriptor();
    var value = new TestdataListValue("0");
    var entity = TestdataListEntity.createWithValues("100", value);
    var childEntities = SelectorTestUtils.mockEntitySelector(entityDescriptor, entity);
    var originEntities = SelectorTestUtils.mockReplayingEntitySelector(entityDescriptor, entity);
    var childValues = SelectorTestUtils.mockIterableValueSelector(descriptor, value);
    return switch (kind) {
      case ENTITY ->
          new Fixture(
              new NearEntityNearbyEntitySelector<>(
                  childEntities, originEntities, new CodeDistanceMeter(), null, false),
              childEntities);
      case VALUE ->
          new Fixture(
              new NearEntityNearbyValueSelector<>(
                  childValues, originEntities, new CodeDistanceMeter(), null, false),
              childValues);
      case DESTINATION -> {
        ElementDestinationSelector<TestdataListSolution> child =
            mock(ElementDestinationSelector.class);
        yield new Fixture(
            new NearbyDestinationSelector<>(
                new DestinationSelectorConfig(),
                buildHeuristicConfigPolicy(TestdataListSolution.buildSolutionDescriptor()),
                new NearbySelectionConfig().withNearbyDistanceMeterClass(CodeDistanceMeter.class),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.ORIGINAL,
                child,
                childEntities,
                childValues,
                originEntities,
                null,
                null),
            child);
      }
      case SUB_LIST -> {
        RandomSubListSelector<TestdataListSolution> child = mock(RandomSubListSelector.class);
        when(child.getVariableDescriptor()).thenReturn(descriptor);
        when(child.getMinimumSubListSize()).thenReturn(1);
        when(child.getMaximumSubListSize()).thenReturn(1);
        var origin =
            SelectorTestUtils.mockReplayingSubListSelector(descriptor, new SubList(entity, 0, 1));
        yield new Fixture(
            new NearbySubListSelector<>(
                child, origin, new CodeDistanceMeter(), null, false, Integer.MAX_VALUE, false),
            child);
      }
    };
  }

  private record Fixture(
      Selector<TestdataListSolution> selector,
      PhaseLifecycleListener<TestdataListSolution> child) {}

  public static final class CodeDistanceMeter
      implements NearbyDistanceMeter<TestdataObject, TestdataObject> {
    @Override
    public double getNearbyDistance(TestdataObject origin, TestdataObject destination) {
      return Math.abs(Integer.parseInt(origin.getCode()) - Integer.parseInt(destination.getCode()));
    }
  }

  public static final class ListSizeScoreCalculator
      implements EasyScoreCalculator<TestdataPinnedNoShadowsListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataPinnedNoShadowsListSolution solution) {
      return SimpleScore.of(
          solution.getEntityList().stream()
              .mapToInt(entity -> entity.getValueList().size() * entity.getValueList().size())
              .sum());
    }
  }
}
