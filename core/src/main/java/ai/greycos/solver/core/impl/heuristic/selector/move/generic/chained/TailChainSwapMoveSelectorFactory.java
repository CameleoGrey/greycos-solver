package ai.greycos.solver.core.impl.heuristic.selector.move.generic.chained;

import java.util.Objects;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.chained.TailChainSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import ai.greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.ValueSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;

public class TailChainSwapMoveSelectorFactory<Solution_>
    extends AbstractMoveSelectorFactory<Solution_, TailChainSwapMoveSelectorConfig> {

  public TailChainSwapMoveSelectorFactory(TailChainSwapMoveSelectorConfig moveSelectorConfig) {
    super(moveSelectorConfig);
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    EntitySelectorConfig entitySelectorConfig =
        Objects.requireNonNullElseGet(config.getEntitySelectorConfig(), EntitySelectorConfig::new);
    ValueSelectorConfig valueSelectorConfig =
        Objects.requireNonNullElseGet(config.getValueSelectorConfig(), ValueSelectorConfig::new);
    SelectionOrder selectionOrder = SelectionOrder.fromRandomSelectionBoolean(randomSelection);
    EntitySelector<Solution_> entitySelector =
        EntitySelectorFactory.<Solution_>create(entitySelectorConfig)
            .buildEntitySelector(configPolicy, minimumCacheType, selectionOrder);
    ValueSelector<Solution_> valueSelector =
        ValueSelectorFactory.<Solution_>create(valueSelectorConfig)
            .buildValueSelector(
                configPolicy,
                entitySelector.getEntityDescriptor(),
                minimumCacheType,
                selectionOrder);
    return new TailChainSwapMoveSelector<>(entitySelector, valueSelector, randomSelection);
  }
}
