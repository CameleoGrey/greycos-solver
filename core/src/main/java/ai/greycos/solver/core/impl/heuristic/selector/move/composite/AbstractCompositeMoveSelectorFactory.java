package ai.greycos.solver.core.impl.heuristic.selector.move.composite;

import java.util.List;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;

abstract class AbstractCompositeMoveSelectorFactory<
        Solution_, MoveSelectorConfig_ extends MoveSelectorConfig<MoveSelectorConfig_>>
    extends AbstractMoveSelectorFactory<Solution_, MoveSelectorConfig_> {

  protected AbstractCompositeMoveSelectorFactory(MoveSelectorConfig_ moveSelectorConfig) {
    super(moveSelectorConfig);
  }

  protected List<MoveSelector<Solution_>> buildInnerMoveSelectors(
      List<MoveSelectorConfig> innerMoveSelectorList,
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    return innerMoveSelectorList.stream()
        .map(
            moveSelectorConfig -> {
              AbstractMoveSelectorFactory<Solution_, ?> moveSelectorFactory =
                  MoveSelectorFactory.create(moveSelectorConfig);
              var selectionOrder = SelectionOrder.fromRandomSelectionBoolean(randomSelection);
              return moveSelectorFactory.buildMoveSelector(
                  configPolicy, minimumCacheType, selectionOrder, false);
            })
        .toList();
  }
}
