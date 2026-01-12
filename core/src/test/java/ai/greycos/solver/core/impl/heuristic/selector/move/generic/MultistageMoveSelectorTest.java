package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

class MultistageMoveSelectorTest {

  @Test
  void sizeCalculation() {
    MoveSelector<TestdataSolution> s1 =
        MultistageMoveSelectorTestHelper.mockMoveSelectorWithSize(3);
    MoveSelector<TestdataSolution> s2 =
        MultistageMoveSelectorTestHelper.mockMoveSelectorWithSize(4);
    MoveSelector<TestdataSolution> s3 =
        MultistageMoveSelectorTestHelper.mockMoveSelectorWithSize(2);

    var selector = new MultistageMoveSelector<>(createTestProvider(3), List.of(s1, s2, s3), false);

    assertThat(selector.getSize()).isEqualTo(24); // 3 * 4 * 2
  }

  @Test
  void sizeWithEmptyStage() {
    MoveSelector<TestdataSolution> s1 =
        MultistageMoveSelectorTestHelper.mockMoveSelectorWithSize(3);
    MoveSelector<TestdataSolution> s2 =
        MultistageMoveSelectorTestHelper.mockMoveSelectorWithSize(0);

    var selector = new MultistageMoveSelector<>(createTestProvider(2), List.of(s1, s2), false);

    assertThat(selector.getSize()).isZero();
  }

  @Test
  void countability() {
    MoveSelector<TestdataSolution> countable1 =
        MultistageMoveSelectorTestHelper.mockCountableSelector(true);
    MoveSelector<TestdataSolution> countable2 =
        MultistageMoveSelectorTestHelper.mockCountableSelector(true);
    MoveSelector<TestdataSolution> uncountable =
        MultistageMoveSelectorTestHelper.mockCountableSelector(false);

    var allCountable =
        new MultistageMoveSelector<>(createTestProvider(2), List.of(countable1, countable2), false);

    var oneUncountable =
        new MultistageMoveSelector<>(
            createTestProvider(2), List.of(countable1, uncountable), false);

    assertThat(allCountable.isCountable()).isTrue();
    assertThat(oneUncountable.isCountable()).isFalse();
  }

  @Test
  void neverEnding() {
    MoveSelector<TestdataSolution> finite1 =
        MultistageMoveSelectorTestHelper.mockNeverEndingSelector(false);
    MoveSelector<TestdataSolution> finite2 =
        MultistageMoveSelectorTestHelper.mockNeverEndingSelector(false);
    MoveSelector<TestdataSolution> infinite =
        MultistageMoveSelectorTestHelper.mockNeverEndingSelector(true);

    var allFinite =
        new MultistageMoveSelector<>(createTestProvider(2), List.of(finite1, finite2), false);

    var oneInfinite =
        new MultistageMoveSelector<>(createTestProvider(2), List.of(finite1, infinite), false);

    assertThat(allFinite.isNeverEnding()).isFalse();
    assertThat(oneInfinite.isNeverEnding()).isTrue();
  }

  @Test
  void cachingSupport() {
    MoveSelector<TestdataSolution> s1 =
        MultistageMoveSelectorTestHelper.mockMoveSelectorWithSize(2);
    MoveSelector<TestdataSolution> s2 =
        MultistageMoveSelectorTestHelper.mockMoveSelectorWithSize(3);

    var selector = new MultistageMoveSelector<>(createTestProvider(2), List.of(s1, s2), false);

    assertThat(selector.supportsPhaseAndSolverCaching()).isFalse();
  }

  @Test
  void phaseLifecyclePropagation() {
    MoveSelector<TestdataSolution> s1 = mock(MoveSelector.class);
    MoveSelector<TestdataSolution> s2 = mock(MoveSelector.class);

    var selector = new MultistageMoveSelector<>(createTestProvider(2), List.of(s1, s2), false);

    SolverScope<TestdataSolution> solverScope = SelectorTestUtils.solvingStarted(selector);

    verify(s1).solvingStarted(any(SolverScope.class));
    verify(s2).solvingStarted(any(SolverScope.class));

    AbstractPhaseScope<TestdataSolution> phaseScope =
        SelectorTestUtils.phaseStarted(selector, solverScope);

    verify(s1).phaseStarted(any(AbstractPhaseScope.class));
    verify(s2).phaseStarted(any(AbstractPhaseScope.class));

    SelectorTestUtils.stepStarted(selector, phaseScope);

    verify(s1).stepStarted(any());
    verify(s2).stepStarted(any());
  }

  private MultistageMoveSelectorTestHelper.TestStageProvider<TestdataSolution> createTestProvider(
      int stageCount) {
    return new MultistageMoveSelectorTestHelper.TestStageProvider<>(List.of(), stageCount);
  }
}
