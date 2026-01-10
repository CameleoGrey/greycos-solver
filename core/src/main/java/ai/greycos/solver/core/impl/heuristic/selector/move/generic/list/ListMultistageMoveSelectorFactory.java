package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.List;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.StageProvider;

import org.jspecify.annotations.NonNull;

/**
 * Factory for creating {@link ListMultistageMoveSelector}.
 *
 * <p>This factory follows same pattern as basic {@link MultistageMoveSelectorFactory} but is
 * specialized for list planning variables.
 *
 * <p>The factory instantiates the user-provided {@link StageProvider} and uses it to create move
 * selectors for each stage.
 *
 * @param <Solution_> solution type
 */
public class ListMultistageMoveSelectorFactory<Solution_>
    extends AbstractMoveSelectorFactory<Solution_, ListMultistageMoveSelectorConfig> {

  public ListMultistageMoveSelectorFactory(@NonNull ListMultistageMoveSelectorConfig config) {
    super(config);
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      @NonNull HeuristicConfigPolicy<Solution_> configPolicy,
      @NonNull SelectionCacheType minimumCacheType,
      boolean randomSelection) {

    Class<?> stageProviderClass = config.getStageProviderClass();
    if (stageProviderClass == null) {
      throw new IllegalArgumentException(
          "ListMultistageMoveSelectorConfig must specify a stageProviderClass");
    }

    @SuppressWarnings("unchecked")
    StageProvider<Solution_> stageProvider =
        (StageProvider<Solution_>)
            ConfigUtils.newInstance(config, "stageProviderClass", stageProviderClass);

    List<MoveSelector<Solution_>> stageSelectors = stageProvider.createStages(configPolicy);

    if (stageSelectors.isEmpty()) {
      throw new IllegalStateException(
          "StageProvider "
              + stageProviderClass.getName()
              + " returned empty stage list. At least one stage is required.");
    }

    int expectedStageCount = stageProvider.getStageCount();
    if (expectedStageCount != stageSelectors.size()) {
      throw new IllegalStateException(
          "StageProvider "
              + stageProviderClass.getName()
              + " getStageCount() returned "
              + expectedStageCount
              + " but createStages() returned "
              + stageSelectors.size()
              + " stages. These must match.");
    }

    if (expectedStageCount < 1) {
      throw new IllegalStateException(
          "StageProvider "
              + stageProviderClass.getName()
              + " getStageCount() returned "
              + expectedStageCount
              + ". Must return at least 1.");
    }

    return new ListMultistageMoveSelector<>(stageProvider, stageSelectors, randomSelection);
  }

  @Override
  protected boolean isBaseInherentlyCached() {
    return false;
  }
}
