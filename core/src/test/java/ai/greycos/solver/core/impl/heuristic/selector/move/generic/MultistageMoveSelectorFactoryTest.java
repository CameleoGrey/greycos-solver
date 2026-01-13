package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class MultistageMoveSelectorFactoryTest {

  @Test
  void buildNullStageProviderClass() {
    var config = new MultistageMoveSelectorConfig();
    // Don't set stageProviderClass

    var factory = new MultistageMoveSelectorFactory<TestdataSolution>(config);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                factory.buildMoveSelector(
                    buildHeuristicConfigPolicy(),
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false))
        .withMessageContaining("stageProviderClass");
  }

  @Test
  void buildEmptyStageList() {
    var config = new MultistageMoveSelectorConfig();
    config.setStageProviderClass(EmptyStageProvider.class);

    var factory = new MultistageMoveSelectorFactory<TestdataSolution>(config);

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                factory.buildMoveSelector(
                    buildHeuristicConfigPolicy(),
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false))
        .withMessageContaining("At least one stage");
  }

  @Test
  void buildStageCountMismatch() {
    var config = new MultistageMoveSelectorConfig();
    config.setStageProviderClass(StageCountMismatchProvider.class);

    var factory = new MultistageMoveSelectorFactory<TestdataSolution>(config);

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                factory.buildMoveSelector(
                    buildHeuristicConfigPolicy(),
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false))
        .withMessageContaining("getStageCount() returned 3 but createStages() returned 2");
  }

  @Test
  void buildInvalidStageCount() {
    var config = new MultistageMoveSelectorConfig();
    config.setStageProviderClass(ZeroStageCountProvider.class);

    var factory = new MultistageMoveSelectorFactory<TestdataSolution>(config);

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                factory.buildMoveSelector(
                    buildHeuristicConfigPolicy(),
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM,
                    false))
        .withMessageContaining("getStageCount() returned 0 but createStages() returned 1");
  }

  @Test
  void buildValidSelector() {
    var config = new MultistageMoveSelectorConfig();
    config.setStageProviderClass(ValidTwoStageProvider.class);

    var factory = new MultistageMoveSelectorFactory<TestdataSolution>(config);

    @SuppressWarnings("unchecked")
    MoveSelector<TestdataSolution> sequential =
        factory.buildMoveSelector(
            buildHeuristicConfigPolicy(),
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.ORIGINAL,
            false);

    assertThat(sequential).isInstanceOf(MultistageMoveSelector.class);
    MultistageMoveSelector<TestdataSolution> multistageSequential =
        (MultistageMoveSelector<TestdataSolution>) sequential;
    assertThat(multistageSequential.getStageSelectors()).hasSize(2);

    @SuppressWarnings("unchecked")
    MoveSelector<TestdataSolution> random =
        factory.buildMoveSelector(
            buildHeuristicConfigPolicy(),
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.RANDOM,
            false);

    assertThat(random).isInstanceOf(MultistageMoveSelector.class);
    MultistageMoveSelector<TestdataSolution> multistageRandom =
        (MultistageMoveSelector<TestdataSolution>) random;
    assertThat(multistageRandom.getStageSelectors()).hasSize(2);
  }

  // Test StageProviders

  public static class EmptyStageProvider
      extends MultistageMoveSelectorTestHelper.TestStageProvider<TestdataSolution> {
    public EmptyStageProvider() {
      super(List.of(), 0);
    }
  }

  public static class StageCountMismatchProvider
      extends MultistageMoveSelectorTestHelper.TestStageProvider<TestdataSolution> {
    public StageCountMismatchProvider() {
      // Create 2 selectors but declare count as 3
      super(List.of(SelectorTestUtils.mockMoveSelector(), SelectorTestUtils.mockMoveSelector()), 3);
    }
  }

  public static class ZeroStageCountProvider
      extends MultistageMoveSelectorTestHelper.TestStageProvider<TestdataSolution> {
    public ZeroStageCountProvider() {
      // Create 1 selector but declare count as 0
      super(List.of(SelectorTestUtils.mockMoveSelector()), 0);
    }
  }

  public static class ValidTwoStageProvider
      extends MultistageMoveSelectorTestHelper.TestStageProvider<TestdataSolution> {
    public ValidTwoStageProvider() {
      super(List.of(SelectorTestUtils.mockMoveSelector(), SelectorTestUtils.mockMoveSelector()), 2);
    }
  }
}
