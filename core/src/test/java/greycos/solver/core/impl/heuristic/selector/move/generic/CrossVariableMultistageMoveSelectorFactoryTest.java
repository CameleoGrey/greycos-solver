package greycos.solver.core.impl.heuristic.selector.move.generic;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.CrossVariableCustomStage;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableReference;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageVariableConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageVariableKind;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelector;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.childtoo.TestdataBothAnnotatedChildEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.childtoo.TestdataBothAnnotatedSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CrossVariableMultistageMoveSelectorFactoryTest {

  @Test
  void resolvesMultipleBasicVariablesAndMixedVariables() {
    assertThat(
            build(
                config()
                    .withVariables(
                        BasicVariableReference.of(
                            TestdataMultiVarEntity.class, "primaryValue", TestdataValue.class),
                        BasicVariableReference.of(
                            TestdataMultiVarEntity.class, "secondaryValue", TestdataValue.class)),
                TestdataMultiVarSolution.buildSolutionDescriptor()))
        .isInstanceOf(MultistageMoveSelector.class);
    var mixedConfig =
        config()
            .withVariables(
                BasicVariableReference.of(
                    TestdataMixedEntity.class, "basicValue", TestdataMixedOtherValue.class),
                ListVariableReference.of(
                    TestdataMixedEntity.class, "valueList", TestdataMixedValue.class));
    assertThat(build(mixedConfig, TestdataMixedSolution.buildSolutionDescriptor()))
        .isInstanceOf(MultistageMoveSelector.class);
    assertThat(
            build(
                new UnionMoveSelectorConfig()
                    .withMoveSelectors(
                        new UnionMoveSelectorConfig().withMoveSelectors(mixedConfig)),
                TestdataMixedSolution.buildSolutionDescriptor()))
        .isInstanceOf(UnionMoveSelector.class);
  }

  @Test
  void preservesDistinctDeclaredScopesForAnInheritedDescriptor() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            TestdataBothAnnotatedSolution.class,
            TestdataEntity.class,
            TestdataBothAnnotatedChildEntity.class);
    var baseReference =
        BasicVariableReference.of(TestdataEntity.class, "value", TestdataValue.class);
    var childReference =
        BasicVariableReference.of(
            TestdataBothAnnotatedChildEntity.class, "value", TestdataValue.class);
    var factory =
        new CrossVariableMultistageMoveSelectorFactory<TestdataBothAnnotatedSolution>(
            config().withVariables(baseReference, childReference));
    var bindings = factory.resolveVariableBindings(policy(descriptor));
    assertThat(bindings).hasSize(2);
    assertThat(bindings.get(0).reference()).isEqualTo(baseReference);
    assertThat(bindings.get(1).reference()).isEqualTo(childReference);
    assertThat(bindings.get(0).variable()).isSameAs(bindings.get(1).variable());
    assertThat(bindings.get(0).targetEntityDescriptor().getEntityClass())
        .isEqualTo(TestdataEntity.class);
    assertThat(bindings.get(1).targetEntityDescriptor().getEntityClass())
        .isEqualTo(TestdataBothAnnotatedChildEntity.class);
    assertThat(build(config().withVariables(baseReference, childReference), descriptor))
        .isInstanceOf(MultistageMoveSelector.class);
  }

  @Test
  void requiresDeclarationsAndRejectsNullEntries() {
    for (var declarations : Arrays.<List<MultistageVariableConfig>>asList(null, List.of())) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> buildBasic(config().withVariableList(declarations)))
          .withMessageContaining("nonempty variableList");
    }
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> buildBasic(config().withVariableList(Arrays.asList(basicDeclaration(), null))))
        .withMessageContaining("null variable declaration")
        .withMessageContaining("index (1)");
  }

  @Test
  void requiresEveryDeclarationField() {
    for (var declaration :
        List.of(
            basicDeclaration().withKind(null),
            basicDeclaration().withEntityClass(null),
            basicDeclaration().withVariableName(null),
            basicDeclaration().withVariableName("  "),
            basicDeclaration().withValueClass(null))) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> buildBasic(config().withVariableList(List.of(declaration))))
          .withMessageContaining("incomplete variable declaration")
          .withMessageContaining("index (0)");
    }
  }

  @Test
  void rejectsDuplicateDeclarations() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                buildBasic(
                    config().withVariableList(List.of(basicDeclaration(), basicDeclaration()))))
        .withMessageContaining("duplicate variable declaration")
        .withMessageContaining("value")
        .withMessageContaining("index (1)");
  }

  @Test
  void rejectsUnknownEntityMissingVariableAndShadowVariable() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                buildBasic(
                    config()
                        .withVariableList(
                            List.of(basicDeclaration().withEntityClass(String.class)))))
        .withMessageContaining("java.lang.String")
        .withMessageContaining("not a configured planning entity");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                buildBasic(
                    config()
                        .withVariableList(List.of(basicDeclaration().withVariableName("missing")))))
        .withMessageContaining("missing")
        .withMessageContaining("not a genuine planning variable");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    config()
                        .withVariables(
                            BasicVariableReference.of(
                                TestdataListValue.class, "index", Integer.class)),
                    TestdataListSolution.buildSolutionDescriptor()))
        .withMessageContaining("index")
        .withMessageContaining("not a genuine planning variable");
  }

  @Test
  void rejectsWrongKindInBothDirections() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                buildBasic(
                    config()
                        .withVariableList(
                            List.of(basicDeclaration().withKind(MultistageVariableKind.LIST)))))
        .withMessageContaining("declares kind (LIST)")
        .withMessageContaining("planning variable kind is (BASIC)");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                build(
                    config()
                        .withVariables(
                            BasicVariableReference.of(
                                TestdataListEntity.class, "valueList", TestdataListValue.class)),
                    TestdataListSolution.buildSolutionDescriptor()))
        .withMessageContaining("declares kind (BASIC)")
        .withMessageContaining("planning variable kind is (LIST)");
  }

  @Test
  void requiresExactBasicAndListElementTypes() {
    for (var valueClass : List.of(Object.class, String.class, SpecificValue.class)) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  buildBasic(
                      config()
                          .withVariableList(
                              List.of(basicDeclaration().withValueClass(valueClass)))))
          .withMessageContaining("exact declared value type")
          .withMessageContaining(TestdataValue.class.getName());
    }
    for (var valueClass : List.of(Object.class, List.class, TestdataValue.class)) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  build(
                      config()
                          .withVariables(
                              ListVariableReference.of(
                                  TestdataListEntity.class, "valueList", valueClass)),
                      TestdataListSolution.buildSolutionDescriptor()))
          .withMessageContaining("exact declared value type")
          .withMessageContaining(TestdataListValue.class.getName());
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"ORIGINAL", "RANDOM"})
  void supportsOriginalAndRandom(SelectionOrder order) {
    var selector = buildBasic(config().withSelectionOrder(order));
    assertThat(selector).isInstanceOf(MultistageMoveSelector.class);
    assertThat(selector.isNeverEnding()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionCacheType.class,
      names = {"STEP", "PHASE", "SOLVER"})
  void rejectsSelectorAndAncestorCaching(SelectionCacheType cacheType) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> buildBasic(config().withCacheType(cacheType)))
        .withMessageContaining("JUST_IN_TIME");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                buildBasic(
                    new UnionMoveSelectorConfig()
                        .withCacheType(cacheType)
                        .withMoveSelectors(
                            new UnionMoveSelectorConfig().withMoveSelectors(config()))))
        .withMessageContaining("JUST_IN_TIME");
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"SORTED", "SHUFFLED", "PROBABILISTIC"})
  void rejectsUnsupportedOrders(SelectionOrder order) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> buildBasic(config().withSelectionOrder(order)))
        .withMessageContaining("only ORIGINAL and RANDOM");
  }

  @Test
  void rejectsDirectAndNestedCartesianPlacement() {
    for (var child : List.of(config(), new UnionMoveSelectorConfig().withMoveSelectors(config()))) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () -> buildBasic(new CartesianProductMoveSelectorConfig().withMoveSelectors(child)))
          .withMessageContaining("cartesianProductMoveSelector")
          .withMessageContaining("multistage");
    }
  }

  @Test
  void rejectsUnsupportedPhaseAndDirectedOriginSelection() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MoveSelectorFactory.<TestdataSolution>create(config())
                    .buildMoveSelector(
                        buildHeuristicConfigPolicy(),
                        SelectionCacheType.JUST_IN_TIME,
                        SelectionOrder.RANDOM,
                        false))
        .withMessageContaining("requires a local search phase");
    var directedPolicy =
        policy(TestdataSolution.buildSolutionDescriptor())
            .cloneBuilder()
            .withGuidedLocalSearchSelectionContext(new GuidedLocalSearchSelectionContext<>())
            .build();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MoveSelectorFactory.<TestdataSolution>create(config())
                    .buildMoveSelector(
                        directedPolicy,
                        SelectionCacheType.JUST_IN_TIME,
                        SelectionOrder.RANDOM,
                        false))
        .withMessageContaining("directedOriginSelection");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void rejectsInvalidProvidersAndBudgets() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> buildBasic(config().withStageProviderClass(null)))
        .withMessageContaining("stageProviderClass (null)")
        .withMessageContaining("CrossVariableStageProvider");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> buildBasic(config().withStageProviderClass((Class) String.class)))
        .withMessageContaining("CrossVariableStageProvider");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> buildBasic(config().withStageProviderClass(AbstractProvider.class)))
        .withMessageContaining("public concrete class");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> buildBasic(config().withStageProviderClass(NoDefaultConstructorProvider.class)))
        .withMessageContaining("public no-arg constructor");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> buildBasic(config().withCandidateCountLimit(0)))
        .withMessageContaining("candidateCountLimit");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> buildBasic(config().withProbeCountLimit(-1)))
        .withMessageContaining("probeCountLimit");
  }

  private static CrossVariableMultistageMoveSelectorConfig config() {
    return new CrossVariableMultistageMoveSelectorConfig()
        .withStageProviderClass(Provider.class)
        .withVariableList(List.of(basicDeclaration()));
  }

  private static MultistageVariableConfig basicDeclaration() {
    return new MultistageVariableConfig()
        .withKind(MultistageVariableKind.BASIC)
        .withEntityClass(TestdataEntity.class)
        .withVariableName("value")
        .withValueClass(TestdataValue.class);
  }

  private static <Solution_> HeuristicConfigPolicy<Solution_> policy(
      SolutionDescriptor<Solution_> descriptor) {
    return buildHeuristicConfigPolicy(descriptor)
        .cloneBuilder()
        .withMultistageMoveSelectionEnabled(true)
        .build();
  }

  private static MoveSelector<TestdataSolution> buildBasic(MoveSelectorConfig<?> config) {
    return build(config, TestdataSolution.buildSolutionDescriptor());
  }

  private static <Solution_> MoveSelector<Solution_> build(
      MoveSelectorConfig<?> config, SolutionDescriptor<Solution_> descriptor) {
    return MoveSelectorFactory.<Solution_>create(config)
        .buildMoveSelector(
            policy(descriptor), SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM, false);
  }

  public static class Provider implements CrossVariableStageProvider<Object, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 0;
    }

    @Override
    public List<CrossVariableCustomStage<Object, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of();
    }
  }

  public abstract static class AbstractProvider extends Provider {}

  public static class NoDefaultConstructorProvider extends Provider {
    public NoDefaultConstructorProvider(String unused) {}
  }

  public static class SpecificValue extends TestdataValue {}
}
