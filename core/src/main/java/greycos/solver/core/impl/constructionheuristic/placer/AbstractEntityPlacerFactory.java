package greycos.solver.core.impl.constructionheuristic.placer;

import static greycos.solver.core.config.heuristic.selector.common.SelectionCacheType.PHASE;
import static greycos.solver.core.config.heuristic.selector.common.SelectionCacheType.STEP;

import greycos.solver.core.config.constructionheuristic.placer.EntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
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
    ChangeMoveSelectorConfig changeMoveSelectorConfig = new ChangeMoveSelectorConfig();
    changeMoveSelectorConfig.setEntitySelectorConfig(
        EntitySelectorConfig.newMimicSelectorConfig(entitySelectorConfigId));
    ValueSelectorConfig changeValueSelectorConfig =
        new ValueSelectorConfig().withVariableName(variableDescriptor.getVariableName());
    if (ValueSelectorConfig.hasSorter(configPolicy.getValueSorterManner(), variableDescriptor)) {
      changeValueSelectorConfig =
          changeValueSelectorConfig
              .withCacheType(variableDescriptor.canExtractValueRangeFromSolution() ? PHASE : STEP)
              .withSelectionOrder(SelectionOrder.SORTED)
              .withSorterManner(configPolicy.getValueSorterManner());
    }
    return changeMoveSelectorConfig.withValueSelectorConfig(changeValueSelectorConfig);
  }
}
