package greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin;

import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.CountSupplier;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;

public final class ListRuinRecreateMoveSelectorFactory<Solution_>
    extends AbstractMoveSelectorFactory<Solution_, ListRuinRecreateMoveSelectorConfig> {

  private final ListRuinRecreateMoveSelectorConfig ruinMoveSelectorConfig;

  public ListRuinRecreateMoveSelectorFactory(
      ListRuinRecreateMoveSelectorConfig ruinMoveSelectorConfig) {
    super(ruinMoveSelectorConfig);
    this.ruinMoveSelectorConfig = ruinMoveSelectorConfig;
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    CountSupplier minimumSelectedSupplier = ruinMoveSelectorConfig::determineMinimumRuinedCount;
    CountSupplier maximumSelectedSupplier = ruinMoveSelectorConfig::determineMaximumRuinedCount;

    this.getTheOnlyEntityDescriptorWithListVariable(configPolicy.getSolutionDescriptor());

    var listVariableDescriptor = configPolicy.getSolutionDescriptor().getListVariableDescriptor();
    var entityDescriptor = listVariableDescriptor.getEntityDescriptor();
    var valueSelector =
        (IterableValueSelector<Solution_>)
            ValueSelectorFactory.<Solution_>create(
                    new ValueSelectorConfig()
                        .withVariableName(listVariableDescriptor.getVariableName()))
                .buildValueSelector(
                    configPolicy,
                    entityDescriptor,
                    minimumCacheType,
                    SelectionOrder.RANDOM,
                    false,
                    ValueSelectorFactory.ListValueFilteringType.ACCEPT_ASSIGNED);
    var entityPlacerConfig =
        DefaultConstructionHeuristicPhaseFactory.buildListVariableQueuedValuePlacerConfig(
            configPolicy, listVariableDescriptor);

    var constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig().withEntityPlacerConfig(entityPlacerConfig);
    var constructionHeuristicPhaseBuilder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            configPolicy, constructionHeuristicPhaseConfig);
    return new ListRuinRecreateMoveSelector<>(
        valueSelector,
        listVariableDescriptor,
        constructionHeuristicPhaseBuilder,
        minimumSelectedSupplier,
        maximumSelectedSupplier);
  }
}
