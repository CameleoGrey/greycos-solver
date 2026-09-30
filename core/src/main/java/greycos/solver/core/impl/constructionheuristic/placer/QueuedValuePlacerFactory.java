package greycos.solver.core.impl.constructionheuristic.placer;

import java.util.ArrayList;
import java.util.Objects;

import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyMoveSelectorFactory;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelectorFactory;

public class QueuedValuePlacerFactory<Solution_>
    extends AbstractEntityPlacerFactory<Solution_, QueuedValuePlacerConfig> {

  public static QueuedValuePlacerConfig unfoldNew(MoveSelectorConfig templateMoveSelectorConfig) {
    return unfoldNew(templateMoveSelectorConfig, new ValueSelectorConfig().withId("queuedValue"));
  }

  public static <Solution_> QueuedValuePlacerConfig unfoldNew(
      HeuristicConfigPolicy<Solution_> configPolicy,
      MoveSelectorConfig templateMoveSelectorConfig) {
    var config = new QueuedValuePlacerConfig();
    var factory = new QueuedValuePlacerFactory<Solution_>(config);
    var entityDescriptor = factory.deduceEntityDescriptor(configPolicy, null);
    return unfoldNew(
        templateMoveSelectorConfig,
        factory.buildValueSelectorConfig(configPolicy, entityDescriptor));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static QueuedValuePlacerConfig unfoldNew(
      MoveSelectorConfig templateMoveSelectorConfig, ValueSelectorConfig queuedValueConfig) {
    var moveConfig = (MoveSelectorConfig) templateMoveSelectorConfig.copyConfig();
    var leaves = new ArrayList<MoveSelectorConfig>();
    moveConfig.extractLeafMoveSelectorConfigsIntoList(leaves);
    for (var leaf : leaves) {
      if (!(leaf instanceof ChangeMoveSelectorConfig change)) {
        throw new IllegalArgumentException(
            "The queued value construction heuristic candidate (%s) must be a changeMoveSelector, optionally nested in a composite selector."
                .formatted(leaf));
      }
      if (change.getValueSelectorConfig() != null) {
        throw new IllegalArgumentException(
            "The queued value construction heuristic changeMoveSelector (%s) contains a valueSelector. Configure a queuedValuePlacer explicitly when source selection is customized."
                .formatted(change));
      }
      change.setValueSelectorConfig(
          new ValueSelectorConfig(queuedValueConfig.getVariableName())
              .withMimicSelectorRef(queuedValueConfig.getId()));
    }
    return new QueuedValuePlacerConfig()
        .withValueSelectorConfig(queuedValueConfig)
        .withMoveSelectorConfig(moveConfig);
  }

  public QueuedValuePlacerFactory(QueuedValuePlacerConfig placerConfig) {
    super(placerConfig);
  }

  @Override
  public QueuedValuePlacer<Solution_> buildEntityPlacer(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    EntityDescriptor<Solution_> entityDescriptor =
        deduceEntityDescriptor(configPolicy, config.getEntityClass());
    ValueSelectorConfig valueSelectorConfig_ =
        buildValueSelectorConfig(configPolicy, entityDescriptor);
    // TODO improve the ValueSelectorFactory API (avoid the boolean flags).
    ValueSelector<Solution_> valueSelector =
        ValueSelectorFactory.<Solution_>create(valueSelectorConfig_)
            .buildValueSelector(
                configPolicy,
                entityDescriptor,
                SelectionCacheType.PHASE,
                SelectionOrder.ORIGINAL,
                false, // override applyReinitializeVariableFiltering
                ValueSelectorFactory.ListValueFilteringType.ACCEPT_UNASSIGNED);

    MoveSelectorConfig<?> moveSelectorConfig_ =
        config.getMoveSelectorConfig() == null
            ? buildChangeMoveSelectorConfig(
                configPolicy, valueSelectorConfig_.getId(), valueSelector.getVariableDescriptor())
            : config.getMoveSelectorConfig();

    MoveSelector<Solution_> moveSelector =
        MoveSelectorFactory.<Solution_>create(moveSelectorConfig_)
            .buildMoveSelector(
                configPolicy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL, false);
    moveSelector =
        ConstructionHeuristicNearbyMoveSelectorFactory.wrap(
            moveSelector, moveSelectorConfig_, configPolicy);
    if (!(valueSelector instanceof IterableValueSelector<Solution_> iterableValueSelector)) {
      throw new IllegalArgumentException(
          "The queuedValuePlacer (%s) needs to be based on an %s (%s). Check your @%s annotations."
              .formatted(
                  this,
                  IterableValueSelector.class.getSimpleName(),
                  valueSelector,
                  ValueRangeProvider.class.getSimpleName()));
    }
    return new QueuedValuePlacer<>(this, configPolicy, iterableValueSelector, moveSelector);
  }

  private ValueSelectorConfig buildValueSelectorConfig(
      HeuristicConfigPolicy<Solution_> configPolicy, EntityDescriptor<Solution_> entityDescriptor) {
    var result =
        Objects.requireNonNullElseGet(
            config.getValueSelectorConfig(),
            () -> {
              var entityClass = entityDescriptor.getEntityClass();
              var variableDescriptor = getTheOnlyVariableDescriptor(entityDescriptor);
              var valueSelectorConfig =
                  new ValueSelectorConfig()
                      .withId(entityClass.getName() + "." + variableDescriptor.getVariableName())
                      .withVariableName(variableDescriptor.getVariableName());
              if (ValueSelectorConfig.hasSorter(
                  configPolicy.getValueSorterManner(), variableDescriptor)) {
                valueSelectorConfig =
                    valueSelectorConfig
                        .withCacheType(SelectionCacheType.PHASE)
                        .withSelectionOrder(SelectionOrder.SORTED)
                        .withSorterManner(configPolicy.getValueSorterManner());
              }
              return valueSelectorConfig;
            });
    var cacheType = result.getCacheType();
    if (cacheType != null && cacheType.compareTo(SelectionCacheType.PHASE) < 0) {
      throw new IllegalArgumentException(
          "The queuedValuePlacer (%s) cannot have a valueSelectorConfig (%s) with a cacheType (%s) lower than %s."
              .formatted(this, result, cacheType, SelectionCacheType.PHASE));
    }
    return result;
  }

  @Override
  protected ChangeMoveSelectorConfig buildChangeMoveSelectorConfig(
      HeuristicConfigPolicy<Solution_> configPolicy,
      String valueSelectorConfigId,
      GenuineVariableDescriptor<Solution_> variableDescriptor) {
    ChangeMoveSelectorConfig changeMoveSelectorConfig = new ChangeMoveSelectorConfig();
    EntityDescriptor<Solution_> entityDescriptor = variableDescriptor.getEntityDescriptor();
    EntitySelectorConfig changeEntitySelectorConfig =
        new EntitySelectorConfig().withEntityClass(entityDescriptor.getEntityClass());
    if (configPolicy.getEntitySorterManner() != null
        && EntitySelectorConfig.hasSorter(configPolicy.getEntitySorterManner(), entityDescriptor)) {
      changeEntitySelectorConfig =
          changeEntitySelectorConfig
              .withCacheType(SelectionCacheType.PHASE)
              .withSelectionOrder(SelectionOrder.SORTED)
              .withSorterManner(configPolicy.getEntitySorterManner());
    }
    ValueSelectorConfig changeValueSelectorConfig =
        new ValueSelectorConfig()
            .withVariableName(variableDescriptor.getVariableName())
            .withMimicSelectorRef(valueSelectorConfigId);
    return changeMoveSelectorConfig
        .withEntitySelectorConfig(changeEntitySelectorConfig)
        .withValueSelectorConfig(changeValueSelectorConfig);
  }
}
