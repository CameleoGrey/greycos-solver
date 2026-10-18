package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfileResolver;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class MultipleListMoveSelectorFactoryTest {

  @Test
  void omittedNearbyProfileTargetsCoverEachListDeclaration() {
    var descriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveGlobal(
            descriptor, UniversalDistanceMeter.class);
    for (var listDescriptor : descriptor.getListVariableDescriptorList()) {
      assertThat(profiles.getProfiles(listDescriptor)).isNotEmpty();
    }
  }

  @Test
  void sameVariableNameOnDifferentOwnersRequiresOwnerTargetAndKeepsNearbyProfilesSeparate() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            SameNameSolution.class, FirstOwner.class, SecondOwner.class);
    var policy = buildHeuristicConfigPolicy(descriptor);
    var target = descriptor.resolveListVariableDescriptor(SecondOwner.class, "values");
    var config =
        new ListSwapMoveSelectorConfig()
            .withEntityClass(SecondOwner.class)
            .withValueSelectorConfig(new ValueSelectorConfig("values"));
    var selector =
        MoveSelectorFactory.<SameNameSolution>create(config)
            .buildMoveSelector(
                policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM, false);
    assertThat(((AbstractGenericListMoveSelector<?>) selector).listVariableDescriptor)
        .isSameAs(target);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MoveSelectorFactory.<SameNameSolution>create(
                        new ListSwapMoveSelectorConfig()
                            .withValueSelectorConfig(new ValueSelectorConfig("values")))
                    .buildMoveSelector(
                        policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM, false))
        .withMessageContaining("FirstOwner")
        .withMessageContaining("SecondOwner");
    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
            new LocalSearchPhaseConfig().withMoveSelectorConfig(config),
            descriptor,
            UniversalDistanceMeter.class,
            "test");
    assertThat(profiles.getProfiles(target)).hasSize(1);
    assertThat(
            profiles.getProfiles(
                descriptor.resolveListVariableDescriptor(FirstOwner.class, "values")))
        .isEmpty();
  }

  public static class UniversalDistanceMeter implements NearbyDistanceMeter<Object, Object> {
    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      return 0;
    }
  }

  @PlanningEntity
  public static class FirstOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRange")
    public List<String> values = new ArrayList<>();
  }

  @PlanningEntity
  public static class SecondOwner {
    @PlanningListVariable(valueRangeProviderRefs = "secondRange")
    public List<Integer> values = new ArrayList<>();
  }

  @PlanningSolution
  public static class SameNameSolution {
    @PlanningEntityCollectionProperty public List<FirstOwner> firstOwners = List.of();
    @PlanningEntityCollectionProperty public List<SecondOwner> secondOwners = List.of();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "firstRange")
    public List<String> firstValues = List.of();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "secondRange")
    public List<Integer> secondValues = List.of();

    @PlanningScore public SimpleScore score;
  }

  @Test
  void eachMoveFamilyRetainsItsDeclaration() {
    var solutionDescriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    for (var descriptor : solutionDescriptor.getListVariableDescriptorList()) {
      var name = descriptor.getVariableName();
      var configs =
          List.<MoveSelectorConfig<?>>of(
              new ListChangeMoveSelectorConfig()
                  .withValueSelectorConfig(new ValueSelectorConfig(name)),
              new ListSwapMoveSelectorConfig()
                  .withEntityClass(TestdataMultipleListEntity.class)
                  .withValueSelectorConfig(new ValueSelectorConfig(name)),
              new KOptListMoveSelectorConfig()
                  .withEntityClass(TestdataMultipleListEntity.class)
                  .withOriginSelectorConfig(new ValueSelectorConfig(name)),
              new ListRuinRecreateMoveSelectorConfig()
                  .withEntityClass(TestdataMultipleListEntity.class)
                  .withVariableName(name));
      for (var config : configs) {
        var selector =
            MoveSelectorFactory.<TestdataMultipleListSolution>create(config)
                .buildMoveSelector(
                    buildHeuristicConfigPolicy(solutionDescriptor)
                        .cloneBuilder()
                        .withInitializingScoreTrend(
                            InitializingScoreTrend.buildUniformTrend(
                                InitializingScoreTrendLevel.ANY, 1))
                        .build(),
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false);
        assertThat(((AbstractGenericListMoveSelector<?>) selector).listVariableDescriptor)
            .isSameAs(descriptor);
      }
      var subListConfig =
          new SubListSelectorConfig().withValueSelectorConfig(new ValueSelectorConfig(name));
      var change =
          (RandomSubListChangeMoveSelector<TestdataMultipleListSolution>)
              MoveSelectorFactory.<TestdataMultipleListSolution>create(
                      new SubListChangeMoveSelectorConfig()
                          .withSubListSelectorConfig(subListConfig))
                  .buildMoveSelector(
                      buildHeuristicConfigPolicy(solutionDescriptor),
                      SelectionCacheType.JUST_IN_TIME,
                      SelectionOrder.RANDOM,
                      false);
      assertThat(change.getSubListSelector().getVariableDescriptor()).isSameAs(descriptor);
      var swap =
          (RandomSubListSwapMoveSelector<TestdataMultipleListSolution>)
              MoveSelectorFactory.<TestdataMultipleListSolution>create(
                      new SubListSwapMoveSelectorConfig()
                          .withEntityClass(TestdataMultipleListEntity.class)
                          .withSubListSelectorConfig(subListConfig))
                  .buildMoveSelector(
                      buildHeuristicConfigPolicy(solutionDescriptor),
                      SelectionCacheType.JUST_IN_TIME,
                      SelectionOrder.RANDOM,
                      false);
      assertThat(swap.getLeftSubListSelector().getVariableDescriptor()).isSameAs(descriptor);
      assertThat(swap.getRightSubListSelector().getVariableDescriptor()).isSameAs(descriptor);
    }
  }

  @Test
  void omittedTargetRejectsAmbiguityForEveryMoveFamily() {
    for (var config :
        List.<MoveSelectorConfig<?>>of(
            new ListChangeMoveSelectorConfig(),
            new ListSwapMoveSelectorConfig(),
            new SubListChangeMoveSelectorConfig(),
            new SubListSwapMoveSelectorConfig(),
            new KOptListMoveSelectorConfig(),
            new ListRuinRecreateMoveSelectorConfig())) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  MoveSelectorFactory.<TestdataMultipleListSolution>create(config)
                      .buildMoveSelector(
                          buildHeuristicConfigPolicy(
                              TestdataMultipleListSolution.buildSolutionDescriptor()),
                          SelectionCacheType.JUST_IN_TIME,
                          SelectionOrder.RANDOM,
                          false))
          .withMessageContaining("firstValueList")
          .withMessageContaining("secondValueList");
    }
  }

  @Test
  void conflictingSourceAndDestinationAreRejected() {
    for (var config :
        List.<MoveSelectorConfig<?>>of(
            new ListChangeMoveSelectorConfig()
                .withValueSelectorConfig(new ValueSelectorConfig("firstValueList"))
                .withDestinationSelectorConfig(
                    new DestinationSelectorConfig()
                        .withValueSelectorConfig(new ValueSelectorConfig("secondValueList"))),
            new ListSwapMoveSelectorConfig()
                .withValueSelectorConfig(new ValueSelectorConfig("firstValueList"))
                .withSecondaryValueSelectorConfig(new ValueSelectorConfig("secondValueList")),
            new KOptListMoveSelectorConfig()
                .withOriginSelectorConfig(new ValueSelectorConfig("firstValueList"))
                .withValueSelectorConfig(new ValueSelectorConfig("secondValueList")),
            new SubListSwapMoveSelectorConfig()
                .withSubListSelectorConfig(
                    new SubListSelectorConfig()
                        .withValueSelectorConfig(new ValueSelectorConfig("firstValueList")))
                .withSecondarySubListSelectorConfig(
                    new SubListSelectorConfig()
                        .withValueSelectorConfig(new ValueSelectorConfig("secondValueList"))))) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  MoveSelectorFactory.<TestdataMultipleListSolution>create(config)
                      .buildMoveSelector(
                          buildHeuristicConfigPolicy(
                              TestdataMultipleListSolution.buildSolutionDescriptor()),
                          SelectionCacheType.JUST_IN_TIME,
                          SelectionOrder.RANDOM,
                          false))
          .withMessageContaining("same planning list variable");
    }
  }

  @Test
  void mimicReferenceRetainsDeclarationWithoutVariableName() {
    var descriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var policy = buildHeuristicConfigPolicy(descriptor);
    var target =
        descriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, "secondValueList");
    ValueSelectorFactory.<TestdataMultipleListSolution>create(
            new ValueSelectorConfig("secondValueList").withId("recorded"))
        .buildValueSelector(
            policy,
            target.getEntityDescriptor(),
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.ORIGINAL);
    var selector =
        (ListChangeMoveSelector<TestdataMultipleListSolution>)
            MoveSelectorFactory.<TestdataMultipleListSolution>create(
                    new ListChangeMoveSelectorConfig()
                        .withValueSelectorConfig(
                            new ValueSelectorConfig().withMimicSelectorRef("recorded")))
                .buildMoveSelector(
                    policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL, false);
    assertThat(selector.getListVariableDescriptor()).isSameAs(target);
  }
}
