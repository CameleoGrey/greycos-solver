package greycos.solver.core.impl.heuristic.selector.move.factory;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

public class MoveIteratorFactoryFactory<Solution_>
    extends AbstractMoveSelectorFactory<Solution_, MoveIteratorFactoryConfig> {

  public MoveIteratorFactoryFactory(MoveIteratorFactoryConfig moveSelectorConfig) {
    super(moveSelectorConfig);
  }

  @Override
  public MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    var moveIteratorFactoryClass = config.getMoveIteratorFactoryClass();
    if (moveIteratorFactoryClass == null) {
      throw new IllegalArgumentException(
          "The moveIteratorFactoryConfig (%s) lacks a moveListFactoryClass (%s)."
              .formatted(config, moveIteratorFactoryClass));
    }
    var moveIteratorFactory =
        ConfigUtils.newInstance(config, "moveIteratorFactoryClass", moveIteratorFactoryClass);
    ConfigUtils.applyCustomProperties(
        moveIteratorFactory,
        "moveIteratorFactoryClass",
        config.getMoveIteratorFactoryCustomProperties(),
        "moveIteratorFactoryCustomProperties");
    return new MoveIteratorFactoryToMoveSelectorBridge<>(moveIteratorFactory, randomSelection);
  }
}
