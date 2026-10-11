package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.list.ListVariableSelectorSupport;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;

public class ListSwapMoveSelectorFactory<Solution_>
    extends AbstractMoveSelectorFactory<Solution_, ListSwapMoveSelectorConfig> {

  public ListSwapMoveSelectorFactory(ListSwapMoveSelectorConfig moveSelectorConfig) {
    // We copy the configuration,
    // as the settings may be updated during the autoconfiguration of the entity value range
    super(Objects.requireNonNull(moveSelectorConfig).copyConfig());
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    var valueSelectorConfig =
        Objects.requireNonNullElseGet(config.getValueSelectorConfig(), ValueSelectorConfig::new);
    var secondaryValueSelectorConfig =
        Objects.requireNonNullElseGet(
            config.getSecondaryValueSelectorConfig(), valueSelectorConfig::copyConfig);
    var selectionOrder = SelectionOrder.fromRandomSelectionBoolean(randomSelection);
    var listDescriptor =
        ListVariableSelectorSupport.resolve(
            configPolicy,
            config.getEntityClass(),
            valueSelectorConfig,
            secondaryValueSelectorConfig);
    var entityDescriptor = listDescriptor.getEntityDescriptor();
    // When enabling entity value range filtering,
    // the recorder ID from the origin selector is required for FilteringEntityValueRangeSelector
    // and FilteringValueRangeSelector
    // to replay the selected value and return only reachable values.
    var enableEntityValueRangeFilter = !listDescriptor.canExtractValueRangeFromSolution();
    // A null ID means to turn off the entity value range filtering
    String entityValueRangeRecorderId = null;
    if (enableEntityValueRangeFilter) {
      if (valueSelectorConfig.getId() == null
          && valueSelectorConfig.getMimicSelectorRef() == null) {
        var variableName = Objects.requireNonNull(valueSelectorConfig.getVariableName());
        // We set the id to make sure the value selector will use the mimic recorder
        entityValueRangeRecorderId =
            ConfigUtils.addRandomSuffix(variableName, configPolicy.getRandom().factoryUsage());
        valueSelectorConfig.setId(entityValueRangeRecorderId);
      } else {
        entityValueRangeRecorderId =
            valueSelectorConfig.getId() != null
                ? valueSelectorConfig.getId()
                : valueSelectorConfig.getMimicSelectorRef();
      }
    }
    var leftValueSelector =
        buildIterableValueSelector(
            configPolicy,
            entityDescriptor,
            valueSelectorConfig,
            minimumCacheType,
            selectionOrder,
            null,
            true);
    var rightValueSelector =
        buildIterableValueSelector(
            configPolicy,
            entityDescriptor,
            secondaryValueSelectorConfig,
            minimumCacheType,
            selectionOrder,
            entityValueRangeRecorderId,
            false);
    var variableDescriptor = leftValueSelector.getVariableDescriptor();
    // This may be redundant but emphasizes that the ListSwapMove is not designed to swap elements
    // on multiple list variables, unlike the SwapMove, which swaps all (basic) variables between
    // left and right entities.
    if (variableDescriptor != rightValueSelector.getVariableDescriptor()) {
      throw new IllegalStateException(
          "Impossible state: the leftValueSelector (%s) and the rightValueSelector (%s) have different variable descriptors. This should have failed fast during config unfolding."
              .formatted(leftValueSelector, rightValueSelector));
    }

    return new ListSwapMoveSelector<>(
        leftValueSelector,
        rightValueSelector,
        randomSelection,
        secondaryValueSelectorConfig.getNearbySelectionConfig() != null);
  }

  private IterableValueSelector<Solution_> buildIterableValueSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      EntityDescriptor<Solution_> entityDescriptor,
      ValueSelectorConfig valueSelectorConfig,
      SelectionCacheType minimumCacheType,
      SelectionOrder inheritedSelectionOrder,
      String entityValueRangeRecorderId,
      boolean originSelection) {
    if (originSelection) {
      return (IterableValueSelector<Solution_>)
          ValueSelectorFactory.<Solution_>create(valueSelectorConfig)
              .buildOriginValueSelector(
                  configPolicy,
                  entityDescriptor,
                  minimumCacheType,
                  inheritedSelectionOrder,
                  ValueSelectorFactory.ListValueFilteringType.NONE);
    }
    // Swap moves require asserting both sides,
    // which means checking if the left and right entities accept the swapped values
    var valueSelector =
        ValueSelectorFactory.<Solution_>create(valueSelectorConfig)
            .buildValueSelector(
                configPolicy,
                entityDescriptor,
                minimumCacheType,
                inheritedSelectionOrder,
                configPolicy.isReinitializeVariableFilterEnabled(),
                ValueSelectorFactory.ListValueFilteringType.NONE,
                entityValueRangeRecorderId,
                true);
    return (IterableValueSelector<Solution_>) valueSelector;
  }

  @Override
  protected MoveSelectorConfig<?> buildUnfoldedMoveSelectorConfig(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    var descriptor =
        ListVariableSelectorSupport.resolve(
            configPolicy,
            config.getEntityClass(),
            config.getValueSelectorConfig(),
            config.getSecondaryValueSelectorConfig());
    var valueConfig = config.getValueSelectorConfig();
    var secondaryConfig = config.getSecondaryValueSelectorConfig();
    if (config.getEntityClass() != null
        && valueConfig != null
        && (valueConfig.getVariableName() != null || valueConfig.getMimicSelectorRef() != null)
        && (secondaryConfig == null
            || secondaryConfig.getVariableName() != null
            || secondaryConfig.getMimicSelectorRef() != null)) {
      return null;
    }
    return buildUnfoldedMoveSelectorConfig(List.of(descriptor));
  }

  protected MoveSelectorConfig<?> buildUnfoldedMoveSelectorConfig(
      List<ListVariableDescriptor<Solution_>> variableDescriptorList) {
    var moveSelectorConfigList = new ArrayList<MoveSelectorConfig>(variableDescriptorList.size());
    for (var variableDescriptor : variableDescriptorList) {
      // No childMoveSelectorConfig.inherit() because of unfoldedMoveSelectorConfig.inheritFolded()
      var childMoveSelectorConfig =
          new ListSwapMoveSelectorConfig()
              .withEntityClass(variableDescriptor.getEntityDescriptor().getEntityClass());
      var childValueSelectorConfig = new ValueSelectorConfig(config.getValueSelectorConfig());
      if (childValueSelectorConfig.getMimicSelectorRef() == null) {
        childValueSelectorConfig.setVariableName(variableDescriptor.getVariableName());
      }
      childMoveSelectorConfig.setValueSelectorConfig(childValueSelectorConfig);
      if (config.getSecondaryValueSelectorConfig() != null) {
        var childSecondaryValueSelectorConfig =
            new ValueSelectorConfig(config.getSecondaryValueSelectorConfig());
        if (childSecondaryValueSelectorConfig.getMimicSelectorRef() == null) {
          childSecondaryValueSelectorConfig.setVariableName(variableDescriptor.getVariableName());
        }
        childMoveSelectorConfig.setSecondaryValueSelectorConfig(childSecondaryValueSelectorConfig);
      }
      moveSelectorConfigList.add(childMoveSelectorConfig);
    }

    MoveSelectorConfig<?> unfoldedMoveSelectorConfig;
    if (moveSelectorConfigList.size() == 1) {
      unfoldedMoveSelectorConfig = moveSelectorConfigList.get(0);
    } else {
      unfoldedMoveSelectorConfig = new UnionMoveSelectorConfig(moveSelectorConfigList);
    }
    unfoldedMoveSelectorConfig.inheritFolded(config);
    return unfoldedMoveSelectorConfig;
  }
}
