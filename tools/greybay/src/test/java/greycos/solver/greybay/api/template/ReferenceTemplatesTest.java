package greycos.solver.greybay.api.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsGrouping;
import greycos.solver.core.api.solver.alns.AlnsRanking;
import greycos.solver.core.api.solver.alns.AlnsRelatedness;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.alns.AlnsSelectionPolicyType;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.core.testutil.PlannerTestUtils;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.ProblemCapabilities;
import greycos.solver.greybay.config.Activation;
import greycos.solver.greybay.config.CategoricalDimension;
import greycos.solver.greybay.config.Dimension;
import greycos.solver.greybay.config.FixedDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.config.template.AlnsPortfolio;
import greycos.solver.greybay.config.template.LocalSearchVariant;
import greycos.solver.greybay.config.template.MovePortfolio;
import greycos.solver.greybay.config.template.TemplateParameters;
import greycos.solver.greybay.config.template.TemplateRequirements;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(30)
class ReferenceTemplatesTest {
  private static final ComponentIdentity ID =
      new ComponentIdentity("reference-test", "1", "test-artifact");
  private static final Map<String, MovePortfolio> MOVES =
      Map.of(
          "automatic",
          MovePortfolio.automatic(
              new ComponentIdentity("automatic", "1"), TemplateRequirements.allModels()));

  @ParameterizedTest
  @EnumSource(LocalSearchVariant.class)
  void ordinaryBranchesRunAndExportWithoutInheritedAcceptance(LocalSearchVariant variant) {
    var template = localSearch(variant);
    template.validate(capabilities(true, false));
    var inheritedAcceptor =
        new LocalSearchAcceptorConfig().withEntityTabuSize(7).withLateAcceptanceSize(99);
    var base =
        basicConfig()
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig()
                    .withAcceptorConfig(inheritedAcceptor)
                    .withTerminationConfig(
                        new TerminationConfig().withUnimprovedStepCountLimit(50)));
    var originalXml = xml(base);
    var config = template.materialize(base, template.defaultAssignment());
    var phase = (LocalSearchPhaseConfig) config.getPhaseConfigList().get(1);
    assertThat(phase.getLocalSearchType()).isNull();
    assertThat(phase.getTerminationConfig()).isNull();
    assertThat(phase.getForagerConfig().getAcceptedCountLimit()).isEqualTo(2);
    assertThat(phase.getAcceptorConfig().getLateAcceptanceSize())
        .isEqualTo(variant == LocalSearchVariant.LATE_ACCEPTANCE ? 4 : null);
    assertThat(phase.getAcceptorConfig().getEntityTabuSize())
        .isEqualTo(variant == LocalSearchVariant.TABU_SIZE ? 2 : null);
    assertThat(phase.getAcceptorConfig().getEntityTabuRatio())
        .isEqualTo(variant == LocalSearchVariant.TABU_RATIO ? .2 : null);
    assertThat(phase.getAcceptorConfig().getGreatDelugeWaterLevelIncrementScore())
        .isEqualTo(variant == LocalSearchVariant.GREAT_DELUGE_SCORE ? "10" : null);
    assertThat(xml(base)).isEqualTo(originalXml);
    var again =
        (LocalSearchPhaseConfig)
            template.materialize(base, template.defaultAssignment()).getPhaseConfigList().get(1);
    phase.getAcceptorConfig().setEntityTabuSize(123);
    assertThat(again.getAcceptorConfig().getEntityTabuSize())
        .isEqualTo(variant == LocalSearchVariant.TABU_SIZE ? 2 : null);
    var runnable = template.materialize(base, template.defaultAssignment());
    runBasic(roundTrip(runnable));
  }

  @Test
  void guidedSearchHasExplicitControllerAndNoOrdinaryAcceptance() {
    var template = gls();
    var config = template.materialize(basicConfig(), template.defaultAssignment());
    var phase = (LocalSearchPhaseConfig) config.getPhaseConfigList().get(1);
    assertThat(phase.getLocalSearchType()).isEqualTo(LocalSearchType.GUIDED_LOCAL_SEARCH);
    assertThat(phase.getAcceptorConfig()).isNull();
    assertThat(phase.getForagerConfig()).isNull();
    assertThat(phase.getGuidedLocalSearchConfig().getPenaltyFactor()).isEqualByComparingTo("0.125");
    assertThat(config.getEnablePreviewFeatureSet())
        .isEqualTo(basicConfig().getEnablePreviewFeatureSet());
    runBasic(roundTrip(config));
  }

  static Stream<Arguments> alnsBranches() {
    return Stream.of(AlnsAcceptanceType.values())
        .flatMap(
            acceptance ->
                Stream.of(AlnsSelectionPolicyType.values())
                    .flatMap(
                        selection ->
                            Stream.of("greedy", "regret", "randomized")
                                .flatMap(
                                    portfolio ->
                                        Stream.of("COUNT", "PERCENTAGE")
                                            .map(
                                                mode ->
                                                    Arguments.of(
                                                        acceptance, selection, portfolio, mode)))));
  }

  @ParameterizedTest
  @MethodSource("alnsBranches")
  void alnsBranchesRunWithOnlyActiveFields(
      AlnsAcceptanceType acceptance,
      AlnsSelectionPolicyType selection,
      String portfolio,
      String mode) {
    var template = alns();
    template.validate(capabilities(true, false));
    var assignment = alnsAssignment(acceptance, selection, portfolio, mode);
    var config = template.materialize(basicConfig(), assignment);
    var phase = (AlnsPhaseConfig) config.getPhaseConfigList().get(1);
    assertThat(phase.getMoveThreadingMode()).isEqualTo(AlnsMoveThreadingMode.PROBES);
    assertThat(phase.getMoveThreadCount()).isEqualTo(SolverConfig.MOVE_THREAD_COUNT_NONE);
    assertThat(phase.getRepairAttemptCount()).isNull();
    assertThat(phase.getLateAcceptanceSize())
        .isEqualTo(acceptance == AlnsAcceptanceType.LATE_ACCEPTANCE ? 4 : null);
    assertThat(phase.getStartingTemperature())
        .isEqualTo(acceptance == AlnsAcceptanceType.SIMULATED_ANNEALING ? "10" : null);
    assertThat(phase.getCoolingRate())
        .isEqualTo(acceptance == AlnsAcceptanceType.SIMULATED_ANNEALING ? .99 : null);
    assertThat(phase.getUcbExploration())
        .isEqualTo(selection == AlnsSelectionPolicyType.UCB ? 1.0 : null);
    assertThat(phase.getReactionFactor())
        .isEqualTo(selection == AlnsSelectionPolicyType.SEGMENTED_ROULETTE ? .5 : null);
    var repair = phase.getRepairOperatorConfigList().getFirst();
    assertThat(repair.getRegretK()).isEqualTo(portfolio.equals("regret") ? 3 : null);
    assertThat(repair.getTopK()).isEqualTo(portfolio.equals("randomized") ? 2 : null);
    var destroy = phase.getDestroyOperatorConfigList().getFirst();
    assertThat(destroy.getMinimumDestroyedCount()).isEqualTo(mode.equals("COUNT") ? 1 : null);
    assertThat(destroy.getMinimumDestroyedPercentage())
        .isEqualTo(mode.equals("PERCENTAGE") ? .2 : null);
    runBasic(roundTrip(config));
  }

  @Test
  void pipelinesPreserveConstructionAndExportNominalCaps() {
    for (var first : List.of(gls(), alns())) {
      var pipeline =
          ReferenceTemplates.pipeline(
              ID,
              first,
              localSearch(LocalSearchVariant.LATE_ACCEPTANCE),
              new FixedDimension("pipeline.firstFraction", .3),
              .3);
      var base = basicConfig();
      var chTermination = new TerminationConfig().withStepCountLimit(20);
      base.getPhaseConfigList().getFirst().setTerminationConfig(chTermination);
      var config = pipeline.materialize(base, pipeline.defaultAssignment());
      assertThat(config.getPhaseConfigList()).hasSize(3);
      assertThat(config.getPhaseConfigList().getFirst().getTerminationConfig().getStepCountLimit())
          .isEqualTo(20);
      var caps =
          pipeline.approvedSearchPhaseLimits(
              config, Duration.ofSeconds(60), pipeline.defaultAssignment());
      assertThat(caps).isEqualTo(Map.of(1, Duration.ofSeconds(18)));
      config.setTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(60)));
      caps.forEach(
          (index, duration) ->
              config
                  .getPhaseConfigList()
                  .get(index)
                  .setTerminationConfig(new TerminationConfig().withSpentLimit(duration)));
      var reloaded = roundTrip(config);
      assertThat(reloaded.getTerminationConfig().getSpentLimit()).isEqualTo(Duration.ofSeconds(60));
      assertThat(reloaded.getPhaseConfigList().get(1).getTerminationConfig().getSpentLimit())
          .isEqualTo(Duration.ofSeconds(18));
      assertThat(reloaded.getPhaseConfigList().get(2).getTerminationConfig()).isNull();
      runBasic(reloaded);
      assertThatThrownBy(
              () ->
                  pipeline.approvedSearchPhaseLimits(
                      config, Duration.ofMillis(1), pipeline.defaultAssignment()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("zero-millisecond");
    }
  }

  @Test
  void listAndMixedModelsRunAndPreserveBusinessBindingsAndShadows() {
    for (var template : List.of(localSearch(LocalSearchVariant.LATE_ACCEPTANCE), gls(), alns())) {
      template.validate(capabilities(false, true));
      var listConfig =
          PlannerTestUtils.buildSolverConfig(
                  TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
              .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class)
              .withPhases(new ConstructionHeuristicPhaseConfig());
      var list =
          SolverFactory.<TestdataListSolution>create(
                  runtimeConfig(
                      roundTrip(template.materialize(listConfig, template.defaultAssignment()))))
              .buildSolver()
              .solve(TestdataListSolution.generateUninitializedSolution(8, 3));
      assertThat(
              list.getEntityList().stream()
                  .flatMap(entity -> entity.getValueList().stream())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(list.getValueList());
      list.getEntityList()
          .forEach(
              entity -> {
                for (int i = 0; i < entity.getValueList().size(); i++) {
                  assertThat(entity.getValueList().get(i).getEntity()).isSameAs(entity);
                  assertThat(entity.getValueList().get(i).getIndex()).isEqualTo(i);
                }
              });

      template.validate(capabilities(true, true));
      var mixedConfig =
          PlannerTestUtils.buildSolverConfig(
                  TestdataMixedSolution.class,
                  TestdataMixedEntity.class,
                  TestdataMixedValue.class,
                  TestdataMixedOtherValue.class)
              .withEasyScoreCalculatorClass(TestdataMixedEasyScoreCalculator.class)
              .withPhases(new LocalSearchPhaseConfig());
      var problem = TestdataMixedSolution.generateUninitializedSolution(3, 8, 3);
      for (int i = 0; i < 3; i++) {
        problem.getEntityList().get(i).setBasicValue(problem.getOtherValueList().get(i));
        problem
            .getEntityList()
            .get(i)
            .setSecondBasicValue(problem.getOtherValueList().get((i + 1) % 3));
      }
      for (int i = 0; i < 8; i++)
        problem.getEntityList().get(i % 3).getValueList().add(problem.getValueList().get(i));
      SolutionManager.updateShadowVariables(problem);
      var mixed =
          SolverFactory.<TestdataMixedSolution>create(
                  runtimeConfig(
                      roundTrip(template.materialize(mixedConfig, template.defaultAssignment()))))
              .buildSolver()
              .solve(problem);
      assertThat(
              mixed.getEntityList().stream()
                  .flatMap(entity -> entity.getValueList().stream())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(mixed.getValueList());
      mixed
          .getEntityList()
          .forEach(
              entity -> {
                assertThat(entity.getBasicValue()).isNotNull();
                assertThat(entity.getSecondBasicValue()).isNotNull();
                assertThat(entity.getDeclarativeShadowVariableValue())
                    .isEqualTo(entity.getBasicValue().getStrength());
              });
    }
  }

  @Test
  void rejectsInactiveControlsAndMissingEligibilityOrScoreLayout() {
    var template = alns();
    var values = new LinkedHashMap<>(template.defaultAssignment().values());
    values.put("alns.sa.coolingRate", .9);
    assertThatThrownBy(() -> template.materialize(basicConfig(), ParameterAssignment.of(values)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inactive");
    assertThatThrownBy(() -> template.validate(new ProblemCapabilities(true, false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eligibility");
    var sa = localSearch(LocalSearchVariant.SIMULATED_ANNEALING);
    assertThatThrownBy(() -> sa.validate(new ProblemCapabilities(true, false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("score type");
  }

  @Test
  void rejectsInvalidNondefaultBranchAtTemplateRegistration() {
    var valid = alns();
    var broken =
        valid.searchSpace().dimensions().stream()
            .map(
                dimension ->
                    dimension.name().equals("alns.sa.coolingRate")
                        ? dimension.withActivation(
                            Activation.when("alns.acceptance", "HILL_CLIMBING"))
                        : dimension)
            .toList();
    var parameters =
        new TemplateParameters(ID, new SearchSpace("1", broken), valid.defaultAssignment());
    assertThatThrownBy(
            () ->
                ReferenceTemplates.alns(
                    parameters,
                    Map.of(
                        "greedy", portfolio(AlnsRepairOperatorType.GREEDY),
                        "regret", portfolio(AlnsRepairOperatorType.REGRET_K),
                        "randomized", portfolio(AlnsRepairOperatorType.RANDOMIZED_GREEDY)),
                    SimpleScore.of(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inactive/unknown controls");
  }

  @Test
  void approvedCallbacksRunAndReloadWithTheirDeclaredIdentity() {
    var callbackIdentity = new ComponentIdentity("test-callbacks", "1", "test-callback-artifact");
    for (var type :
        List.of(
            AlnsDestroyOperatorType.RELATEDNESS,
            AlnsDestroyOperatorType.WORST_REMOVAL,
            AlnsDestroyOperatorType.GROUP_REMOVAL)) {
      var destroy = new AlnsDestroyOperatorConfig().withId("callback-destroy").withType(type);
      switch (type) {
        case RELATEDNESS -> destroy.setRelatednessClass(TestCallbacks.class);
        case WORST_REMOVAL -> destroy.setRankingClass(TestCallbacks.class);
        case GROUP_REMOVAL -> destroy.setGroupingClass(TestCallbacks.class);
        default -> throw new AssertionError(type);
      }
      var repair =
          new AlnsRepairOperatorConfig().withId("greedy").withType(AlnsRepairOperatorType.GREEDY);
      assertThatThrownBy(
              () ->
                  new AlnsPortfolio(
                      ID,
                      List.of(destroy),
                      List.of(repair),
                      TemplateRequirements.allModels(),
                      "eligible",
                      Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("registered component identity");
      var portfolio =
          new AlnsPortfolio(
              ID,
              List.of(destroy),
              List.of(repair),
              TemplateRequirements.allModels(),
              "eligible",
              Map.of(TestCallbacks.class, callbackIdentity));
      var defaults =
          alnsAssignment(
              AlnsAcceptanceType.LATE_ACCEPTANCE,
              AlnsSelectionPolicyType.SEGMENTED_ROULETTE,
              "greedy",
              "COUNT");
      var template =
          ReferenceTemplates.alns(
              fixedParameters(defaults.values()), Map.of("greedy", portfolio), null);
      assertThatThrownBy(() -> template.validate(capabilities(true, false)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("callback");
      template.validate(
          capabilities(true, false)
              .withFeatures(Set.of("eligible", AlnsPortfolio.callbackFeature(callbackIdentity))));
      runBasic(roundTrip(template.materialize(basicConfig(), defaults)));
    }
  }

  public static final class TestCallbacks
      implements AlnsRelatedness<TestdataSolution>,
          AlnsRanking<TestdataSolution>,
          AlnsGrouping<TestdataSolution> {
    public TestCallbacks() {}

    @Override
    public double distance(
        TestdataSolution solution,
        AlnsTarget<TestdataSolution> left,
        AlnsTarget<TestdataSolution> right) {
      return 1.0;
    }

    @Override
    public double rank(TestdataSolution solution, AlnsTarget<TestdataSolution> target) {
      return 1.0;
    }

    @Override
    public Object groupKey(TestdataSolution solution, AlnsTarget<TestdataSolution> target) {
      return "one-group";
    }
  }

  @Test
  void rejectsUncoveredScopesAndListOnlyOperatorsOnBasicModels() {
    var destroy =
        new AlnsDestroyOperatorConfig()
            .withId("block")
            .withType(AlnsDestroyOperatorType.LIST_BLOCK)
            .withEntityClass(TestdataListEntity.class)
            .withVariableName("valueList");
    var repair =
        new AlnsRepairOperatorConfig().withId("greedy").withType(AlnsRepairOperatorType.GREEDY);
    var portfolio =
        new AlnsPortfolio(
            ID,
            List.of(destroy),
            List.of(repair),
            TemplateRequirements.allModels(),
            "eligible",
            Map.of());
    assertThatThrownBy(() -> portfolio.validate(capabilities(true, false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("LIST_BLOCK");
    repair.setEntityClass(TestdataEntity.class);
    assertThatThrownBy(
            () ->
                new AlnsPortfolio(
                    ID,
                    List.of(destroy),
                    List.of(repair),
                    TemplateRequirements.allModels(),
                    "eligible",
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("covering");
    destroy.setId("changed");
    assertThat(portfolio.destroys().getFirst().getId()).isEqualTo("block");
    portfolio.destroys().getFirst().setId("changed-copy");
    assertThat(portfolio.destroys().getFirst().getId()).isEqualTo("block");
  }

  @Test
  void preservesDeclaredCustomInitializationAndRejectsInterleavedInitialization() {
    var first =
        new CustomPhaseConfig()
            .withCustomProperties(new LinkedHashMap<>(Map.of("mode", "initialize")))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(7));
    var base =
        basicConfig()
            .withPhases(
                first, new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig());
    var pipeline =
        ReferenceTemplates.pipeline(
            ID,
            gls(),
            localSearch(LocalSearchVariant.LATE_ACCEPTANCE),
            new FixedDimension("pipeline.firstFraction", .25),
            .25);
    var materialized = pipeline.materialize(base, pipeline.defaultAssignment());
    assertThat(materialized.getPhaseConfigList()).hasSize(4);
    var preserved = (CustomPhaseConfig) materialized.getPhaseConfigList().getFirst();
    assertThat(preserved.getCustomProperties()).containsEntry("mode", "initialize");
    assertThat(preserved.getTerminationConfig().getStepCountLimit()).isEqualTo(7);
    assertThat(
            pipeline.approvedSearchPhaseLimits(
                materialized, Duration.ofSeconds(4), pipeline.defaultAssignment()))
        .isEqualTo(Map.of(2, Duration.ofSeconds(1)));
    preserved.getCustomProperties().put("mode", "changed");
    assertThat(first.getCustomProperties()).containsEntry("mode", "initialize");
    assertThatThrownBy(
            () ->
                pipeline.materialize(
                    base.withPhases(new LocalSearchPhaseConfig(), first),
                    pipeline.defaultAssignment()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Initialization phases");
  }

  @Test
  void movePortfoliosOwnNestedFactoryPropertyMaps() {
    var properties = new LinkedHashMap<>(Map.of("value", "original"));
    var factory = new MoveListFactoryConfig().withMoveListFactoryCustomProperties(properties);
    var portfolio =
        new MovePortfolio(
            ID, new UnionMoveSelectorConfig(List.of(factory)), TemplateRequirements.allModels());
    properties.put("value", "caller-changed");
    var first =
        (MoveListFactoryConfig)
            ((UnionMoveSelectorConfig) portfolio.selector()).getMoveSelectorList().getFirst();
    assertThat(first.getMoveListFactoryCustomProperties()).containsEntry("value", "original");
    first.getMoveListFactoryCustomProperties().put("value", "copy-changed");
    var second =
        (MoveListFactoryConfig)
            ((UnionMoveSelectorConfig) portfolio.selector()).getMoveSelectorList().getFirst();
    assertThat(second.getMoveListFactoryCustomProperties()).containsEntry("value", "original");
  }

  private static ConfigurationTemplate localSearch(LocalSearchVariant variant) {
    var values = new LinkedHashMap<String, Object>();
    values.put("ls.moves", "automatic");
    values.put("ls.acceptedCount", 2);
    switch (variant) {
      case LATE_ACCEPTANCE -> values.put("ls.la.history", 4);
      case TABU_SIZE -> values.put("ls.tabu.size", 2);
      case TABU_RATIO -> values.put("ls.tabu.ratio", .2);
      case SIMULATED_ANNEALING -> values.put("ls.sa.temperatureScale", 1.0);
      case GREAT_DELUGE_RATIO -> values.put("ls.gd.incrementRatio", .001);
      case GREAT_DELUGE_SCORE -> values.put("ls.gd.incrementScale", 1.0);
    }
    return ReferenceTemplates.localSearch(
        fixedParameters(values),
        variant,
        MOVES,
        variant == LocalSearchVariant.SIMULATED_ANNEALING
                || variant == LocalSearchVariant.GREAT_DELUGE_SCORE
            ? SimpleScore.of(10)
            : null);
  }

  private static ConfigurationTemplate gls() {
    return ReferenceTemplates.guidedLocalSearch(
        fixedParameters(
            Map.of(
                "gls.moves",
                "automatic",
                "gls.penaltyFactor",
                "0.125",
                "gls.sampleSize",
                8,
                "gls.focusStepLimit",
                4,
                "gls.focusPenaltyUpdateLimit",
                2,
                "gls.maxPenaltyUpdatesPerStep",
                8,
                "gls.excursionStepLimit",
                2,
                "gls.excursionRepairStepLimit",
                4,
                "gls.maxUnproductiveRounds",
                2)),
        MOVES);
  }

  private static ConfigurationTemplate alns() {
    var dimensions = new ArrayList<Dimension>();
    dimensions.add(
        new CategoricalDimension("alns.portfolio", List.of("greedy", "regret", "randomized")));
    dimensions.add(
        new CategoricalDimension(
            "alns.acceptance", Stream.of(AlnsAcceptanceType.values()).map(Enum::name).toList()));
    dimensions.add(
        new CategoricalDimension(
            "alns.selection",
            Stream.of(AlnsSelectionPolicyType.values()).map(Enum::name).toList()));
    dimensions.add(new CategoricalDimension("alns.destroy.mode", List.of("COUNT", "PERCENTAGE")));
    var baseline =
        alnsAssignment(
            AlnsAcceptanceType.LATE_ACCEPTANCE,
            AlnsSelectionPolicyType.SEGMENTED_ROULETTE,
            "greedy",
            "COUNT");
    dimensions.add(new FixedDimension("alns.repair.calculationLimit", 100));
    dimensions.add(
        new FixedDimension("alns.repair.regretK", 3, Activation.when("alns.portfolio", "regret")));
    dimensions.add(
        new FixedDimension("alns.repair.topK", 2, Activation.when("alns.portfolio", "randomized")));
    dimensions.add(
        new FixedDimension(
            "alns.la.history", 4, Activation.when("alns.acceptance", "LATE_ACCEPTANCE")));
    dimensions.add(
        new FixedDimension(
            "alns.sa.temperatureScale",
            1.0,
            Activation.when("alns.acceptance", "SIMULATED_ANNEALING")));
    dimensions.add(
        new FixedDimension(
            "alns.sa.coolingRate", .99, Activation.when("alns.acceptance", "SIMULATED_ANNEALING")));
    dimensions.add(
        new FixedDimension("alns.ucb.exploration", 1.0, Activation.when("alns.selection", "UCB")));
    dimensions.add(
        new FixedDimension(
            "alns.roulette.segmentLength",
            2,
            Activation.when("alns.selection", "SEGMENTED_ROULETTE")));
    dimensions.add(
        new FixedDimension(
            "alns.roulette.reactionFactor",
            .5,
            Activation.when("alns.selection", "SEGMENTED_ROULETTE")));
    dimensions.add(
        new FixedDimension(
            "alns.roulette.minimumWeight",
            .1,
            Activation.when("alns.selection", "SEGMENTED_ROULETTE")));
    dimensions.add(
        new FixedDimension(
            "alns.destroy.minimumCount", 1, Activation.when("alns.destroy.mode", "COUNT")));
    dimensions.add(
        new FixedDimension(
            "alns.destroy.maximumCount", 2, Activation.when("alns.destroy.mode", "COUNT")));
    dimensions.add(
        new FixedDimension(
            "alns.destroy.minimumPercentage",
            .2,
            Activation.when("alns.destroy.mode", "PERCENTAGE")));
    dimensions.add(
        new FixedDimension(
            "alns.destroy.maximumPercentage",
            .4,
            Activation.when("alns.destroy.mode", "PERCENTAGE")));
    var portfolios = new LinkedHashMap<String, AlnsPortfolio>();
    portfolios.put("greedy", portfolio(AlnsRepairOperatorType.GREEDY));
    portfolios.put("regret", portfolio(AlnsRepairOperatorType.REGRET_K));
    portfolios.put("randomized", portfolio(AlnsRepairOperatorType.RANDOMIZED_GREEDY));
    return ReferenceTemplates.alns(
        new TemplateParameters(ID, new SearchSpace("1", dimensions), baseline),
        portfolios,
        SimpleScore.of(10));
  }

  private static AlnsPortfolio portfolio(AlnsRepairOperatorType repair) {
    return new AlnsPortfolio(
        ID,
        List.of(
            new AlnsDestroyOperatorConfig()
                .withId("random")
                .withType(AlnsDestroyOperatorType.RANDOM)),
        List.of(new AlnsRepairOperatorConfig().withId("repair").withType(repair)),
        TemplateRequirements.allModels(),
        "eligible",
        Map.of());
  }

  private static ParameterAssignment alnsAssignment(
      AlnsAcceptanceType acceptance,
      AlnsSelectionPolicyType selection,
      String portfolio,
      String mode) {
    var values = new LinkedHashMap<String, Object>();
    values.put("alns.portfolio", portfolio);
    values.put("alns.acceptance", acceptance.name());
    values.put("alns.selection", selection.name());
    values.put("alns.destroy.mode", mode);
    values.put("alns.repair.calculationLimit", 100);
    if (portfolio.equals("regret")) values.put("alns.repair.regretK", 3);
    if (portfolio.equals("randomized")) values.put("alns.repair.topK", 2);
    if (acceptance == AlnsAcceptanceType.LATE_ACCEPTANCE) values.put("alns.la.history", 4);
    if (acceptance == AlnsAcceptanceType.SIMULATED_ANNEALING) {
      values.put("alns.sa.temperatureScale", 1.0);
      values.put("alns.sa.coolingRate", .99);
    }
    if (selection == AlnsSelectionPolicyType.UCB) values.put("alns.ucb.exploration", 1.0);
    else {
      values.put("alns.roulette.segmentLength", 2);
      values.put("alns.roulette.reactionFactor", .5);
      values.put("alns.roulette.minimumWeight", .1);
    }
    if (mode.equals("COUNT")) {
      values.put("alns.destroy.minimumCount", 1);
      values.put("alns.destroy.maximumCount", 2);
    } else {
      values.put("alns.destroy.minimumPercentage", .2);
      values.put("alns.destroy.maximumPercentage", .4);
    }
    return ParameterAssignment.of(values);
  }

  private static TemplateParameters fixedParameters(Map<String, Object> values) {
    var dimensions =
        values.entrySet().stream()
            .map(entry -> (Dimension) new FixedDimension(entry.getKey(), entry.getValue()))
            .toList();
    return new TemplateParameters(
        ID, new SearchSpace("1", dimensions), ParameterAssignment.of(values));
  }

  private static ProblemCapabilities capabilities(boolean basic, boolean list) {
    return new ProblemCapabilities(
        basic,
        list,
        false,
        false,
        SimpleScore.class.getName(),
        List.of("score"),
        Set.of("eligible"));
  }

  private static SolverConfig basicConfig() {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withPhases(new ConstructionHeuristicPhaseConfig());
  }

  private static void runBasic(SolverConfig config) {
    var result =
        SolverFactory.<TestdataSolution>create(runtimeConfig(config))
            .buildSolver()
            .solve(TestdataSolution.generateUninitializedSolution(3, 6));
    assertThat(result.getScore()).isNotNull();
    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
  }

  /** A short deterministic runtime exercise; study cap correctness is checked separately above. */
  private static SolverConfig runtimeConfig(SolverConfig config) {
    config.setTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(10)));
    config.getPhaseConfigList().stream()
        .filter(phase -> !(phase instanceof ConstructionHeuristicPhaseConfig))
        .forEach(
            phase -> phase.setTerminationConfig(new TerminationConfig().withStepCountLimit(3)));
    return config;
  }

  private static String xml(SolverConfig config) {
    var writer = new StringWriter();
    config.writeXml(writer);
    return writer.toString();
  }

  private static SolverConfig roundTrip(SolverConfig config) {
    return SolverConfig.createFromXmlReader(new StringReader(xml(config)));
  }
}
