package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Objects;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.ListVariableSelectorSupport;
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

    var subListSelectorConfig =
        Objects.requireNonNullElseGet(config.getSubListSelectorConfig(), SubListSelectorConfig::new)
            .copyConfig();
    var secondarySubListSelectorConfig =
        Objects.requireNonNullElse(
                config.getSecondarySubListSelectorConfig(), subListSelectorConfig)
            .copyConfig();
    var descriptor =
        ListVariableSelectorSupport.resolve(
            configPolicy,
            config.getEntityClass(),
            subListSelectorConfig,
            secondarySubListSelectorConfig);
    var entitySelector =
        EntitySelectorFactory.<Solution_>create(
                new EntitySelectorConfig()
                    .withEntityClass(descriptor.getEntityDescriptor().getEntityClass()))
            .buildEntitySelector(configPolicy, minimumCacheType, selectionOrder);
    for (var subListConfig :
        new SubListSelectorConfig[] {subListSelectorConfig, secondarySubListSelectorConfig}) {
      if (subListConfig.getMimicSelectorRef() == null) {
        var valueConfig =
            Objects.requireNonNullElseGet(
                subListConfig.getValueSelectorConfig(), ValueSelectorConfig::new);
        if (valueConfig.getMimicSelectorRef() == null) {
          valueConfig.setVariableName(descriptor.getVariableName());
        }
        subListConfig.setValueSelectorConfig(valueConfig);
      }
    }

    var leftSubListSelector =
        SubListSelectorFactory.<Solution_>create(subListSelectorConfig)
            .buildOriginSubListSelector(
                configPolicy, entitySelector, minimumCacheType, selectionOrder);
    var rightSubListSelector =
        SubListSelectorFactory.<Solution_>create(secondarySubListSelectorConfig)
            .buildSubListSelector(configPolicy, entitySelector, minimumCacheType, selectionOrder);

    var selectReversingMoveToo =
        Objects.requireNonNullElse(config.getSelectReversingMoveToo(), true);

    return new RandomSubListSwapMoveSelector<>(
        leftSubListSelector,
        rightSubListSelector,
        selectReversingMoveToo,
        secondarySubListSelectorConfig.getNearbySelectionConfig() != null);
  }
}
