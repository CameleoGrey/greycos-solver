package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.mixed.multientity.TestdataMixedMultiEntitySolution;

import org.junit.jupiter.api.Test;

class SubListChangeMoveSelectorFactoryTest {

  @Test
  void buildMoveSelector() {
    var config = new SubListChangeMoveSelectorConfig();
    var moveSelectorFactory = new SubListChangeMoveSelectorFactory<TestdataListSolution>(config);

    var heuristicConfigPolicy =
        buildHeuristicConfigPolicy(TestdataListSolution.buildSolutionDescriptor());

    var selector =
        (RandomSubListChangeMoveSelector<TestdataListSolution>)
            moveSelectorFactory.buildMoveSelector(
                heuristicConfigPolicy,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);

    assertThat(selector.isCountable()).isTrue();
    assertThat(selector.isNeverEnding()).isTrue();
    assertThat(selector.isSelectReversingMoveToo()).isTrue();
  }

  @Test
  void buildMoveSelectorMultiEntity() {
    var config = new SubListChangeMoveSelectorConfig();
    var moveSelectorFactory =
        new SubListChangeMoveSelectorFactory<TestdataMixedMultiEntitySolution>(config);

    var heuristicConfigPolicy =
        buildHeuristicConfigPolicy(TestdataMixedMultiEntitySolution.buildSolutionDescriptor());

    var selector =
        (RandomSubListChangeMoveSelector<TestdataMixedMultiEntitySolution>)
            moveSelectorFactory.buildMoveSelector(
                heuristicConfigPolicy,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);

    assertThat(selector.isCountable()).isTrue();
    assertThat(selector.isNeverEnding()).isTrue();
    assertThat(selector.isSelectReversingMoveToo()).isTrue();
  }

  @Test
  void disableSelectReversingMoveToo() {
    var config = new SubListChangeMoveSelectorConfig();
    config.setSelectReversingMoveToo(false);
    var moveSelectorFactory = new SubListChangeMoveSelectorFactory<TestdataListSolution>(config);

    var heuristicConfigPolicy =
        buildHeuristicConfigPolicy(TestdataListSolution.buildSolutionDescriptor());

    var selector =
        (RandomSubListChangeMoveSelector<TestdataListSolution>)
            moveSelectorFactory.buildMoveSelector(
                heuristicConfigPolicy,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                false);

    assertThat(selector.isSelectReversingMoveToo()).isFalse();
  }

  @Test
  void unfoldingFailsIfThereIsNoListVariable() {
    var config = new SubListChangeMoveSelectorConfig();
    var moveSelectorFactory = new SubListChangeMoveSelectorFactory<TestdataSolution>(config);

    var heuristicConfigPolicy =
        buildHeuristicConfigPolicy(TestdataSolution.buildSolutionDescriptor());

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                moveSelectorFactory.buildMoveSelector(
                    heuristicConfigPolicy,
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false))
        .withMessageContaining("it cannot be deduced automatically");
  }

  @Test
  void explicitConfigMustUseListVariable() {
    var config =
        new SubListChangeMoveSelectorConfig()
            .withSubListSelectorConfig(
                new SubListSelectorConfig()
                    .withValueSelectorConfig(new ValueSelectorConfig("value")))
            .withDestinationSelectorConfig(
                new DestinationSelectorConfig()
                    .withEntitySelectorConfig(new EntitySelectorConfig(TestdataEntity.class))
                    .withValueSelectorConfig(new ValueSelectorConfig("value")));

    var moveSelectorFactory = new SubListChangeMoveSelectorFactory<TestdataSolution>(config);

    var heuristicConfigPolicy =
        buildHeuristicConfigPolicy(TestdataSolution.buildSolutionDescriptor());

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                moveSelectorFactory.buildMoveSelector(
                    heuristicConfigPolicy,
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false))
        .withMessageContaining("not a planning list variable");
  }
}
