package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Objects;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

public class SubListSwapMoveSelectorFactory<Solution_>
    extends AbstractMoveSelectorFactory<Solution_, SubListSwapMoveSelectorConfig> {

  public SubListSwapMoveSelectorFactory(SubListSwapMoveSelectorConfig moveSelectorConfig) {
    super(moveSelectorConfig);
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    if (!randomSelection) {
      throw new IllegalArgumentException(
          "The subListSwapMoveSelector (" + config + ") only supports random selection order.");
    }

    var selectionOrder = SelectionOrder.fromRandomSelectionBoolean(randomSelection);

    var onlyEntityDescriptor =
        getTheOnlyEntityDescriptorWithListVariable(configPolicy.getSolutionDescriptor());
    // We defined the entity class since there is a single entity descriptor that includes a list
    // variable
    var entitySelector =
        EntitySelectorFactory.<Solution_>create(
                new EntitySelectorConfig().withEntityClass(onlyEntityDescriptor.getEntityClass()))
            .buildEntitySelector(configPolicy, minimumCacheType, selectionOrder);

    var subListSelectorConfig =
        Objects.requireNonNullElseGet(
            config.getSubListSelectorConfig(), SubListSelectorConfig::new);
    var secondarySubListSelectorConfig =
        Objects.requireNonNullElse(
            config.getSecondarySubListSelectorConfig(), subListSelectorConfig);

    var leftSubListSelector =
        SubListSelectorFactory.<Solution_>create(subListSelectorConfig)
            .buildSubListSelector(configPolicy, entitySelector, minimumCacheType, selectionOrder);
    var rightSubListSelector =
        SubListSelectorFactory.<Solution_>create(secondarySubListSelectorConfig)
            .buildSubListSelector(configPolicy, entitySelector, minimumCacheType, selectionOrder);

    var selectReversingMoveToo =
        Objects.requireNonNullElse(config.getSelectReversingMoveToo(), true);

    return new RandomSubListSwapMoveSelector<>(
        leftSubListSelector, rightSubListSelector, selectReversingMoveToo);
  }
}
