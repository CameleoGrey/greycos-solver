package greycos.solver.core.impl.constructionheuristic;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import greycos.solver.core.config.constructionheuristic.placer.EntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySorterManner;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSorterManner;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhase.DefaultConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.constructionheuristic.decider.ConstructionHeuristicDecider;
import greycos.solver.core.impl.constructionheuristic.decider.MultiThreadedConstructionHeuristicDecider;
import greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForagerFactory;
import greycos.solver.core.impl.constructionheuristic.decider.forager.RandomAssignmentConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.placer.EntityPlacer;
import greycos.solver.core.impl.constructionheuristic.placer.EntityPlacerFactory;
import greycos.solver.core.impl.constructionheuristic.placer.PooledEntityPlacerFactory;
import greycos.solver.core.impl.constructionheuristic.placer.QueuedEntityPlacerFactory;
import greycos.solver.core.impl.constructionheuristic.placer.QueuedValuePlacerFactory;
import greycos.solver.core.impl.constructionheuristic.placer.RandomAssignmentEntityPlacer;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.phase.AbstractPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.termination.SolverTermination;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

public class DefaultConstructionHeuristicPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, ConstructionHeuristicPhaseConfig> {

  public DefaultConstructionHeuristicPhaseFactory(ConstructionHeuristicPhaseConfig phaseConfig) {
    super(phaseConfig.copyConfig());
  }

  public final DefaultConstructionHeuristicPhaseBuilder<Solution_> getBuilder(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      SolverTermination<Solution_> solverTermination) {
    var constructionHeuristicType_ =
        Objects.requireNonNullElse(
            phaseConfig.getConstructionHeuristicType(),
            ConstructionHeuristicType.ALLOCATE_ENTITY_FROM_QUEUE);
    var entitySorterManner =
        Objects.requireNonNullElse(
            phaseConfig.getEntitySorterManner(),
            constructionHeuristicType_.getDefaultEntitySorterManner());
    var valueSorterManner =
        Objects.requireNonNullElse(
            phaseConfig.getValueSorterManner(),
            constructionHeuristicType_.getDefaultValueSorterManner());
    var environmentMode = resolveEnvironmentMode(solverConfigPolicy);
    var moveThreadCount =
        resolveMoveThreadCount(
            phaseConfig.getMoveThreadCount(), solverConfigPolicy.getMoveThreadCount(), true);
    var nearbySelectionSize =
        Objects.requireNonNullElse(
            phaseConfig.getNearbySelectionSize(),
            solverConfigPolicy.getConstructionHeuristicNearbySelectionSize());
    if (nearbySelectionSize < 1) {
      throw new IllegalArgumentException(
          "The construction heuristic nearbySelectionSize (%d) must be at least 1."
              .formatted(nearbySelectionSize));
    }
    var phaseConfigPolicyBuilder =
        solverConfigPolicy
            .cloneBuilder()
            .withEnvironmentMode(environmentMode)
            .withMoveThreadCount(moveThreadCount)
            .withNonDoableCandidateRetentionEnabled(false)
            .withReinitializeVariableFilterEnabled(true)
            .withUnassignedValuesAllowed(true)
            .withEntitySorterManner(entitySorterManner)
            .withValueSorterManner(valueSorterManner)
            .withConstructionHeuristicNearbyAutoConfigurationEnabled(
                Boolean.TRUE.equals(phaseConfig.getNearbySelectionAutoConfigurationEnabled()))
            .withConstructionHeuristicNearbySelectionSize(nearbySelectionSize)
            // Local-search union augmentation duplicates candidates and has no construction
            // evaluation boundary. Construction uses its independently resolved profiles.
            .withNearbyDistanceMeterClass(null);
    var phaseConfigPolicy = phaseConfigPolicyBuilder.build();
    if (constructionHeuristicType_ == ConstructionHeuristicType.RANDOM_ASSIGNMENT) {
      validateRandomAssignmentConfiguration(moveThreadCount);
      return createBuilder(
          phaseConfigPolicy,
          solverTermination,
          phaseIndex,
          lastInitializingPhase,
          new RandomAssignmentEntityPlacer<>());
    }
    var entityPlacerConfig_ =
        getValidEntityPlacerConfig()
            .orElseGet(
                () ->
                    buildDefaultEntityPlacerConfig(phaseConfigPolicy, constructionHeuristicType_));
    var entityPlacer =
        EntityPlacerFactory.<Solution_>create(entityPlacerConfig_)
            .buildEntityPlacer(phaseConfigPolicy);
    validateFinitePlacementSelectors(entityPlacer, phaseConfigPolicy);
    return createBuilder(
        phaseConfigPolicy, solverTermination, phaseIndex, lastInitializingPhase, entityPlacer);
  }

  private void validateRandomAssignmentConfiguration(Integer moveThreadCount) {
    if (moveThreadCount != null) {
      throw new IllegalArgumentException(
          "RANDOM_ASSIGNMENT construction does not support moveThreadCount (%s). Set its moveThreadCount to NONE."
              .formatted(moveThreadCount));
    }
    var conflicts = new LinkedHashMap<String, Object>();
    if (phaseConfig.getEntityPlacerConfig() != null) {
      conflicts.put("entityPlacerConfig", phaseConfig.getEntityPlacerConfig());
    }
    if (!ConfigUtils.isEmptyCollection(phaseConfig.getMoveSelectorConfigList())) {
      conflicts.put("moveSelectorConfigList", phaseConfig.getMoveSelectorConfigList());
    }
    if (phaseConfig.getForagerConfig() != null) {
      conflicts.put("foragerConfig", phaseConfig.getForagerConfig());
    }
    if (phaseConfig.getEntitySorterManner() != null
        && phaseConfig.getEntitySorterManner() != EntitySorterManner.NONE) {
      conflicts.put("entitySorterManner", phaseConfig.getEntitySorterManner());
    }
    if (phaseConfig.getValueSorterManner() != null
        && phaseConfig.getValueSorterManner() != ValueSorterManner.NONE) {
      conflicts.put("valueSorterManner", phaseConfig.getValueSorterManner());
    }
    if (Boolean.TRUE.equals(phaseConfig.getNearbySelectionAutoConfigurationEnabled())) {
      conflicts.put(
          "nearbySelectionAutoConfigurationEnabled",
          phaseConfig.getNearbySelectionAutoConfigurationEnabled());
    }
    if (phaseConfig.getNearbySelectionSize() != null) {
      conflicts.put("nearbySelectionSize", phaseConfig.getNearbySelectionSize());
    }
    if (!conflicts.isEmpty()) {
      throw new IllegalArgumentException(
          "RANDOM_ASSIGNMENT construction has incompatible settings (%s). Remove these settings; this construction heuristic samples one complete assignment directly."
              .formatted(conflicts));
    }
  }

  private void validateFinitePlacementSelectors(
      EntityPlacer<Solution_> entityPlacer, HeuristicConfigPolicy<Solution_> configPolicy) {
    var foragerConfig = phaseConfig.getForagerConfig();
    if (foragerConfig != null && foragerConfig.getForagerClass() != null) {
      // A custom forager owns its stopping contract, including finite evaluation of random
      // candidates. A built-in pickEarlyType does not describe that custom contract.
      return;
    }
    var earlyType = foragerConfig == null ? null : foragerConfig.getPickEarlyType();
    boolean earlyPick =
        earlyType != null
            ? earlyType != ConstructionHeuristicPickEarlyType.NEVER
            : configPolicy.getInitializingScoreTrend().isOnlyDown();
    if (!earlyPick) {
      for (var selector : entityPlacer.getCandidateMoveSelectors()) {
        if (selector.isNeverEnding()) {
          throw new IllegalArgumentException(
              "The construction heuristic candidate selector (%s) is never-ending. Configure a finite selectedCountLimit or a built-in pickEarlyType; random nearby distribution caps do not limit the number of evaluated moves."
                  .formatted(selector));
        }
      }
    }
  }

  protected DefaultConstructionHeuristicPhaseBuilder<Solution_> createBuilder(
      HeuristicConfigPolicy<Solution_> phaseConfigPolicy,
      SolverTermination<Solution_> solverTermination,
      int phaseIndex,
      boolean lastInitializingPhase,
      EntityPlacer<Solution_> entityPlacer) {
    var phaseTermination = buildPhaseTermination(phaseConfigPolicy, solverTermination);
    return new DefaultConstructionHeuristicPhaseBuilder<>(
            phaseIndex,
            lastInitializingPhase,
            phaseConfigPolicy.getEnvironmentMode(),
            phaseConfigPolicy.getLogIndentation(),
            phaseTermination,
            entityPlacer,
            buildDecider(phaseConfigPolicy, phaseTermination))
        .enableAssertions();
  }

  @Override
  public ConstructionHeuristicPhase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {
    return getBuilder(phaseIndex, lastInitializingPhase, solverConfigPolicy, solverTermination)
        .build();
  }

  private Optional<EntityPlacerConfig<?>> getValidEntityPlacerConfig() {
    var entityPlacerConfig = phaseConfig.getEntityPlacerConfig();
    if (entityPlacerConfig == null) {
      return Optional.empty();
    }
    if (phaseConfig.getConstructionHeuristicType() != null) {
      throw new IllegalArgumentException(
          "The constructionHeuristicType (%s) must not be configured if the entityPlacerConfig (%s) is explicitly configured."
              .formatted(phaseConfig.getConstructionHeuristicType(), entityPlacerConfig));
    }
    var moveSelectorConfigList = phaseConfig.getMoveSelectorConfigList();
    if (moveSelectorConfigList != null) {
      throw new IllegalArgumentException(
          "The moveSelectorConfigList (%s) cannot be configured if the entityPlacerConfig (%s) is explicitly configured."
              .formatted(moveSelectorConfigList, entityPlacerConfig));
    }
    return Optional.of(entityPlacerConfig);
  }

  private EntityPlacerConfig<?> buildDefaultEntityPlacerConfig(
      HeuristicConfigPolicy<Solution_> configPolicy,
      ConstructionHeuristicType constructionHeuristicType) {
    return findValidListVariableDescriptor(configPolicy.getSolutionDescriptor())
        .<EntityPlacerConfig<?>>map(
            listVariableDescriptor ->
                buildConfiguredListVariablePlacer(configPolicy, listVariableDescriptor))
        .orElseGet(() -> buildUnfoldedEntityPlacerConfig(configPolicy, constructionHeuristicType));
  }

  private QueuedValuePlacerConfig buildConfiguredListVariablePlacer(
      HeuristicConfigPolicy<Solution_> configPolicy, ListVariableDescriptor<?> variableDescriptor) {
    var placer =
        (QueuedValuePlacerConfig)
            buildListVariableQueuedValuePlacerConfig(configPolicy, variableDescriptor);
    if (ConfigUtils.isEmptyCollection(phaseConfig.getMoveSelectorConfigList())) {
      return placer;
    }
    var moveConfig = (MoveSelectorConfig<?>) checkSingleMoveSelectorConfig().copyConfig();
    var leaves = new java.util.ArrayList<MoveSelectorConfig>();
    moveConfig.extractLeafMoveSelectorConfigsIntoList(leaves);
    for (var leaf : leaves) {
      if (!(leaf instanceof ListChangeMoveSelectorConfig listChange)) {
        throw new IllegalArgumentException(
            "The list construction heuristic candidate (%s) must be a listChangeMoveSelector, optionally nested in a composite selector."
                .formatted(leaf));
      }
      if (listChange.getValueSelectorConfig() != null) {
        throw new IllegalArgumentException(
            "The list construction heuristic listChangeMoveSelector (%s) already contains a source valueSelector. Configure a queuedValuePlacer explicitly when source selection is customized."
                .formatted(listChange));
      }
      listChange.setValueSelectorConfig(
          new ValueSelectorConfig(variableDescriptor.getVariableName())
              .withMimicSelectorRef(placer.getValueSelectorConfig().getId()));
    }
    placer.setMoveSelectorConfig(moveConfig);
    return placer;
  }

  private Optional<ListVariableDescriptor<?>> findValidListVariableDescriptor(
      SolutionDescriptor<Solution_> solutionDescriptor) {
    var listVariableDescriptor = solutionDescriptor.getListVariableDescriptor();
    if (listVariableDescriptor == null) {
      return Optional.empty();
    }
    // When an entity has both list and basic variables,
    // the CH configuration will require two separate placers to initialize each variable,
    // which cannot be deduced automatically by default, since a single placer would be returned
    if (listVariableDescriptor.getEntityDescriptor().hasAnyBasicVariables()) {
      throw new IllegalArgumentException(
          """
          The entity (%s) has both basic and list variables and cannot be deduced automatically.
          Maybe customize the phase configuration and add separate construction heuristic phases for each variable.\
          """
              .formatted(listVariableDescriptor.getEntityDescriptor().getEntityClass()));
    }
    return Optional.of(listVariableDescriptor);
  }

  @SuppressWarnings("rawtypes")
  public static EntityPlacerConfig buildListVariableQueuedValuePlacerConfig(
      HeuristicConfigPolicy<?> configPolicy, ListVariableDescriptor<?> variableDescriptor) {
    var mimicSelectorId = variableDescriptor.getVariableName();

    // Prepare recording ValueSelector config.
    var mimicRecordingValueSelectorConfig =
        new ValueSelectorConfig(variableDescriptor.getVariableName()).withId(mimicSelectorId);
    if (ValueSelectorConfig.hasSorter(configPolicy.getValueSorterManner(), variableDescriptor)) {
      mimicRecordingValueSelectorConfig =
          mimicRecordingValueSelectorConfig
              .withCacheType(SelectionCacheType.PHASE)
              .withSelectionOrder(SelectionOrder.SORTED)
              .withSorterManner(configPolicy.getValueSorterManner());
    }
    // Prepare replaying ValueSelector config.
    var mimicReplayingValueSelectorConfig =
        new ValueSelectorConfig()
            .withMimicSelectorRef(mimicSelectorId)
            .withVariableName(variableDescriptor.getVariableName());

    // ListChangeMoveSelector uses the replaying ValueSelector.
    var listChangeMoveSelectorConfig =
        new ListChangeMoveSelectorConfig()
            .withValueSelectorConfig(mimicReplayingValueSelectorConfig);

    // Finally, QueuedValuePlacer uses the recording ValueSelector and a ListChangeMoveSelector.
    // The ListChangeMoveSelector's replaying ValueSelector mimics the QueuedValuePlacer's recording
    // ValueSelector.
    return new QueuedValuePlacerConfig()
        .withEntityClass(variableDescriptor.getEntityDescriptor().getEntityClass())
        .withValueSelectorConfig(mimicRecordingValueSelectorConfig)
        .withMoveSelectorConfig(listChangeMoveSelectorConfig);
  }

  protected ConstructionHeuristicDecider<Solution_> buildDecider(
      HeuristicConfigPolicy<Solution_> configPolicy, PhaseTermination<Solution_> termination) {
    var forager = buildForager(configPolicy);
    var moveThreadCount = configPolicy.getMoveThreadCount();
    var decider =
        (moveThreadCount == null)
            ? new ConstructionHeuristicDecider<>(
                configPolicy.getLogIndentation(), termination, forager)
            : new MultiThreadedConstructionHeuristicDecider<>(
                configPolicy.getLogIndentation(),
                termination,
                forager,
                configPolicy.buildThreadFactory(ChildThreadType.MOVE_THREAD),
                moveThreadCount,
                moveThreadCount
                    * (configPolicy.getMoveThreadBufferSize() != null
                        ? configPolicy.getMoveThreadBufferSize()
                        : 10));
    decider.enableAssertions(configPolicy.getEnvironmentMode());
    return decider;
  }

  protected ConstructionHeuristicForager<Solution_> buildForager(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    if (phaseConfig.getConstructionHeuristicType() == ConstructionHeuristicType.RANDOM_ASSIGNMENT) {
      return new RandomAssignmentConstructionHeuristicForager<>();
    }
    var foragerConfig_ =
        Objects.requireNonNullElseGet(
            phaseConfig.getForagerConfig(), ConstructionHeuristicForagerConfig::new);
    return ConstructionHeuristicForagerFactory.<Solution_>create(foragerConfig_)
        .buildForager(configPolicy);
  }

  private EntityPlacerConfig<?> buildUnfoldedEntityPlacerConfig(
      HeuristicConfigPolicy<Solution_> phaseConfigPolicy,
      ConstructionHeuristicType constructionHeuristicType) {
    return switch (constructionHeuristicType) {
      case FIRST_FIT,
          FIRST_FIT_DECREASING,
          WEAKEST_FIT,
          WEAKEST_FIT_DECREASING,
          STRONGEST_FIT,
          STRONGEST_FIT_DECREASING,
          ALLOCATE_ENTITY_FROM_QUEUE -> {
        if (!ConfigUtils.isEmptyCollection(phaseConfig.getMoveSelectorConfigList())) {
          yield QueuedEntityPlacerFactory.unfoldNew(
              phaseConfigPolicy, phaseConfig.getMoveSelectorConfigList());
        }
        yield new QueuedEntityPlacerConfig();
      }
      case ALLOCATE_TO_VALUE_FROM_QUEUE -> {
        if (!ConfigUtils.isEmptyCollection(phaseConfig.getMoveSelectorConfigList())) {
          yield QueuedValuePlacerFactory.unfoldNew(
              phaseConfigPolicy, checkSingleMoveSelectorConfig());
        }
        yield new QueuedValuePlacerConfig();
      }
      case CHEAPEST_INSERTION, ALLOCATE_FROM_POOL -> {
        if (!ConfigUtils.isEmptyCollection(phaseConfig.getMoveSelectorConfigList())) {
          yield PooledEntityPlacerFactory.unfoldNew(
              phaseConfigPolicy, checkSingleMoveSelectorConfig());
        }
        yield new PooledEntityPlacerConfig();
      }
      case RANDOM_ASSIGNMENT ->
          throw new IllegalStateException("RANDOM_ASSIGNMENT uses its complete-assignment placer.");
    };
  }

  private MoveSelectorConfig<?>
      checkSingleMoveSelectorConfig() { // Non-null guaranteed by the caller.
    var moveSelectorConfigList = Objects.requireNonNull(phaseConfig.getMoveSelectorConfigList());
    if (moveSelectorConfigList.size() != 1) {
      throw new IllegalArgumentException(
          """
          For the constructionHeuristicType (%s), the moveSelectorConfigList (%s) must be a singleton.
          Use a single %s or %s element to nest multiple MoveSelectors.\
          """
              .formatted(
                  phaseConfig.getConstructionHeuristicType(),
                  phaseConfig.getMoveSelectorConfigList(),
                  UnionMoveSelectorConfig.class.getSimpleName(),
                  CartesianProductMoveSelectorConfig.class.getSimpleName()));
    }

    return moveSelectorConfigList.get(0);
  }
}
