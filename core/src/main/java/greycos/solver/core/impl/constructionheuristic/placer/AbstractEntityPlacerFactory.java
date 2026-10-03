package greycos.solver.core.impl.constructionheuristic.placer;

import greycos.solver.core.config.constructionheuristic.placer.EntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.impl.AbstractFromConfigFactory;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;

abstract class AbstractEntityPlacerFactory<
        Solution_, EntityPlacerConfig_ extends EntityPlacerConfig<EntityPlacerConfig_>>
    extends AbstractFromConfigFactory<Solution_, EntityPlacerConfig_>
    implements EntityPlacerFactory<Solution_> {

  protected AbstractEntityPlacerFactory(EntityPlacerConfig_ placerConfig) {
    super(placerConfig);
  }

  protected ChangeMoveSelectorConfig buildChangeMoveSelectorConfig(
      HeuristicConfigPolicy<Solution_> configPolicy,
      String entitySelectorConfigId,
      GenuineVariableDescriptor<Solution_> variableDescriptor) {
    return QueuedEntityPlacerFactory.buildChangeMoveSelectorConfig(
        configPolicy.getValueSorterManner(), entitySelectorConfigId, variableDescriptor);
  }
}
