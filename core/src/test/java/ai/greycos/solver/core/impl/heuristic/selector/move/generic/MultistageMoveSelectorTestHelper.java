package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

import org.jspecify.annotations.NonNull;

/** Test helper for multistage move selector tests. */
public final class MultistageMoveSelectorTestHelper {

  /** Test implementation of StageProvider for testing purposes. */
  public static class TestStageProvider<Solution_> implements StageProvider<Solution_> {

    private final List<MoveSelector<Solution_>> stages;
    private final int stageCount;

    public TestStageProvider(List<MoveSelector<Solution_>> stages, int stageCount) {
      this.stages = List.copyOf(stages);
      this.stageCount = stageCount;
    }

    @Override
    public @NonNull List<MoveSelector<Solution_>> createStages(
        @NonNull HeuristicConfigPolicy<Solution_> configPolicy) {
      return stages;
    }

    @Override
    public int getStageCount() {
      return stageCount;
    }
  }

  /** Creates a mock MoveSelector with specified countability. */
  public static <Solution_> MoveSelector<Solution_> mockCountableSelector(boolean countable) {
    MoveSelector<Solution_> selector = mock(MoveSelector.class);
    when(selector.isCountable()).thenReturn(countable);
    when(selector.isNeverEnding()).thenReturn(!countable);
    when(selector.getCacheType()).thenReturn(SelectionCacheType.JUST_IN_TIME);
    return selector;
  }

  /** Creates a mock MoveSelector with specified never-ending property. */
  public static <Solution_> MoveSelector<Solution_> mockNeverEndingSelector(boolean neverEnding) {
    MoveSelector<Solution_> selector = mock(MoveSelector.class);
    when(selector.isNeverEnding()).thenReturn(neverEnding);
    when(selector.isCountable()).thenReturn(!neverEnding);
    when(selector.getCacheType()).thenReturn(SelectionCacheType.JUST_IN_TIME);
    return selector;
  }

  /** Creates a mock MoveSelector with specified size. */
  public static <Solution_> MoveSelector<Solution_> mockMoveSelectorWithSize(long size) {
    MoveSelector<Solution_> selector = mock(MoveSelector.class);
    when(selector.isCountable()).thenReturn(true);
    when(selector.isNeverEnding()).thenReturn(false);
    when(selector.getSize()).thenReturn(size);
    when(selector.getCacheType()).thenReturn(SelectionCacheType.JUST_IN_TIME);
    return selector;
  }

  private MultistageMoveSelectorTestHelper() {}
}
