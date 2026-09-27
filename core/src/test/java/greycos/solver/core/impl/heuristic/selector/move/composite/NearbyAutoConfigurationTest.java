package greycos.solver.core.impl.heuristic.selector.move.composite;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionProbabilityWeightFactory;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMoveSelector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NearbyAutoConfigurationTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unfoldedFamilyKeepsEquivalentConcreteSelectionWeight(boolean explicitWeights) {
    Double changeWeight = explicitWeights ? 6.0 : null;
    Double swapWeight = explicitWeights ? 2.0 : null;
    double concreteChangeWeight = (changeWeight == null ? 1.0 : changeWeight) / 3.0;
    var folded =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new ChangeMoveSelectorConfig().withFixedProbabilityWeight(changeWeight),
                new SwapMoveSelectorConfig().withFixedProbabilityWeight(swapWeight));
    var concrete =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                concreteChange("primaryValue", concreteChangeWeight),
                concreteChange("secondaryValue", concreteChangeWeight),
                concreteChange("tertiaryValueAllowedUnassigned", concreteChangeWeight),
                new SwapMoveSelectorConfig().withFixedProbabilityWeight(swapWeight));

    double foldedChangeProbability = changeProbability(buildMultiVariableSelector(folded));

    assertThat(foldedChangeProbability).isCloseTo(explicitWeights ? 0.75 : 0.5, within(1.0e-12));
    assertThat(foldedChangeProbability)
        .isCloseTo(changeProbability(buildMultiVariableSelector(concrete)), within(1.0e-12));
  }

  @Test
  void explicitNestedUnionKeepsItsWeight() {
    var config =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new UnionMoveSelectorConfig().withMoveSelectors(new ChangeMoveSelectorConfig()),
                new SwapMoveSelectorConfig());

    var selector = buildMultiVariableSelector(config);

    assertThat(selector.getSelectorProbabilityWeightFactory()).isNull();
    assertThat(changeProbability(selector)).isCloseTo(1.0 / 3.0, within(1.0e-12));
  }

  @Test
  void customProbabilityWeightFactoryRemainsAuthoritative() {
    var folded =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig())
            .withSelectorProbabilityWeightFactoryClass(SelectorCountWeightFactory.class);
    var concrete =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                concreteChange("primaryValue", 1.0),
                concreteChange("secondaryValue", 1.0),
                concreteChange("tertiaryValueAllowedUnassigned", 1.0),
                new SwapMoveSelectorConfig())
            .withSelectorProbabilityWeightFactoryClass(SelectorCountWeightFactory.class);

    var selector = buildMultiVariableSelector(folded);

    assertThat(selector.getSelectorProbabilityWeightFactory())
        .isInstanceOf(SelectorCountWeightFactory.class);
    assertThat(changeProbability(selector))
        .isCloseTo(0.75, within(1.0e-12))
        .isCloseTo(changeProbability(buildMultiVariableSelector(concrete)), within(1.0e-12));
  }

  private static ChangeMoveSelectorConfig concreteChange(String variableName, double weight) {
    return new ChangeMoveSelectorConfig()
        .withValueSelectorConfig(new ValueSelectorConfig().withVariableName(variableName))
        .withFixedProbabilityWeight(weight);
  }

  private static UnionMoveSelector<TestdataMultiVarSolution> buildMultiVariableSelector(
      UnionMoveSelectorConfig config) {
    var policy =
        buildHeuristicConfigPolicy(TestdataMultiVarSolution.buildSolutionDescriptor())
            .cloneBuilder()
            .withNearbyDistanceMeterClass(TestDistanceMeter.class)
            .build();
    return (UnionMoveSelector<TestdataMultiVarSolution>)
        new UnionMoveSelectorFactory<TestdataMultiVarSolution>(config)
            .buildMoveSelector(
                policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM, false);
  }

  private static double changeProbability(MoveSelector<TestdataMultiVarSolution> selector) {
    if (selector instanceof ChangeMoveSelector<?>) {
      return 1.0;
    }
    if (!(selector instanceof UnionMoveSelector<TestdataMultiVarSolution> union)) {
      return 0.0;
    }
    var weightFactory = union.getSelectorProbabilityWeightFactory();
    double totalWeight = 0.0;
    double changeWeight = 0.0;
    for (var child : union.getChildMoveSelectorList()) {
      double weight =
          weightFactory == null ? 1.0 : weightFactory.createProbabilityWeight(null, child);
      totalWeight += weight;
      changeWeight += weight * changeProbability(child);
    }
    return changeWeight / totalWeight;
  }

  @Test
  void defaultMultiVariableNeighborhood() {
    var config = solverConfig(TestdataMultiVarSolution.class, TestdataMultiVarEntity.class);

    assertRepeatedSolves(config, () -> TestdataMultiVarSolution.generateSolution(3, 3, 3));
  }

  @Test
  void defaultMixedVariableNeighborhood() {
    assertRepeatedMixedSolves(mixedSolverConfig());
  }

  @ParameterizedTest
  @MethodSource("mixedListSelectors")
  void mixedListNeighborhood(MoveSelectorConfig<?> moveSelectorConfig) {
    assertRepeatedMixedSolves(
        mixedSolverConfig()
            .withPhases(new LocalSearchPhaseConfig().withMoveSelectorConfig(moveSelectorConfig)));
  }

  static Stream<MoveSelectorConfig<?>> mixedListSelectors() {
    return Stream.of(
        new ListChangeMoveSelectorConfig(),
        new ListSwapMoveSelectorConfig(),
        new KOptListMoveSelectorConfig());
  }

  @ParameterizedTest(name = "{0}, explicit origin variableName = {1}")
  @MethodSource("explicitMixedNearbySelectors")
  void explicitMixedNearbyNeighborhood(
      MoveSelectorConfig<?> moveSelectorConfig, boolean explicitOriginVariableName) {
    var config =
        mixedSolverConfig()
            .withPhases(new LocalSearchPhaseConfig().withMoveSelectorConfig(moveSelectorConfig));
    config.setNearbyDistanceMeterClass(null);

    assertRepeatedMixedSolves(config);
  }

  static Stream<Arguments> explicitMixedNearbySelectors() {
    return Stream.of(false, true)
        .flatMap(
            explicitOriginVariableName -> {
              var origin = new ValueSelectorConfig().withVariableName("valueList").withId("origin");
              var replay = new ValueSelectorConfig().withMimicSelectorRef("origin");
              if (explicitOriginVariableName) {
                replay.setVariableName("valueList");
              }
              var nearby =
                  new NearbySelectionConfig()
                      .withOriginValueSelectorConfig(replay)
                      .withNearbyDistanceMeterClass(TestDistanceMeter.class);
              return Stream.<MoveSelectorConfig<?>>of(
                      new ListChangeMoveSelectorConfig()
                          .withValueSelectorConfig(origin.copyConfig())
                          .withDestinationSelectorConfig(
                              new DestinationSelectorConfig()
                                  .withNearbySelectionConfig(nearby.copyConfig())),
                      new ListSwapMoveSelectorConfig()
                          .withValueSelectorConfig(origin.copyConfig())
                          .withSecondaryValueSelectorConfig(
                              new ValueSelectorConfig()
                                  .withVariableName("valueList")
                                  .withNearbySelectionConfig(nearby.copyConfig())),
                      new KOptListMoveSelectorConfig()
                          .withOriginSelectorConfig(origin.copyConfig())
                          .withValueSelectorConfig(
                              new ValueSelectorConfig()
                                  .withVariableName("valueList")
                                  .withNearbySelectionConfig(nearby.copyConfig())))
                  .map(move -> Arguments.of(move, explicitOriginVariableName));
            });
  }

  private static SolverConfig mixedSolverConfig() {
    return solverConfig(
            TestdataMixedSolution.class,
            TestdataMixedEntity.class,
            TestdataMixedValue.class,
            TestdataMixedOtherValue.class)
        .withEnvironmentMode(EnvironmentMode.STEP_ASSERT)
        .withEasyScoreCalculatorClass(TestdataMixedEasyScoreCalculator.class);
  }

  private static void assertRepeatedMixedSolves(SolverConfig config) {
    var originalConfig = config.copyConfig();
    var factory = SolverFactory.<TestdataMixedSolution>create(config);
    for (int build = 0; build < 2; build++) {
      var problem = TestdataMixedSolution.generateUninitializedSolution(3, 9, 3);
      for (int i = 0; i < problem.getEntityList().size(); i++) {
        var entity = problem.getEntityList().get(i);
        entity.setBasicValue(problem.getOtherValueList().get(i));
        entity.setSecondBasicValue(problem.getOtherValueList().get((i + 1) % 3));
      }
      for (int i = 0; i < problem.getValueList().size(); i++) {
        problem.getEntityList().get(i % 3).getValueList().add(problem.getValueList().get(i));
      }
      SolutionManager.create(factory).update(problem);
      var initialScore = problem.getScore();
      TestDistanceMeter.invocationCount.set(0);

      var result = factory.buildSolver().solve(problem);

      assertThat(TestDistanceMeter.invocationCount).hasPositiveValue();
      assertThat(result.getScore())
          .isEqualTo(new TestdataMixedEasyScoreCalculator().calculateScore(result))
          .isGreaterThanOrEqualTo(initialScore);
      assertThat(
              result.getEntityList().stream()
                  .flatMap(entity -> entity.getValueList().stream())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(result.getValueList());
      for (var entity : result.getEntityList()) {
        assertThat(result.getOtherValueList())
            .contains(entity.getBasicValue(), entity.getSecondBasicValue());
        for (int index = 0; index < entity.getValueList().size(); index++) {
          var value = entity.getValueList().get(index);
          assertThat(value.getEntity()).isSameAs(entity);
          assertThat(value.getIndex()).isEqualTo(index);
        }
      }
      assertThat(config).usingRecursiveComparison().isEqualTo(originalConfig);
    }
  }

  @Test
  void nestedMultiVariableNeighborhood() {
    var config =
        solverConfig(TestdataMultiVarSolution.class, TestdataMultiVarEntity.class)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new UnionMoveSelectorConfig()
                            .withMoveSelectors(
                                new UnionMoveSelectorConfig()
                                    .withMoveSelectors(new ChangeMoveSelectorConfig()),
                                new SwapMoveSelectorConfig())));

    assertRepeatedSolves(config, () -> TestdataMultiVarSolution.generateSolution(3, 3, 3));
  }

  @ParameterizedTest
  @MethodSource("namedBasicSelectors")
  void namedBasicSelectors(MoveSelectorConfig<?> moveSelectorConfig) {
    var config =
        solverConfig(TestdataSolution.class, TestdataEntity.class)
            .withPhases(new LocalSearchPhaseConfig().withMoveSelectorConfig(moveSelectorConfig));

    assertRepeatedSolves(config, () -> TestdataSolution.generateSolution(3, 3));
  }

  static Stream<MoveSelectorConfig<?>> namedBasicSelectors() {
    return Stream.of(
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("entity"))
            .withValueSelectorConfig(new ValueSelectorConfig().withId("value")),
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("primary"))
            .withSecondaryEntitySelectorConfig(new EntitySelectorConfig().withId("secondary")));
  }

  @ParameterizedTest
  @MethodSource("namedListSelectors")
  void namedListSelectors(MoveSelectorConfig<?> moveSelectorConfig) {
    var config =
        solverConfig(TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
            .withPhases(new LocalSearchPhaseConfig().withMoveSelectorConfig(moveSelectorConfig));

    assertRepeatedSolves(config, () -> TestdataListSolution.generateInitializedSolution(8, 2));
  }

  static Stream<MoveSelectorConfig<?>> namedListSelectors() {
    return Stream.of(
        new ListChangeMoveSelectorConfig()
            .withValueSelectorConfig(new ValueSelectorConfig().withId("origin"))
            .withDestinationSelectorConfig(
                new DestinationSelectorConfig()
                    .withEntitySelectorConfig(
                        new EntitySelectorConfig().withId("destinationEntity"))
                    .withValueSelectorConfig(new ValueSelectorConfig().withId("destinationValue"))),
        new ListSwapMoveSelectorConfig()
            .withValueSelectorConfig(new ValueSelectorConfig().withId("primary"))
            .withSecondaryValueSelectorConfig(new ValueSelectorConfig().withId("secondary")),
        new KOptListMoveSelectorConfig()
            .withOriginSelectorConfig(new ValueSelectorConfig().withId("origin"))
            .withValueSelectorConfig(new ValueSelectorConfig().withId("value")));
  }

  @Test
  void explicitNearbyStillConflictsWithQuickConfiguration() {
    var change =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("origin"))
            .withValueSelectorConfig(
                new ValueSelectorConfig()
                    .withNearbySelectionConfig(
                        new NearbySelectionConfig()
                            .withOriginEntitySelectorConfig(
                                new EntitySelectorConfig().withMimicSelectorRef("origin"))
                            .withNearbyDistanceMeterClass(TestDistanceMeter.class)));
    var config =
        solverConfig(TestdataSolution.class, TestdataEntity.class)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new UnionMoveSelectorConfig()
                            .withMoveSelectors(
                                new UnionMoveSelectorConfig().withMoveSelectors(change))));
    var originalConfig = config.copyConfig();
    var factory = SolverFactory.create(config);

    for (int build = 0; build < 2; build++) {
      assertThatIllegalArgumentException()
          .isThrownBy(factory::buildSolver)
          .withMessageContainingAll("already includes", "nearbyDistanceMeterClass");
      assertThat(config).usingRecursiveComparison().isEqualTo(originalConfig);
    }
  }

  private static SolverConfig solverConfig(Class<?> solutionClass, Class<?>... entityClasses) {
    return new SolverConfig()
        .withSolutionClass(solutionClass)
        .withEntityClasses(entityClasses)
        .withEasyScoreCalculatorClass(TestScoreCalculator.class)
        .withNearbyDistanceMeterClass(TestDistanceMeter.class)
        .withPhases(new LocalSearchPhaseConfig())
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(20));
  }

  private static <Solution_> void assertRepeatedSolves(
      SolverConfig config, Supplier<Solution_> problemSupplier) {
    var originalConfig = config.copyConfig();
    var factory = SolverFactory.<Solution_>create(config);
    for (int build = 0; build < 2; build++) {
      TestDistanceMeter.invocationCount.set(0);
      assertThat(factory.buildSolver().solve(problemSupplier.get())).isNotNull();
      assertThat(TestDistanceMeter.invocationCount).hasPositiveValue();
      assertThat(config).usingRecursiveComparison().isEqualTo(originalConfig);
    }
  }

  public static final class TestDistanceMeter implements NearbyDistanceMeter<Object, Object> {
    private static final AtomicInteger invocationCount = new AtomicInteger();

    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      invocationCount.incrementAndGet();
      return origin == destination ? 0.0 : 1.0;
    }
  }

  public static final class TestScoreCalculator
      implements EasyScoreCalculator<Object, SimpleScore> {
    @Override
    public SimpleScore calculateScore(Object solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class SelectorCountWeightFactory
      implements SelectionProbabilityWeightFactory<
          TestdataMultiVarSolution, MoveSelector<TestdataMultiVarSolution>> {

    @Override
    public double createProbabilityWeight(
        ScoreDirector<TestdataMultiVarSolution> scoreDirector,
        MoveSelector<TestdataMultiVarSolution> selector) {
      return selector instanceof UnionMoveSelector<?> union
          ? union.getChildMoveSelectorList().size()
          : 1.0;
    }
  }
}
