package greycos.solver.core.impl.heuristic.selector.move.generic;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableCustomStage;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelector;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MultistageMoveSelectorFactoryTest {

  @Test
  void infersTheOnlyBasicVariable() {
    assertThat(build(basic(), TestdataSolution.buildSolutionDescriptor()))
        .isInstanceOf(MultistageMoveSelector.class);
  }

  @Test
  void infersTheOnlyListVariable() {
    assertThat(build(list(), TestdataListSolution.buildSolutionDescriptor()))
        .isInstanceOf(MultistageMoveSelector.class);
  }

  @Test
  void resolvesExplicitBasicVariable() {
    assertThat(
            build(
                basic().withVariableName("secondaryValue"),
                TestdataMultiVarSolution.buildSolutionDescriptor()))
        .isInstanceOf(MultistageMoveSelector.class);
  }

  @Test
  void rejectsAmbiguousOrMissingVariablesAndEntities() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> build(basic(), TestdataMultiVarSolution.buildSolutionDescriptor()))
        .withMessageContaining("exactly one")
        .withMessageContaining("variableName");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    basic().withVariableName("missing"),
                    TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("missing")
        .withMessageContaining("0 basic planning variables");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    basic().withEntityClass(String.class),
                    TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("java.lang.String")
        .withMessageContaining("not a configured planning entity");
    assertThat(
            build(
                basic().withEntityClass(TestdataEntity.class),
                TestdataSolution.buildSolutionDescriptor()))
        .isInstanceOf(MultistageMoveSelector.class);
  }

  @Test
  void rejectsWrongVariableKind() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> build(basic(), TestdataListSolution.buildSolutionDescriptor()))
        .withMessageContaining("0 basic planning variables");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> build(list(), TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("0 list planning variables");
  }

  @Test
  void supportsBothVariableKindsInMixedModelAndNestedUnions() {
    var mixedUnion =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new UnionMoveSelectorConfig()
                    .withMoveSelectors(basic().withVariableName("basicValue")),
                list());
    assertThat(build(mixedUnion, TestdataMixedSolution.buildSolutionDescriptor()))
        .isInstanceOf(UnionMoveSelector.class);
  }

  @Test
  void rejectsDirectAndNestedCartesianPlacement() {
    for (var child : List.of(basic(), new UnionMoveSelectorConfig().withMoveSelectors(basic()))) {
      var cartesian = new CartesianProductMoveSelectorConfig().withMoveSelectors(child);
      assertThatIllegalArgumentException()
          .isThrownBy(() -> build(cartesian, TestdataSolution.buildSolutionDescriptor()))
          .withMessageContaining("cartesianProductMoveSelector")
          .withMessageContaining("multistage");
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionCacheType.class,
      names = {"STEP", "PHASE", "SOLVER"})
  void rejectsCachingOnSelectorAndAncestor(SelectionCacheType cacheType) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(basic().withCacheType(cacheType), TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("JUST_IN_TIME");
    var cachedUnion =
        new UnionMoveSelectorConfig()
            .withCacheType(cacheType)
            .withMoveSelectors(new UnionMoveSelectorConfig().withMoveSelectors(basic()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> build(cachedUnion, TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("JUST_IN_TIME");
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"SORTED", "SHUFFLED", "PROBABILISTIC"})
  void rejectsUnsupportedOrder(SelectionOrder order) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    basic().withSelectionOrder(order), TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("only ORIGINAL and RANDOM");
  }

  @Test
  void rejectsPhasesWithoutMultistagePreparation() {
    var configPolicy = buildHeuristicConfigPolicy();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MoveSelectorFactory.<TestdataSolution>create(basic())
                    .buildMoveSelector(
                        configPolicy,
                        SelectionCacheType.JUST_IN_TIME,
                        SelectionOrder.RANDOM,
                        false))
        .withMessageContaining("requires a local search phase");
    var localSearchPolicy =
        configPolicy.cloneBuilder().withMultistageMoveSelectionEnabled(true).build();
    assertThat(localSearchPolicy.copyConfigPolicy().isMultistageMoveSelectionEnabled()).isTrue();
    assertThat(localSearchPolicy.copyPhaseConfigPolicy().isMultistageMoveSelectionEnabled())
        .isFalse();
  }

  @Test
  void rejectsDirectedOriginSelection() {
    var configPolicy =
        policy(TestdataSolution.buildSolutionDescriptor())
            .cloneBuilder()
            .withGuidedLocalSearchSelectionContext(new GuidedLocalSearchSelectionContext<>())
            .build();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MoveSelectorFactory.<TestdataSolution>create(basic())
                    .buildMoveSelector(
                        configPolicy,
                        SelectionCacheType.JUST_IN_TIME,
                        SelectionOrder.RANDOM,
                        false))
        .withMessageContaining("directedOriginSelection");
  }

  @Test
  void rejectsMissingAndNonInstantiableProviders() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    new MultistageMoveSelectorConfig(), TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("stageProviderClass (null)");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    basic().withStageProviderClass(AbstractProvider.class),
                    TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("public concrete class");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    basic().withStageProviderClass(NoDefaultConstructorProvider.class),
                    TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("public no-arg constructor");
  }

  @Test
  void rejectsNonpositiveBudgets() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    basic().withCandidateCountLimit(0), TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("candidateCountLimit");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(basic().withProbeCountLimit(-1), TestdataSolution.buildSolutionDescriptor()))
        .withMessageContaining("probeCountLimit");
  }

  private static MultistageMoveSelectorConfig basic() {
    return new MultistageMoveSelectorConfig().withStageProviderClass(BasicProvider.class);
  }

  private static ListMultistageMoveSelectorConfig list() {
    return new ListMultistageMoveSelectorConfig().withStageProviderClass(ListProvider.class);
  }

  private static <Solution_> HeuristicConfigPolicy<Solution_> policy(
      SolutionDescriptor<Solution_> descriptor) {
    return buildHeuristicConfigPolicy(descriptor)
        .cloneBuilder()
        .withMultistageMoveSelectionEnabled(true)
        .build();
  }

  private static <Solution_> MoveSelector<Solution_> build(
      MoveSelectorConfig<?> config, SolutionDescriptor<Solution_> descriptor) {
    return MoveSelectorFactory.<Solution_>create(config)
        .buildMoveSelector(
            policy(descriptor), SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM, false);
  }

  public static class BasicProvider
      implements BasicVariableStageProvider<Object, Object, Object, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 0;
    }

    @Override
    public List<BasicVariableCustomStage<Object, Object, Object, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of();
    }
  }

  public static class ListProvider
      implements ListVariableStageProvider<Object, Object, Object, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 0;
    }

    @Override
    public List<ListVariableCustomStage<Object, Object, Object, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of();
    }
  }

  public abstract static class AbstractProvider extends BasicProvider {}

  public static class NoDefaultConstructorProvider extends BasicProvider {
    public NoDefaultConstructorProvider(String unused) {}
  }
}
