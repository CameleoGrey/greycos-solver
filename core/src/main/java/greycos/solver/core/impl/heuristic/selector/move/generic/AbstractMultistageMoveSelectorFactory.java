package greycos.solver.core.impl.heuristic.selector.move.generic;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.multistage.MultistageDefinition;

/** Shared construction and validation of multistage selectors. */
public abstract class AbstractMultistageMoveSelectorFactory<
        Solution_, Config_ extends MoveSelectorConfig<Config_>>
    extends AbstractMoveSelectorFactory<Solution_, Config_> {

  private final Class<?> entityClass;
  private final String variableName;
  private final Class<?> providerClass;
  private final int candidateCountLimit;
  private final int probeCountLimit;
  private final boolean listVariable;

  protected AbstractMultistageMoveSelectorFactory(
      Config_ config,
      Class<?> entityClass,
      String variableName,
      Class<?> providerClass,
      int candidateCountLimit,
      int probeCountLimit,
      boolean listVariable) {
    super(config);
    this.entityClass = entityClass;
    this.variableName = variableName;
    this.providerClass = providerClass;
    this.candidateCountLimit = candidateCountLimit;
    this.probeCountLimit = probeCountLimit;
    this.listVariable = listVariable;
  }

  @Override
  public MoveSelector<Solution_> buildMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      SelectionOrder inheritedSelectionOrder,
      boolean skipNonDoableMoves) {
    if (!configPolicy.isMultistageMoveSelectionEnabled()) {
      throw new IllegalArgumentException(
          "The multistage move selector (%s) requires a local search phase. ".formatted(config)
              + "Maybe configure it in localSearch, directly or inside a unionMoveSelector.");
    }
    var resolvedCacheType =
        SelectionCacheType.max(
            minimumCacheType, SelectionCacheType.resolve(config.getCacheType(), minimumCacheType));
    if (resolvedCacheType != SelectionCacheType.JUST_IN_TIME) {
      throw new IllegalArgumentException(
          "The multistage move selector (%s) has resolved cacheType (%s); only JUST_IN_TIME is supported. "
                  .formatted(config, resolvedCacheType)
              + "Maybe remove caching from this selector and its ancestors.");
    }
    var resolvedOrder = SelectionOrder.resolve(config.getSelectionOrder(), inheritedSelectionOrder);
    if (resolvedOrder != SelectionOrder.ORIGINAL && resolvedOrder != SelectionOrder.RANDOM) {
      throw new IllegalArgumentException(
          "The multistage move selector (%s) has resolved selectionOrder (%s); only ORIGINAL and RANDOM are supported."
              .formatted(config, resolvedOrder));
    }
    if (configPolicy.getGuidedLocalSearchSelectionContext() != null) {
      throw new IllegalArgumentException(
          "The multistage move selector (%s) does not support guided local search directedOriginSelection. "
                  .formatted(config)
              + "Maybe disable directedOriginSelection.");
    }
    validateProvider();
    return super.buildMoveSelector(
        configPolicy, minimumCacheType, inheritedSelectionOrder, skipNonDoableMoves);
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    var variableDescriptor = resolveVariable(configPolicy);
    var targetEntityDescriptor =
        entityClass == null
            ? variableDescriptor.getEntityDescriptor()
            : configPolicy.getSolutionDescriptor().getEntityDescriptorStrict(entityClass);
    var definition =
        new MultistageDefinition<>(
            configPolicy.getSolutionDescriptor(),
            variableDescriptor,
            providerClass,
            probeCountLimit,
            targetEntityDescriptor);
    return new MultistageMoveSelector<>(definition, randomSelection, candidateCountLimit);
  }

  protected Class<?> getProviderInterface() {
    return listVariable ? ListVariableStageProvider.class : BasicVariableStageProvider.class;
  }

  private void validateProvider() {
    var providerInterface = getProviderInterface();
    if (providerClass == null || !providerInterface.isAssignableFrom(providerClass)) {
      throw new IllegalArgumentException(
          "The multistage move selector (%s) has stageProviderClass (%s), which must implement %s."
              .formatted(config, providerClass, providerInterface.getSimpleName()));
    }
    try {
      if (Modifier.isAbstract(providerClass.getModifiers())
          || !Modifier.isPublic(providerClass.getModifiers())
          || !Modifier.isPublic(providerClass.getConstructor().getModifiers())) {
        throw new NoSuchMethodException();
      }
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "The multistage move selector (%s) has stageProviderClass (%s), which must be a public concrete class with a public no-arg constructor."
              .formatted(config, providerClass.getName()),
          e);
    }
  }

  private GenuineVariableDescriptor<Solution_> resolveVariable(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    var solutionDescriptor = configPolicy.getSolutionDescriptor();
    List<EntityDescriptor<Solution_>> entities;
    if (entityClass == null) {
      entities = List.copyOf(solutionDescriptor.getGenuineEntityDescriptors());
    } else {
      var entity = solutionDescriptor.getEntityDescriptorStrict(entityClass);
      if (entity == null) {
        throw new IllegalArgumentException(
            "The multistage move selector (%s) has entityClass (%s), which is not a configured planning entity. "
                    .formatted(config, entityClass.getName())
                + "Maybe use one of "
                + solutionDescriptor.getEntityClassSet()
                + ".");
      }
      entities = List.of(entity);
    }
    var candidates = new ArrayList<GenuineVariableDescriptor<Solution_>>();
    for (var entity : entities) {
      for (var variable : entity.getGenuineVariableDescriptorList()) {
        if (variable.isListVariable() == listVariable
            && (variableName == null || variableName.equals(variable.getVariableName()))
            && !candidates.contains(variable)) {
          candidates.add(variable);
        }
      }
    }
    if (candidates.size() != 1) {
      throw new IllegalArgumentException(
          "The multistage move selector (%s) with entityClass (%s) and variableName (%s) resolves to %d %s planning variables (%s); exactly one is required. "
                  .formatted(
                      config,
                      entityClass,
                      variableName,
                      candidates.size(),
                      listVariable ? "list" : "basic",
                      candidates)
              + "Maybe specify entityClass and variableName for the intended planning variable.");
    }
    return candidates.getFirst();
  }

  /** Finds multistage descendants before a composite can hide their execution semantics. */
  public static boolean containsMultistage(MoveSelectorConfig<?> config) {
    if (config instanceof CrossVariableMultistageMoveSelectorConfig
        || config instanceof MultistageMoveSelectorConfig
        || config instanceof ListMultistageMoveSelectorConfig) {
      return true;
    }
    List<MoveSelectorConfig> children;
    if (config instanceof UnionMoveSelectorConfig union) {
      children = union.getMoveSelectorList();
    } else if (config instanceof CartesianProductMoveSelectorConfig cartesian) {
      children = cartesian.getMoveSelectorList();
    } else {
      return false;
    }
    return children != null
        && children.stream().anyMatch(AbstractMultistageMoveSelectorFactory::containsMultistage);
  }
}
