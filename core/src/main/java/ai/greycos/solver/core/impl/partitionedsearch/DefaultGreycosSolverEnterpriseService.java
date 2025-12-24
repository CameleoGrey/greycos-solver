package ai.greycos.solver.core.impl.partitionedsearch;

import java.util.function.BiFunction;
import java.util.function.Function;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.enterprise.GreycosSolverEnterpriseService;
import ai.greycos.solver.core.impl.constructionheuristic.decider.ConstructionHeuristicDecider;
import ai.greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.declarative.DefaultTopologicalOrderGraph;
import ai.greycos.solver.core.impl.domain.variable.declarative.TopologicalOrderGraph;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.DestinationSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import ai.greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import ai.greycos.solver.core.impl.heuristic.selector.value.ValueSelector;
import ai.greycos.solver.core.impl.localsearch.decider.LocalSearchDecider;
import ai.greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.greycos.solver.core.impl.neighborhood.MoveRepository;
import ai.greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecallerFactory;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.termination.SolverTermination;

/**
 * Default implementation of GreycosSolverEnterpriseService for community edition.
 *
 * <p>Provides partitioned search functionality without requiring an enterprise license.
 */
public final class DefaultGreycosSolverEnterpriseService implements GreycosSolverEnterpriseService {

  private static final String VERSION = "1.0.0-community";

  private final Function<Class<?>, String> versionStringFunction;

  private DefaultGreycosSolverEnterpriseService(Function<Class<?>, String> versionStringFunction) {
    this.versionStringFunction = versionStringFunction;
  }

  /**
   * Gets the singleton instance.
   *
   * @param versionStringFunction Function to get version strings
   * @return The singleton instance
   */
  public static DefaultGreycosSolverEnterpriseService getInstance(
      Function<Class<?>, String> versionStringFunction) {
    return new DefaultGreycosSolverEnterpriseService(versionStringFunction);
  }

  @Override
  public TopologicalOrderGraph buildTopologyGraph(int size) {
    // Return a simple topological order graph for community edition
    return new DefaultTopologicalOrderGraph(size);
  }

  @Override
  public GreycosSolverEnterpriseService.ConstraintProviderNodeSharer createNodeSharer() {
    // Return a simple node sharer that doesn't apply node sharing
    return new ConstraintProviderNodeSharer() {
      @Override
      public <T extends ConstraintProvider> Class<T> buildNodeSharedConstraintProvider(
          Class<T> constraintProviderClass) {
        // Simply return the original class without node sharing
        return constraintProviderClass;
      }
    };
  }

  @Override
  public <Solution_> ConstructionHeuristicDecider<Solution_> buildConstructionHeuristic(
      PhaseTermination<Solution_> termination,
      ConstructionHeuristicForager<Solution_> forager,
      HeuristicConfigPolicy<Solution_> configPolicy) {
    throw new UnsupportedOperationException(
        "Construction Heuristic with custom forager is an enterprise feature.");
  }

  @Override
  public <Solution_> LocalSearchDecider<Solution_> buildLocalSearch(
      int moveThreadCount,
      PhaseTermination<Solution_> termination,
      MoveRepository<Solution_> moveRepository,
      Acceptor<Solution_> acceptor,
      LocalSearchForager<Solution_> forager,
      EnvironmentMode environmentMode,
      HeuristicConfigPolicy<Solution_> configPolicy) {
    throw new UnsupportedOperationException(
        "Local Search with moveThreadCount is an enterprise feature.");
  }

  @Override
  public <Solution_> PartitionedSearchPhase<Solution_> buildPartitionedSearch(
      int phaseIndex,
      PartitionedSearchPhaseConfig phaseConfig,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      SolverTermination<Solution_> solverTermination,
      BiFunction<
              HeuristicConfigPolicy<Solution_>,
              SolverTermination<Solution_>,
              PhaseTermination<Solution_>>
          phaseTerminationFunction) {
    // Build phase termination
    PhaseTermination<Solution_> phaseTermination =
        phaseTerminationFunction.apply(solverConfigPolicy, solverTermination);

    // Build best solution recaller
    BestSolutionRecaller<Solution_> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(solverConfigPolicy.getEnvironmentMode());

    return new DefaultPartitionedSearchPhase.Builder<Solution_>(
            phaseIndex,
            solverConfigPolicy.getLogIndentation(),
            phaseTermination,
            solverConfigPolicy,
            buildSolutionPartitioner(phaseConfig),
            solverConfigPolicy.buildThreadFactory(
                ai.greycos.solver.core.impl.solver.thread.ChildThreadType.PART_THREAD),
            resolveActiveThreadCount(phaseConfig.getRunnablePartThreadLimit()),
            phaseConfig.getPhaseConfigList(),
            solverTermination)
        .enableAssertions(solverConfigPolicy.getEnvironmentMode())
        .build();
  }

  private <Solution_> SolutionPartitioner<Solution_> buildSolutionPartitioner(
      PartitionedSearchPhaseConfig phaseConfig) {
    Class<? extends SolutionPartitioner<?>> solutionPartitionerClass =
        phaseConfig.getSolutionPartitionerClass();

    if (solutionPartitionerClass == null) {
      throw new IllegalStateException(
          "The partitionedSearchPhaseConfig does not specify a solutionPartitionerClass.");
    }

    try {
      @SuppressWarnings("unchecked")
      SolutionPartitioner<Solution_> solutionPartitioner =
          (SolutionPartitioner<Solution_>)
              solutionPartitionerClass.getDeclaredConstructor().newInstance();

      // Apply custom properties if any
      var customProperties = phaseConfig.getSolutionPartitionerCustomProperties();
      if (customProperties != null && !customProperties.isEmpty()) {
        applyCustomProperties(solutionPartitioner, customProperties);
      }

      return solutionPartitioner;
    } catch (Exception e) {
      throw new IllegalStateException(
          "The partitionedSearchPhaseConfig has a solutionPartitionerClass ("
              + solutionPartitionerClass
              + ") that could not be instantiated.",
          e);
    }
  }

  private void applyCustomProperties(
      SolutionPartitioner<?> solutionPartitioner, java.util.Map<String, String> customProperties) {
    for (java.util.Map.Entry<String, String> entry : customProperties.entrySet()) {
      String propertyName = entry.getKey();
      String propertyValue = entry.getValue();

      try {
        var setter = findSetterMethod(solutionPartitioner, propertyName);
        if (setter != null) {
          Class<?> paramType = setter.getParameterTypes()[0];
          Object convertedValue = convertValue(propertyValue, paramType);
          setter.invoke(solutionPartitioner, convertedValue);
        }
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to apply custom property ("
                + propertyName
                + "="
                + propertyValue
                + ") to solutionPartitioner ("
                + solutionPartitioner.getClass().getName()
                + ").",
            e);
      }
    }
  }

  private java.lang.reflect.Method findSetterMethod(Object target, String propertyName) {
    String setterName =
        "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);

    for (java.lang.reflect.Method method : target.getClass().getMethods()) {
      if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
        return method;
      }
    }
    return null;
  }

  private Object convertValue(String value, Class<?> targetType) {
    if (targetType == String.class) {
      return value;
    } else if (targetType == Integer.class || targetType == int.class) {
      return Integer.parseInt(value);
    } else if (targetType == Long.class || targetType == long.class) {
      return Long.parseLong(value);
    } else if (targetType == Double.class || targetType == double.class) {
      return Double.parseDouble(value);
    } else if (targetType == Boolean.class || targetType == boolean.class) {
      return Boolean.parseBoolean(value);
    } else {
      throw new IllegalArgumentException("Unsupported property type: " + targetType.getName());
    }
  }

  private Integer resolveActiveThreadCount(String runnablePartThreadLimit) {
    if (runnablePartThreadLimit == null) {
      return null;
    }
    if (PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_AUTO.equals(runnablePartThreadLimit)) {
      return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }
    if (PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED.equals(
        runnablePartThreadLimit)) {
      return null;
    }
    try {
      return Integer.parseInt(runnablePartThreadLimit);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "The runnablePartThreadLimit (" + runnablePartThreadLimit + ") is not a valid integer.",
          e);
    }
  }

  @Override
  public <Solution_> EntitySelector<Solution_> applyNearbySelection(
      EntitySelectorConfig entitySelectorConfig,
      HeuristicConfigPolicy<Solution_> configPolicy,
      NearbySelectionConfig nearbySelectionConfig,
      SelectionCacheType minimumCacheType,
      SelectionOrder resolvedSelectionOrder,
      EntitySelector<Solution_> entitySelector) {
    throw new UnsupportedOperationException("Nearby selection is an enterprise feature.");
  }

  @Override
  public <Solution_> ValueSelector<Solution_> applyNearbySelection(
      ValueSelectorConfig valueSelectorConfig,
      HeuristicConfigPolicy<Solution_> configPolicy,
      EntityDescriptor<Solution_> entityDescriptor,
      SelectionCacheType minimumCacheType,
      SelectionOrder resolvedSelectionOrder,
      ValueSelector<Solution_> valueSelector) {
    throw new UnsupportedOperationException("Nearby selection is an enterprise feature.");
  }

  @Override
  public <Solution_> SubListSelector<Solution_> applyNearbySelection(
      SubListSelectorConfig subListSelectorConfig,
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      SelectionOrder resolvedSelectionOrder,
      RandomSubListSelector<Solution_> subListSelector) {
    throw new UnsupportedOperationException("Nearby selection is an enterprise feature.");
  }

  @Override
  public <Solution_> DestinationSelector<Solution_> applyNearbySelection(
      DestinationSelectorConfig destinationSelectorConfig,
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      SelectionOrder resolvedSelectionOrder,
      ElementDestinationSelector<Solution_> destinationSelector) {
    throw new UnsupportedOperationException("Nearby selection is an enterprise feature.");
  }

  @Override
  public <Solution_>
      AbstractMoveSelectorFactory<Solution_, MultistageMoveSelectorConfig>
          buildBasicMultistageMoveSelectorFactory(MultistageMoveSelectorConfig moveSelectorConfig) {
    throw new UnsupportedOperationException("Multistage move selector is an enterprise feature.");
  }

  @Override
  public <Solution_>
      AbstractMoveSelectorFactory<Solution_, ListMultistageMoveSelectorConfig>
          buildListMultistageMoveSelectorFactory(
              ListMultistageMoveSelectorConfig moveSelectorConfig) {
    throw new UnsupportedOperationException("Multistage move selector is an enterprise feature.");
  }
}
