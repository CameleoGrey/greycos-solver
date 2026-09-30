package greycos.solver.core.impl.constructionheuristic.nearby;

import static greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_ENTITY;
import static greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_VALUE;
import static greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape.VALUE_DESTINATION;
import static greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape.VALUE_VALUE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.AbstractPillarMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

/**
 * Discovers selector argument contracts without unfolding selectors, creating meters, consuming
 * random numbers, or changing the user's configuration.
 */
public final class ConstructionHeuristicNearbyProfileResolver {

  private ConstructionHeuristicNearbyProfileResolver() {}

  public static ConstructionHeuristicNearbyProfiles resolveGlobal(
      SolutionDescriptor<?> solutionDescriptor,
      Class<? extends NearbyDistanceMeter<?, ?>> distanceMeterClass) {
    if (distanceMeterClass == null) {
      return ConstructionHeuristicNearbyProfiles.empty();
    }
    return resolveLocalSearch(
        new LocalSearchPhaseConfig(),
        solutionDescriptor,
        distanceMeterClass,
        "solver.nearbyDistanceMeterClass");
  }

  public static ConstructionHeuristicNearbyProfiles resolveForPhase(
      List<? extends PhaseConfig> phaseConfigList,
      int phaseIndex,
      HeuristicConfigPolicy<?> configPolicy) {
    var phaseConfig = phaseConfigList.get(phaseIndex);
    var solutionDescriptor = configPolicy.getSolutionDescriptor();
    var meterClass = configPolicy.getNearbyDistanceMeterClass();
    if (phaseConfig instanceof LocalSearchPhaseConfig localSearch) {
      // Nested ruin/recreate uses the profiles of its enclosing search, never a later search.
      return resolveLocalSearch(
          localSearch, solutionDescriptor, meterClass, "phase[" + phaseIndex + "].localSearch");
    }
    var inheritedProfiles = configPolicy.getConstructionHeuristicNearbyProfiles();
    if (inheritedProfiles.isEmpty() && meterClass != null) {
      inheritedProfiles = resolveGlobal(solutionDescriptor, meterClass);
    }
    var stages = new ArrayList<ConstructionHeuristicNearbyProfiles>();
    for (int index = phaseIndex + 1; index < phaseConfigList.size(); index++) {
      collectLocalSearchStages(
          phaseConfigList.get(index),
          solutionDescriptor,
          meterClass,
          "phase[" + index + "]",
          stages);
    }
    var resolved = new ArrayList<ConstructionHeuristicNearbyProfile>();
    for (var entityDescriptor : solutionDescriptor.getGenuineEntityDescriptors()) {
      for (var variableDescriptor : entityDescriptor.getGenuineVariableDescriptorList()) {
        List<ConstructionHeuristicNearbyProfile> variableProfiles = List.of();
        for (var stage : stages) {
          variableProfiles = stage.getProfiles(variableDescriptor);
          if (!variableProfiles.isEmpty()) {
            break;
          }
        }
        if (variableProfiles.isEmpty()) {
          variableProfiles = inheritedProfiles.getProfiles(variableDescriptor);
        }
        resolved.addAll(variableProfiles);
      }
    }
    return new ConstructionHeuristicNearbyProfiles(resolved);
  }

  private static void collectLocalSearchStages(
      PhaseConfig<?> phase,
      SolutionDescriptor<?> solutionDescriptor,
      Class<? extends NearbyDistanceMeter<?, ?>> meterClass,
      String path,
      List<ConstructionHeuristicNearbyProfiles> stages) {
    if (phase instanceof LocalSearchPhaseConfig localSearch) {
      stages.add(
          resolveLocalSearch(localSearch, solutionDescriptor, meterClass, path + ".localSearch"));
    } else if (phase instanceof IslandModelPhaseConfig island) {
      var children = island.getPhaseConfigList();
      if (children == null || children.isEmpty()) {
        stages.add(
            resolveLocalSearch(
                new LocalSearchPhaseConfig().withMoveSelectorConfig(island.getMoveSelectorConfig()),
                solutionDescriptor,
                meterClass,
                path + ".islandModel.localSearch"));
      } else {
        collectChildren(children, solutionDescriptor, meterClass, path + ".islandModel", stages);
      }
    } else if (phase instanceof PartitionedSearchPhaseConfig partition) {
      var children = partition.getPhaseConfigList();
      if (children == null || children.isEmpty()) {
        stages.add(
            resolveLocalSearch(
                new LocalSearchPhaseConfig(),
                solutionDescriptor,
                meterClass,
                path + ".partitionedSearch.localSearch"));
      } else {
        collectChildren(
            children, solutionDescriptor, meterClass, path + ".partitionedSearch", stages);
      }
    }
    // ALNS, custom phases and exhaustive search do not supply nearby selector contracts.
  }

  private static void collectChildren(
      List<? extends PhaseConfig> children,
      SolutionDescriptor<?> solutionDescriptor,
      Class<? extends NearbyDistanceMeter<?, ?>> meterClass,
      String path,
      List<ConstructionHeuristicNearbyProfiles> stages) {
    for (int index = 0; index < children.size(); index++) {
      collectLocalSearchStages(
          children.get(index),
          solutionDescriptor,
          meterClass,
          path + ".phase[" + index + "]",
          stages);
    }
  }

  public static ConstructionHeuristicNearbyProfiles resolveLocalSearch(
      LocalSearchPhaseConfig phase,
      SolutionDescriptor<?> solutionDescriptor,
      Class<? extends NearbyDistanceMeter<?, ?>> meterClass,
      String path) {
    var selector = phase.getMoveSelectorConfig();
    if (selector == null && phase.getNeighborhoodProviderClass() != null) {
      return ConstructionHeuristicNearbyProfiles.empty();
    }
    if (selector == null) {
      selector = defaultSelector(solutionDescriptor);
    }
    var discovery = new Discovery(solutionDescriptor, meterClass);
    discovery.index(selector, null);
    discovery.visit(selector, path + ".moveSelector", true);
    return new ConstructionHeuristicNearbyProfiles(discovery.profiles);
  }

  private static MoveSelectorConfig<?> defaultSelector(SolutionDescriptor<?> solutionDescriptor) {
    var selectors = new ArrayList<MoveSelectorConfig>();
    if (solutionDescriptor.hasBasicVariable()) {
      selectors.add(new ChangeMoveSelectorConfig());
      selectors.add(new SwapMoveSelectorConfig());
    }
    if (solutionDescriptor.hasListVariable()) {
      selectors.add(new ListChangeMoveSelectorConfig());
      selectors.add(new ListSwapMoveSelectorConfig());
      selectors.add(new KOptListMoveSelectorConfig());
    }
    return new UnionMoveSelectorConfig(selectors);
  }

  private static final class Discovery {

    private final SolutionDescriptor<?> solutionDescriptor;
    private final Class<? extends NearbyDistanceMeter<?, ?>> globalMeterClass;
    private final NearbyDistanceMeterContract globalMeterContract;
    private final List<ConstructionHeuristicNearbyProfile> profiles = new ArrayList<>();
    private final Map<String, EntitySelectorConfig> entityRecorders = new HashMap<>();
    private final Map<String, ValueBinding> valueRecorders = new HashMap<>();
    private final Map<String, SubListSelectorConfig> subListRecorders = new HashMap<>();

    private Discovery(
        SolutionDescriptor<?> solutionDescriptor,
        Class<? extends NearbyDistanceMeter<?, ?>> globalMeterClass) {
      this.solutionDescriptor = solutionDescriptor;
      this.globalMeterClass = globalMeterClass;
      this.globalMeterContract =
          globalMeterClass == null ? null : NearbyDistanceMeterContract.of(globalMeterClass);
    }

    private static List<MoveSelectorConfig> children(MoveSelectorConfig<?> selector) {
      if (selector instanceof UnionMoveSelectorConfig union) {
        return union.getMoveSelectorList();
      } else if (selector instanceof CartesianProductMoveSelectorConfig cartesian) {
        return cartesian.getMoveSelectorList();
      }
      return null;
    }

    private void index(MoveSelectorConfig<?> selector, EntitySelectorConfig owner) {
      if (selector == null) {
        return;
      }
      var children = children(selector);
      if (children != null) {
        for (var child : children) {
          index(child, owner);
        }
      } else if (selector instanceof ChangeMoveSelectorConfig change) {
        indexEntity(change.getEntitySelectorConfig());
        indexValue(change.getValueSelectorConfig(), change.getEntitySelectorConfig());
      } else if (selector instanceof SwapMoveSelectorConfig swap) {
        indexEntity(swap.getEntitySelectorConfig());
        indexEntity(swap.getSecondaryEntitySelectorConfig());
      } else if (selector instanceof ListChangeMoveSelectorConfig change) {
        indexValue(change.getValueSelectorConfig(), null);
        indexDestination(change.getDestinationSelectorConfig());
      } else if (selector instanceof ListSwapMoveSelectorConfig swap) {
        indexValue(swap.getValueSelectorConfig(), null);
        indexValue(swap.getSecondaryValueSelectorConfig(), null);
      } else if (selector instanceof SubListChangeMoveSelectorConfig change) {
        indexSubList(change.getSubListSelectorConfig());
        indexDestination(change.getDestinationSelectorConfig());
      } else if (selector instanceof SubListSwapMoveSelectorConfig swap) {
        indexSubList(swap.getSubListSelectorConfig());
        indexSubList(swap.getSecondarySubListSelectorConfig());
      } else if (selector instanceof KOptListMoveSelectorConfig kOpt) {
        indexValue(kOpt.getOriginSelectorConfig(), null);
        indexValue(kOpt.getValueSelectorConfig(), null);
      } else if (selector instanceof AbstractPillarMoveSelectorConfig<?> pillar) {
        var pillarSelector = pillar.getPillarSelectorConfig();
        var entity = pillarSelector == null ? null : pillarSelector.getEntitySelectorConfig();
        indexEntity(entity);
        if (selector instanceof PillarChangeMoveSelectorConfig change) {
          indexValue(change.getValueSelectorConfig(), entity);
        } else if (selector instanceof PillarSwapMoveSelectorConfig swap
            && swap.getSecondaryPillarSelectorConfig() != null) {
          indexEntity(swap.getSecondaryPillarSelectorConfig().getEntitySelectorConfig());
        }
      }
    }

    private void indexEntity(EntitySelectorConfig selector) {
      if (selector != null && selector.getId() != null) {
        entityRecorders.put(selector.getId(), selector);
      }
    }

    private void indexValue(ValueSelectorConfig selector, EntitySelectorConfig owner) {
      if (selector != null && selector.getId() != null) {
        valueRecorders.put(selector.getId(), new ValueBinding(selector, owner));
      }
    }

    private void indexSubList(SubListSelectorConfig selector) {
      if (selector != null) {
        if (selector.getId() != null) {
          subListRecorders.put(selector.getId(), selector);
        }
        indexValue(selector.getValueSelectorConfig(), null);
      }
    }

    private void indexDestination(DestinationSelectorConfig selector) {
      if (selector != null) {
        indexEntity(selector.getEntitySelectorConfig());
        indexValue(selector.getValueSelectorConfig(), selector.getEntitySelectorConfig());
      }
    }

    private void visit(MoveSelectorConfig<?> selector, String path, boolean autoConfigured) {
      var children = children(selector);
      if (children != null) {
        for (int index = 0; index < children.size(); index++) {
          visit(
              children.get(index),
              path + ".child[" + index + "]",
              selector instanceof UnionMoveSelectorConfig);
        }
        return;
      }
      if (selector instanceof ChangeMoveSelectorConfig change) {
        var entity = change.getEntitySelectorConfig();
        var value = change.getValueSelectorConfig();
        for (var binding : variableBindings(entity, value, false)) {
          var variable = binding.variableDescriptor();
          if (variable.isListVariable()) {
            // Generic change unfolds to list change only outside mixed models, or when explicit.
            if (!solutionDescriptor.hasBothBasicAndListVariables()
                || (entity != null && value != null && variableName(value) != null)) {
              manual(binding, nearby(value), VALUE_VALUE, path + ".valueSelector");
              if (autoConfigured) {
                global(variable, VALUE_DESTINATION, path);
              }
            }
          } else {
            manual(binding, nearby(entity), ENTITY_ENTITY, path + ".entitySelector");
            manual(binding, nearby(value), ENTITY_VALUE, path + ".valueSelector");
            if (autoConfigured) {
              global(variable, ENTITY_VALUE, path);
            }
          }
        }
      } else if (selector instanceof SwapMoveSelectorConfig swap) {
        var entity = swap.getEntitySelectorConfig();
        var secondary = swap.getSecondaryEntitySelectorConfig();
        for (var binding : variableBindings(entity, null, false)) {
          var variable = binding.variableDescriptor();
          if (swap.getVariableNameIncludeList() != null
              && !swap.getVariableNameIncludeList().contains(variable.getVariableName())) {
            continue;
          }
          if (variable.isListVariable()) {
            var entityVariables = variable.getEntityDescriptor().getGenuineVariableDescriptorList();
            if (entityVariables.size() == 1
                && (!solutionDescriptor.hasBothBasicAndListVariables() || entity != null)) {
              if (autoConfigured) {
                global(variable, VALUE_VALUE, path);
              }
            }
          } else {
            manual(binding, nearby(entity), ENTITY_ENTITY, path + ".entitySelector");
            manual(binding, nearby(secondary), ENTITY_ENTITY, path + ".secondaryEntitySelector");
            if (autoConfigured) {
              global(variable, ENTITY_ENTITY, path);
            }
          }
        }
      } else if (selector instanceof ListChangeMoveSelectorConfig change) {
        listChange(
            change.getValueSelectorConfig(),
            change.getDestinationSelectorConfig(),
            path,
            autoConfigured);
      } else if (selector instanceof ListSwapMoveSelectorConfig swap) {
        listValues(
            swap.getValueSelectorConfig(),
            swap.getSecondaryValueSelectorConfig(),
            path,
            autoConfigured);
      } else if (selector instanceof KOptListMoveSelectorConfig kOpt) {
        listValues(
            kOpt.getOriginSelectorConfig(), kOpt.getValueSelectorConfig(), path, autoConfigured);
      } else if (selector instanceof SubListChangeMoveSelectorConfig change) {
        var origin = effectiveSubList(change.getSubListSelectorConfig());
        listChange(
            origin == null ? null : origin.getValueSelectorConfig(),
            change.getDestinationSelectorConfig(),
            path,
            false);
        manualSubList(origin, path + ".subListSelector");
      } else if (selector instanceof SubListSwapMoveSelectorConfig swap) {
        manualSubList(effectiveSubList(swap.getSubListSelectorConfig()), path + ".subListSelector");
        manualSubList(
            effectiveSubList(swap.getSecondarySubListSelectorConfig()),
            path + ".secondarySubListSelector");
      } else if (selector instanceof AbstractPillarMoveSelectorConfig<?> pillar) {
        var pillarSelector = pillar.getPillarSelectorConfig();
        var entity = pillarSelector == null ? null : pillarSelector.getEntitySelectorConfig();
        var value =
            selector instanceof PillarChangeMoveSelectorConfig change
                ? change.getValueSelectorConfig()
                : null;
        for (var binding : variableBindings(entity, value, false)) {
          var variable = binding.variableDescriptor();
          if (!variable.isListVariable()) {
            manual(binding, nearby(entity), ENTITY_ENTITY, path + ".pillarSelector.entitySelector");
            manual(binding, nearby(value), ENTITY_VALUE, path + ".valueSelector");
            if (selector instanceof PillarSwapMoveSelectorConfig swap
                && swap.getSecondaryPillarSelectorConfig() != null) {
              manual(
                  binding,
                  nearby(swap.getSecondaryPillarSelectorConfig().getEntitySelectorConfig()),
                  ENTITY_ENTITY,
                  path + ".secondaryPillarSelector.entitySelector");
            }
          }
        }
      }
    }

    private void listChange(
        ValueSelectorConfig origin,
        DestinationSelectorConfig destination,
        String path,
        boolean autoConfigured) {
      var destinationValue = destination == null ? null : destination.getValueSelectorConfig();
      var destinationEntity = destination == null ? null : destination.getEntitySelectorConfig();
      var value = origin == null ? destinationValue : origin;
      for (var binding : variableBindings(destinationEntity, value, true)) {
        var variable = binding.variableDescriptor();
        manual(binding, nearby(origin), VALUE_VALUE, path + ".valueSelector");
        if (destination != null) {
          manual(
              binding,
              destination.getNearbySelectionConfig(),
              VALUE_DESTINATION,
              path + ".destinationSelector");
          manual(
              binding,
              nearby(destinationValue),
              VALUE_VALUE,
              path + ".destinationSelector.valueSelector");
        }
        // Sublist selectors have no nearby quick configuration support.
        if (autoConfigured) {
          global(variable, VALUE_DESTINATION, path);
        }
      }
    }

    private void listValues(
        ValueSelectorConfig first,
        ValueSelectorConfig second,
        String path,
        boolean autoConfigured) {
      for (var binding : variableBindings(null, first == null ? second : first, true)) {
        var variable = binding.variableDescriptor();
        manual(binding, nearby(first), VALUE_VALUE, path + ".originValueSelector");
        manual(binding, nearby(second), VALUE_VALUE, path + ".valueSelector");
        if (autoConfigured) {
          global(variable, VALUE_VALUE, path);
        }
      }
    }

    private void manualSubList(SubListSelectorConfig selector, String path) {
      if (selector != null) {
        for (var binding : variableBindings(null, selector.getValueSelectorConfig(), true)) {
          manual(binding, selector.getNearbySelectionConfig(), VALUE_VALUE, path);
          manual(
              binding,
              nearby(selector.getValueSelectorConfig()),
              VALUE_VALUE,
              path + ".valueSelector");
        }
      }
    }

    private void global(GenuineVariableDescriptor<?> variable, ArgumentShape shape, String path) {
      if (globalMeterClass != null
          && (!solutionDescriptor.hasBothBasicAndListVariables() || variable.isListVariable())
          && acceptsGlobalContract(variable, shape)) {
        profiles.add(
            new ConstructionHeuristicNearbyProfile(
                variable, globalMeterClass, shape, path + ".solver.nearbyDistanceMeterClass"));
      }
    }

    private boolean acceptsGlobalContract(
        GenuineVariableDescriptor<?> variable, ArgumentShape shape) {
      if (globalMeterContract == null) {
        return true;
      }
      var entityType = variable.getEntityDescriptor().getEntityClass();
      var valueType =
          variable instanceof ListVariableDescriptor<?> listVariable
              ? listVariable.getElementType()
              : variable.getVariablePropertyType();
      return switch (shape) {
        case ENTITY_VALUE -> globalMeterContract.canApplyTo(entityType, valueType);
        case ENTITY_ENTITY -> globalMeterContract.canApplyTo(entityType, entityType);
        case VALUE_VALUE -> globalMeterContract.canApplyTo(valueType, valueType);
        case VALUE_DESTINATION ->
            globalMeterContract.canApplyTo(valueType, valueType)
                && globalMeterContract.canApplyTo(valueType, entityType);
      };
    }

    @SuppressWarnings("unchecked")
    private void manual(
        VariableBinding binding, NearbySelectionConfig nearby, ArgumentShape shape, String path) {
      if (nearby == null || nearby.getNearbyDistanceMeterClass() == null) {
        return;
      }
      boolean entityOrigin = nearby.getOriginEntitySelectorConfig() != null;
      boolean valueOrigin =
          nearby.getOriginValueSelectorConfig() != null
              || nearby.getOriginSubListSelectorConfig() != null;
      if ((shape == ENTITY_ENTITY || shape == ENTITY_VALUE) ? !entityOrigin : !valueOrigin) {
        return; // An unrelated mimic origin cannot describe this CH assignment.
      }
      var variable = binding.variableDescriptor();
      if (entityOrigin
          && entityContexts(nearby.getOriginEntitySelectorConfig()).stream()
              .noneMatch(
                  originContext ->
                      NearbyDistanceMeterContract.mayShareRuntimeType(
                          originContext.getEntityClass(),
                          binding.entityContext().getEntityClass()))) {
        return;
      }
      if (valueOrigin) {
        var originValue = nearby.getOriginValueSelectorConfig();
        if (originValue == null) {
          var originSubList = effectiveSubList(nearby.getOriginSubListSelectorConfig());
          if (originSubList == null) {
            return;
          }
          originValue = originSubList.getValueSelectorConfig();
        }
        if (variableBindings(null, originValue, true).stream()
            .noneMatch(originBinding -> originBinding.variableDescriptor() == variable)) {
          return;
        }
      }
      profiles.add(
          new ConstructionHeuristicNearbyProfile(
              variable,
              (Class<? extends NearbyDistanceMeter<?, ?>>)
                  (Class<?>) nearby.getNearbyDistanceMeterClass(),
              shape,
              path + ".nearbySelection"));
    }

    private List<VariableBinding> variableBindings(
        EntitySelectorConfig entitySelector, ValueSelectorConfig valueSelector, boolean listOnly) {
      return variableBindings(entitySelector, valueSelector, listOnly, new HashSet<>());
    }

    private List<VariableBinding> variableBindings(
        EntitySelectorConfig entitySelector,
        ValueSelectorConfig valueSelector,
        boolean listOnly,
        HashSet<String> visitedValueRecorders) {
      List<VariableBinding> recordedBindings = null;
      if (valueSelector != null && valueSelector.getMimicSelectorRef() != null) {
        var reference = valueSelector.getMimicSelectorRef();
        if (!visitedValueRecorders.add(reference)) {
          return List.of();
        }
        var recording = valueRecorders.get(reference);
        if (recording == null) {
          return List.of();
        }
        recordedBindings =
            variableBindings(
                recording.owner(), recording.selector(), listOnly, visitedValueRecorders);
        if (entitySelector == null) {
          entitySelector = recording.owner();
        }
      }
      var variableName = valueSelector == null ? null : valueSelector.getVariableName();
      var result = new ArrayList<VariableBinding>();
      for (var parentContext : entityContexts(entitySelector)) {
        var context = contextualEntityDescriptor(parentContext, valueSelector);
        if (context == null) {
          continue;
        }
        if (recordedBindings != null) {
          for (var recordedBinding : recordedBindings) {
            var recordedVariable = recordedBinding.variableDescriptor();
            // Match ValueSelectorFactory's mimic compatibility check. Inherited variables keep
            // the identity of their declaring descriptor, even in a child entity context.
            if ((variableName == null || variableName.equals(recordedVariable.getVariableName()))
                && context.getGenuineVariableDescriptor(recordedVariable.getVariableName())
                    == recordedVariable) {
              result.add(new VariableBinding(context, recordedVariable));
            }
          }
        } else {
          for (var variable : context.getGenuineVariableDescriptorList()) {
            if ((!listOnly || variable.isListVariable())
                && (variableName == null || variable.getVariableName().equals(variableName))) {
              result.add(new VariableBinding(context, variable));
            }
          }
        }
      }
      return result.stream().distinct().toList();
    }

    private List<EntityDescriptor<?>> entityContexts(EntitySelectorConfig selector) {
      var effective = effectiveEntity(selector);
      if (selector != null && effective == null) {
        return List.of();
      }
      var entityClass = effective == null ? null : effective.getEntityClass();
      if (entityClass != null) {
        // A registered parent may have only shadow variables. It is still a valid selector
        // context when its value selector downcasts to a genuine child entity.
        var descriptor = solutionDescriptor.getEntityDescriptorStrict(entityClass);
        return descriptor == null ? List.of() : List.of(descriptor);
      }
      return solutionDescriptor.getGenuineEntityDescriptors().stream()
          .<EntityDescriptor<?>>map(descriptor -> descriptor)
          .toList();
    }

    private EntityDescriptor<?> contextualEntityDescriptor(
        EntityDescriptor<?> parentContext, ValueSelectorConfig valueSelector) {
      var downcastClass = valueSelector == null ? null : valueSelector.getDowncastEntityClass();
      if (downcastClass == null) {
        return parentContext;
      }
      // Discovery must not broaden an invalid downcast. The selector factory supplies its normal
      // startup diagnostic for unknown classes or a downcast outside the parent hierarchy.
      if (!parentContext.getEntityClass().isAssignableFrom(downcastClass)) {
        return null;
      }
      return solutionDescriptor.getEntityDescriptorStrict(downcastClass);
    }

    private EntitySelectorConfig effectiveEntity(EntitySelectorConfig selector) {
      var visited = new HashSet<String>();
      while (selector != null && selector.getMimicSelectorRef() != null) {
        if (!visited.add(selector.getMimicSelectorRef())) {
          return null;
        }
        var recorder = entityRecorders.get(selector.getMimicSelectorRef());
        if (recorder == null) {
          return null;
        }
        selector = recorder;
      }
      return selector;
    }

    private ValueSelectorConfig effectiveValue(ValueSelectorConfig selector) {
      var visited = new HashSet<String>();
      while (selector != null
          && selector.getMimicSelectorRef() != null
          && selector.getVariableName() == null) {
        if (!visited.add(selector.getMimicSelectorRef())) {
          return null;
        }
        var binding = valueRecorders.get(selector.getMimicSelectorRef());
        if (binding == null) {
          return null;
        }
        selector = binding.selector();
      }
      return selector;
    }

    private String variableName(ValueSelectorConfig selector) {
      var effective = effectiveValue(selector);
      return effective == null ? null : effective.getVariableName();
    }

    private SubListSelectorConfig effectiveSubList(SubListSelectorConfig selector) {
      var visited = new HashSet<String>();
      while (selector != null && selector.getMimicSelectorRef() != null) {
        if (!visited.add(selector.getMimicSelectorRef())) {
          return null;
        }
        var recorder = subListRecorders.get(selector.getMimicSelectorRef());
        if (recorder == null) {
          return null;
        }
        selector = recorder;
      }
      return selector;
    }

    private static NearbySelectionConfig nearby(EntitySelectorConfig selector) {
      return selector == null ? null : selector.getNearbySelectionConfig();
    }

    private static NearbySelectionConfig nearby(ValueSelectorConfig selector) {
      return selector == null ? null : selector.getNearbySelectionConfig();
    }

    private record ValueBinding(ValueSelectorConfig selector, EntitySelectorConfig owner) {}

    private record VariableBinding(
        EntityDescriptor<?> entityContext, GenuineVariableDescriptor<?> variableDescriptor) {}
  }
}
