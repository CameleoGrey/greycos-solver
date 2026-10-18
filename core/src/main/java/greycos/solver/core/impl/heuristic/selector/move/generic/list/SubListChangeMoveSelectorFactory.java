package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Objects;
import java.util.Optional;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.DestinationSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.list.ListVariableSelectorSupport;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

public class SubListChangeMoveSelectorFactory<Solution_>
    extends AbstractMoveSelectorFactory<Solution_, SubListChangeMoveSelectorConfig> {

  public SubListChangeMoveSelectorFactory(SubListChangeMoveSelectorConfig moveSelectorConfig) {
    super(moveSelectorConfig);
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    var subListSelectorConfig =
        checkUnfolded("subListSelectorConfig", config.getSubListSelectorConfig());
    var destinationSelectorConfig =
        checkUnfolded("destinationSelectorConfig", config.getDestinationSelectorConfig());
    if (!randomSelection) {
      throw new IllegalArgumentException(
          "The subListChangeMoveSelector (%s) only supports random selection order."
              .formatted(config));
    }
    var selectionOrder = SelectionOrder.fromRandomSelectionBoolean(randomSelection);
    var entitySelector =
        EntitySelectorFactory.<Solution_>create(destinationSelectorConfig.getEntitySelectorConfig())
            .buildEntitySelector(configPolicy, minimumCacheType, selectionOrder);
    var subListSelector =
        SubListSelectorFactory.<Solution_>create(subListSelectorConfig)
            .buildOriginSubListSelector(
                configPolicy, entitySelector, minimumCacheType, selectionOrder);
    var destinationSelector =
        DestinationSelectorFactory.<Solution_>create(destinationSelectorConfig)
            .buildDestinationSelector(configPolicy, minimumCacheType, randomSelection);
    var selectReversingMoveToo =
        Objects.requireNonNullElse(config.getSelectReversingMoveToo(), true);
    return new RandomSubListChangeMoveSelector<>(
        subListSelector,
        destinationSelector,
        selectReversingMoveToo,
        destinationSelectorConfig.getNearbySelectionConfig() != null);
  }

  @Override
  protected MoveSelectorConfig<?> buildUnfoldedMoveSelectorConfig(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    var destinationConfig = config.getDestinationSelectorConfig();
    var entityConfig =
        destinationConfig == null ? null : destinationConfig.getEntitySelectorConfig();
    var entityClass = entityConfig == null ? null : entityConfig.getEntityClass();
    if (entityConfig != null && entityConfig.getMimicSelectorRef() != null) {
      entityClass =
          EntitySelectorFactory.<Solution_>create(entityConfig)
              .extractEntityDescriptor(configPolicy)
              .getEntityClass();
    }
    var subListConfig = config.getSubListSelectorConfig();
    var destinationValueConfig =
        destinationConfig == null ? null : destinationConfig.getValueSelectorConfig();
    var descriptor =
        ListVariableSelectorSupport.resolve(
            configPolicy, entityClass, subListConfig, destinationValueConfig);
    var sourceValueConfig = subListConfig == null ? null : subListConfig.getValueSelectorConfig();
    if (entityClass != null
        && subListConfig != null
        && destinationValueConfig != null
        && (subListConfig.getMimicSelectorRef() != null
            || sourceValueConfig != null
                && (sourceValueConfig.getVariableName() != null
                    || sourceValueConfig.getMimicSelectorRef() != null))
        && destinationValueConfig.getVariableName() != null) {
      return null;
    }
    return buildChildMoveSelectorConfig(descriptor);
  }

  private SubListChangeMoveSelectorConfig buildChildMoveSelectorConfig(
      ListVariableDescriptor<?> variableDescriptor) {
    var subListSelectorConfig = config.getSubListSelectorConfig();
    var destinationSelectorConfig = config.getDestinationSelectorConfig();
    var subListChangeMoveSelectorConfig =
        config
            .copyConfig()
            .withSubListSelectorConfig(
                new SubListSelectorConfig(subListSelectorConfig)
                    .withValueSelectorConfig(
                        Optional.ofNullable(subListSelectorConfig)
                            .map(SubListSelectorConfig::getValueSelectorConfig)
                            .map(
                                ValueSelectorConfig
                                    ::new) // use copy constructor if inherited not null
                            .orElseGet(ValueSelectorConfig::new)))
            .withDestinationSelectorConfig(
                new DestinationSelectorConfig(destinationSelectorConfig)
                    .withEntitySelectorConfig(
                        Optional.ofNullable(destinationSelectorConfig)
                            .map(DestinationSelectorConfig::getEntitySelectorConfig)
                            .map(
                                EntitySelectorConfig
                                    ::new) // use copy constructor if inherited not null
                            .orElseGet(EntitySelectorConfig::new) // otherwise create new instance
                            // override entity class (destination entity selector is never
                            // replaying)
                            .withEntityClass(
                                variableDescriptor.getEntityDescriptor().getEntityClass()))
                    .withValueSelectorConfig(
                        Optional.ofNullable(destinationSelectorConfig)
                            .map(DestinationSelectorConfig::getValueSelectorConfig)
                            .map(
                                ValueSelectorConfig
                                    ::new) // use copy constructor if inherited not null
                            .orElseGet(ValueSelectorConfig::new) // otherwise create new instance
                            // override variable name (destination value selector is never
                            // replaying)
                            .withVariableName(variableDescriptor.getVariableName())));

    var effectiveSubListSelectorConfig =
        Objects.requireNonNull(subListChangeMoveSelectorConfig.getSubListSelectorConfig());
    if (effectiveSubListSelectorConfig.getMimicSelectorRef() != null) {
      effectiveSubListSelectorConfig.setValueSelectorConfig(null);
    } else {
      Objects.requireNonNull(effectiveSubListSelectorConfig.getValueSelectorConfig())
          .setVariableName(variableDescriptor.getVariableName());
    }
    return subListChangeMoveSelectorConfig;
  }
}
