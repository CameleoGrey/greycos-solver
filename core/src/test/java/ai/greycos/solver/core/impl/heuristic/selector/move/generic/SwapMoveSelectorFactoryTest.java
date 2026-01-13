package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import ai.greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelector;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.ListSwapMoveSelector;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.multientity.TestdataHerdEntity;
import ai.greycos.solver.core.testcotwin.multientity.TestdataLeadEntity;
import ai.greycos.solver.core.testcotwin.multientity.TestdataMultiEntitySolution;
import ai.greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;

import org.junit.jupiter.api.Test;

class SwapMoveSelectorFactoryTest {

  @Test
  void deducibleMultiVar() {
    SolutionDescriptor solutionDescriptor = TestdataMultiVarSolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig =
        new SwapMoveSelectorConfig().withVariableNameIncludes("secondaryValue");
    MoveSelector moveSelector =
        MoveSelectorFactory.create(moveSelectorConfig)
            .buildMoveSelector(
                buildHeuristicConfigPolicy(solutionDescriptor),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);
    assertThat(moveSelector).isInstanceOf(SwapMoveSelector.class);
  }

  @Test
  void undeducibleMultiVar() {
    SolutionDescriptor solutionDescriptor = TestdataMultiVarSolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig =
        new SwapMoveSelectorConfig().withVariableNameIncludes("nonExistingValue");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MoveSelectorFactory.create(moveSelectorConfig)
                    .buildMoveSelector(
                        buildHeuristicConfigPolicy(solutionDescriptor),
                        SelectionCacheType.JUST_IN_TIME,
                        SelectionOrder.RANDOM,
                        false));
  }

  @Test
  void unfoldedMultiVar() {
    SolutionDescriptor solutionDescriptor = TestdataMultiVarSolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig = new SwapMoveSelectorConfig();
    MoveSelector moveSelector =
        MoveSelectorFactory.create(moveSelectorConfig)
            .buildMoveSelector(
                buildHeuristicConfigPolicy(solutionDescriptor),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);
    assertThat(moveSelector).isInstanceOf(SwapMoveSelector.class);
  }

  @Test
  void deducibleMultiEntity() {
    SolutionDescriptor solutionDescriptor = TestdataMultiEntitySolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig =
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataHerdEntity.class));
    MoveSelector moveSelector =
        MoveSelectorFactory.create(moveSelectorConfig)
            .buildMoveSelector(
                buildHeuristicConfigPolicy(solutionDescriptor),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);
    assertThat(moveSelector).isInstanceOf(SwapMoveSelector.class);
  }

  @Test
  void undeducibleMultiEntity() {
    SolutionDescriptor solutionDescriptor = TestdataMultiEntitySolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig =
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataEntity.class));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MoveSelectorFactory.create(moveSelectorConfig)
                    .buildMoveSelector(
                        buildHeuristicConfigPolicy(solutionDescriptor),
                        SelectionCacheType.JUST_IN_TIME,
                        SelectionOrder.RANDOM,
                        false));
  }

  @Test
  void unfoldedMultiEntity() {
    SolutionDescriptor solutionDescriptor = TestdataMultiEntitySolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig = new SwapMoveSelectorConfig();
    MoveSelector moveSelector =
        MoveSelectorFactory.create(moveSelectorConfig)
            .buildMoveSelector(
                buildHeuristicConfigPolicy(solutionDescriptor),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);
    assertThat(moveSelector).isInstanceOf(UnionMoveSelector.class);
    assertThat(((UnionMoveSelector) moveSelector).getChildMoveSelectorList()).hasSize(2);
  }

  @Test
  void deducibleMultiEntityWithSecondaryEntitySelector() {
    SolutionDescriptor solutionDescriptor = TestdataMultiEntitySolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig =
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataHerdEntity.class))
            .withSecondaryEntitySelectorConfig(new EntitySelectorConfig(TestdataHerdEntity.class));
    MoveSelector moveSelector =
        MoveSelectorFactory.create(moveSelectorConfig)
            .buildMoveSelector(
                buildHeuristicConfigPolicy(solutionDescriptor),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);
    assertThat(moveSelector).isInstanceOf(SwapMoveSelector.class);
  }

  @Test
  void unswappableMultiEntityWithSecondaryEntitySelector() {
    SolutionDescriptor solutionDescriptor = TestdataMultiEntitySolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig =
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataLeadEntity.class))
            .withSecondaryEntitySelectorConfig(new EntitySelectorConfig(TestdataHerdEntity.class));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MoveSelectorFactory.create(moveSelectorConfig)
                    .buildMoveSelector(
                        buildHeuristicConfigPolicy(solutionDescriptor),
                        SelectionCacheType.JUST_IN_TIME,
                        SelectionOrder.RANDOM,
                        false));
  }

  @Test
  void unfoldedMultiEntityWithSecondaryEntitySelector() {
    SolutionDescriptor solutionDescriptor = TestdataMultiEntitySolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig =
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig())
            .withSecondaryEntitySelectorConfig(new EntitySelectorConfig());
    MoveSelector moveSelector =
        MoveSelectorFactory.create(moveSelectorConfig)
            .buildMoveSelector(
                buildHeuristicConfigPolicy(solutionDescriptor),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);
    assertThat(moveSelector).isInstanceOf(UnionMoveSelector.class);
    assertThat(((UnionMoveSelector) moveSelector).getChildMoveSelectorList()).hasSize(2);
  }

  // ************************************************************************
  // List variable compatibility section
  // ************************************************************************

  @Test
  void unfoldEmptyIntoListSwapMoveSelectorConfig() {
    SolutionDescriptor<TestdataListSolution> solutionDescriptor =
        TestdataListSolution.buildSolutionDescriptor();
    SwapMoveSelectorConfig moveSelectorConfig = new SwapMoveSelectorConfig();
    MoveSelector<TestdataListSolution> moveSelector =
        MoveSelectorFactory.<TestdataListSolution>create(moveSelectorConfig)
            .buildMoveSelector(
                buildHeuristicConfigPolicy(solutionDescriptor),
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);
    assertThat(moveSelector).isInstanceOf(ListSwapMoveSelector.class);
  }

  @Test
  void unfoldConfiguredIntoListSwapMoveSelectorConfig() {
    SolutionDescriptor<TestdataListSolution> solutionDescriptor =
        TestdataListSolution.buildSolutionDescriptor();

    SelectionCacheType moveSelectorCacheType = SelectionCacheType.PHASE;
    long selectedCountLimit = 200;
    SwapMoveSelectorConfig moveSelectorConfig =
        new SwapMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataListEntity.class))
            .withCacheType(moveSelectorCacheType)
            .withSelectedCountLimit(selectedCountLimit);

    SwapMoveSelectorFactory<TestdataListSolution> swapMoveSelectorFactory =
        (SwapMoveSelectorFactory<TestdataListSolution>)
            MoveSelectorFactory.<TestdataListSolution>create(moveSelectorConfig);

    MoveSelectorConfig<?> unfoldedMoveSelectorConfig =
        swapMoveSelectorFactory.buildUnfoldedMoveSelectorConfig(
            buildHeuristicConfigPolicy(solutionDescriptor));

    assertThat(unfoldedMoveSelectorConfig).isExactlyInstanceOf(ListSwapMoveSelectorConfig.class);
    ListSwapMoveSelectorConfig listSwapMoveSelectorConfig =
        (ListSwapMoveSelectorConfig) unfoldedMoveSelectorConfig;

    assertThat(listSwapMoveSelectorConfig.getValueSelectorConfig().getVariableName())
        .isEqualTo("valueList");
    assertThat(listSwapMoveSelectorConfig.getCacheType()).isEqualTo(moveSelectorCacheType);
    assertThat(listSwapMoveSelectorConfig.getSelectedCountLimit()).isEqualTo(selectedCountLimit);
  }
}
