package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Objects;
import java.util.Optional;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.DestinationSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.ListVariableSelectorSupport;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.value.decorator.UnassignedListValueSelector;

public class ListChangeMoveSelectorFactory<Solution_>
    extends AbstractMoveSelectorFactory<Solution_, ListChangeMoveSelectorConfig> {

  private final boolean isExhaustiveSearch;

  public ListChangeMoveSelectorFactory(ListChangeMoveSelectorConfig moveSelectorConfig) {
    this(moveSelectorConfig, false);
  }

  public ListChangeMoveSelectorFactory(
      ListChangeMoveSelectorConfig moveSelectorConfig, boolean isExhaustiveSearch) {
    // We copy the configuration,
    // as the settings may be updated during the autoconfiguration of the entity value range
    super(Objects.requireNonNull(moveSelectorConfig).copyConfig());
    this.isExhaustiveSearch = isExhaustiveSearch;
  }

  @Override
  protected MoveSelector<Solution_> buildMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      MoveSelectorConfig<?> moveSelectorConfig,
      SelectionCacheType minimumCacheType,
      SelectionOrder inheritedSelectionOrder,
      boolean skipNonDoableMoves) {
    if (isExhaustiveSearch) {
      return MoveSelectorFactory.<Solution_>createForExhaustiveSearch(moveSelectorConfig)
          .buildMoveSelector(
              configPolicy, minimumCacheType, inheritedSelectionOrder, skipNonDoableMoves);
    } else {
      return MoveSelectorFactory.<Solution_>create(moveSelectorConfig)
          .buildMoveSelector(
              configPolicy, minimumCacheType, inheritedSelectionOrder, skipNonDoableMoves);
    }
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    var valueSelectorConfig = checkUnfolded("valueSelectorConfig", config.getValueSelectorConfig());
    var destinationSelectorConfig =
        checkUnfolded("destinationSelectorConfig", config.getDestinationSelectorConfig());
    var destinationEntitySelectorConfig =
        checkUnfolded(
            "destinationEntitySelectorConfig", destinationSelectorConfig.getEntitySelectorConfig());
    checkUnfolded(
        "destinationValueSelectorConfig", destinationSelectorConfig.getValueSelectorConfig());

    var selectionOrder = SelectionOrder.fromRandomSelectionBoolean(randomSelection);

    var entityDescriptor =
        EntitySelectorFactory.<Solution_>create(destinationEntitySelectorConfig)
            .extractEntityDescriptor(configPolicy);

    // When enabling entity value range filtering,
    // the recorder ID from the origin selector is required for FilteringEntityValueRangeSelector
    // and FilteringValueRangeSelector
    // to replay the selected value and return only reachable values.
    var enableEntityValueRangeFilter =
        !ListVariableSelectorSupport.resolve(
                configPolicy, entityDescriptor.getEntityClass(), valueSelectorConfig)
            .canExtractValueRangeFromSolution();
    // A null ID means to turn off the entity value range filtering
    String entityValueRangeRecorderId = null;
    if (enableEntityValueRangeFilter) {
      var mimicSelectorConfig = Objects.requireNonNull(valueSelectorConfig);
      if (mimicSelectorConfig.getMimicSelectorRef() == null
          && mimicSelectorConfig.getId() == null) {
        var variableName = Objects.requireNonNull(mimicSelectorConfig.getVariableName());
        // We set the id to make sure the value selector will use the mimic recorder
        entityValueRangeRecorderId =
            ConfigUtils.addRandomSuffix(variableName, configPolicy.getRandom().factoryUsage());
        mimicSelectorConfig.setId(entityValueRangeRecorderId);
      } else {
        entityValueRangeRecorderId =
            mimicSelectorConfig.getMimicSelectorRef() != null
                ? mimicSelectorConfig.getMimicSelectorRef()
                : mimicSelectorConfig.getId();
      }
    }
    if (enableEntityValueRangeFilter && entityValueRangeRecorderId == null) {
      throw new IllegalStateException(
          "Impossible state: when enabling entity value range, the origin value selector ID is required for the destination selector %s."
              .formatted(destinationSelectorConfig));
    }

    var sourceValueSelector =
        ValueSelectorFactory.<Solution_>create(valueSelectorConfig)
            .buildOriginValueSelector(
                configPolicy,
                entityDescriptor,
                minimumCacheType,
                selectionOrder,
                ValueSelectorFactory.ListValueFilteringType.NONE);
    if (isExhaustiveSearch) {
      sourceValueSelector =
          new UnassignedListValueSelector<>((IterableValueSelector<Solution_>) sourceValueSelector);
    }

    var destinationSelector =
        DestinationSelectorFactory.<Solution_>create(destinationSelectorConfig)
            .buildDestinationSelector(
                configPolicy,
                minimumCacheType,
                randomSelection,
                entityValueRangeRecorderId,
                isExhaustiveSearch);

    return new ListChangeMoveSelector<>(
        (IterableValueSelector<Solution_>) sourceValueSelector,
        destinationSelector,
        randomSelection,
        destinationSelectorConfig.getNearbySelectionConfig() != null);
  }

  @Override
  protected MoveSelectorConfig<?> buildUnfoldedMoveSelectorConfig(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    var destinationSelectorConfig = config.getDestinationSelectorConfig();
    var entityConfig =
        destinationSelectorConfig == null
            ? null
            : destinationSelectorConfig.getEntitySelectorConfig();
    var entityClass = entityConfig == null ? null : entityConfig.getEntityClass();
    if (entityConfig != null && entityConfig.getMimicSelectorRef() != null) {
      entityClass =
          EntitySelectorFactory.<Solution_>create(entityConfig)
              .extractEntityDescriptor(configPolicy)
              .getEntityClass();
    }
    var valueConfig = config.getValueSelectorConfig();
    var destinationValueConfig =
        destinationSelectorConfig == null
            ? null
            : destinationSelectorConfig.getValueSelectorConfig();
    var descriptor =
        ListVariableSelectorSupport.resolve(
            configPolicy, entityClass, valueConfig, destinationValueConfig);
    if (entityClass != null
        && valueConfig != null
        && destinationValueConfig != null
        && (valueConfig.getVariableName() != null || valueConfig.getMimicSelectorRef() != null)
        && destinationValueConfig.getVariableName() != null) {
      return null;
    }
    var unfolded = buildChildMoveSelectorConfig(descriptor, valueConfig, destinationSelectorConfig);
    unfolded.inheritFolded(config);
    return unfolded;
  }

  public static ListChangeMoveSelectorConfig buildChildMoveSelectorConfig(
      ListVariableDescriptor<?> variableDescriptor,
      ValueSelectorConfig inheritedValueSelectorConfig,
      DestinationSelectorConfig inheritedDestinationSelectorConfig) {

    var childValueSelectorConfig = new ValueSelectorConfig(inheritedValueSelectorConfig);
    if (childValueSelectorConfig.getMimicSelectorRef() == null) {
      childValueSelectorConfig.setVariableName(variableDescriptor.getVariableName());
    }

    return new ListChangeMoveSelectorConfig()
        .withValueSelectorConfig(childValueSelectorConfig)
        .withDestinationSelectorConfig(
            new DestinationSelectorConfig(inheritedDestinationSelectorConfig)
                .withEntitySelectorConfig(
                    Optional.ofNullable(inheritedDestinationSelectorConfig)
                        .map(DestinationSelectorConfig::getEntitySelectorConfig)
                        .map(
                            EntitySelectorConfig::new) // use copy constructor if inherited not null
                        .orElseGet(EntitySelectorConfig::new) // otherwise create new instance
                        // override entity class (destination entity selector is never replaying)
                        .withEntityClass(variableDescriptor.getEntityDescriptor().getEntityClass()))
                .withValueSelectorConfig(
                    Optional.ofNullable(inheritedDestinationSelectorConfig)
                        .map(DestinationSelectorConfig::getValueSelectorConfig)
                        .map(ValueSelectorConfig::new) // use copy constructor if inherited not null
                        .orElseGet(ValueSelectorConfig::new) // otherwise create new instance
                        // override variable name (destination value selector is never replaying)
                        .withVariableName(variableDescriptor.getVariableName())));
  }
}
