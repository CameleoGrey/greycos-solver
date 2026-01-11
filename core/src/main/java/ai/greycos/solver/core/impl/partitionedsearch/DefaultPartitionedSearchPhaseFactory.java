package ai.greycos.solver.core.impl.partitionedsearch;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import ai.greycos.solver.core.config.phase.PhaseConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import ai.greycos.solver.core.impl.phase.AbstractPhaseFactory;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.termination.SolverTermination;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for building partitioned search phases.
 *
 * <p>Constructs DefaultPartitionedSearchPhase with configured partitioner, thread pool, and child
 * phase configurations.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
public class DefaultPartitionedSearchPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, PartitionedSearchPhaseConfig> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultPartitionedSearchPhaseFactory.class);

  public DefaultPartitionedSearchPhaseFactory(PartitionedSearchPhaseConfig phaseConfig) {
    super(phaseConfig);
  }

  @Override
  public PartitionedSearchPhase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {

    HeuristicConfigPolicy<Solution_> phaseConfigPolicy =
        solverConfigPolicy.createPhaseConfigPolicy();

    ThreadFactory threadFactory =
        solverConfigPolicy.buildThreadFactory(ChildThreadType.PART_THREAD);

    PhaseTermination<Solution_> phaseTermination =
        buildPhaseTermination(phaseConfigPolicy, solverTermination);

    Integer resolvedActiveThreadCount =
        resolveActiveThreadCount(phaseConfig.getRunnablePartThreadLimit());

    SolutionPartitioner<Solution_> solutionPartitioner = buildSolutionPartitioner(phaseConfig);

    List<PhaseConfig> phaseConfigList_ = phaseConfig.getPhaseConfigList();
    if (ConfigUtils.isEmptyCollection(phaseConfigList_)) {
      phaseConfigList_ =
          Arrays.asList(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig());
    }

    return new DefaultPartitionedSearchPhase.Builder<Solution_>(
            phaseIndex,
            solverConfigPolicy.getLogIndentation(),
            phaseTermination,
            phaseConfigPolicy,
            solutionPartitioner,
            threadFactory,
            resolvedActiveThreadCount,
            phaseConfigList_,
            solverTermination)
        .enableAssertions(phaseConfigPolicy.getEnvironmentMode())
        .build();
  }

  private SolutionPartitioner<Solution_> buildSolutionPartitioner(
      @NonNull PartitionedSearchPhaseConfig phaseConfig) {
    Class<? extends SolutionPartitioner<?>> solutionPartitionerClass =
        phaseConfig.getSolutionPartitionerClass();

    if (solutionPartitionerClass == null) {
      if (phaseConfig.getSolutionPartitionerCustomProperties() != null) {
        throw new IllegalStateException(
            "If there is no solutionPartitionerClass ("
                + solutionPartitionerClass
                + "), then there can be no solutionPartitionerCustomProperties ("
                + phaseConfig.getSolutionPartitionerCustomProperties()
                + ") either.");
      }
      throw new UnsupportedOperationException(
          "A solutionPartitionerClass must be specified. Generic partitioner is not yet implemented.");
    }

    SolutionPartitioner<?> solutionPartitioner =
        ConfigUtils.newInstance(phaseConfig, "solutionPartitionerClass", solutionPartitionerClass);
    ConfigUtils.applyCustomProperties(
        solutionPartitioner,
        "solutionPartitionerClass",
        phaseConfig.getSolutionPartitionerCustomProperties(),
        "solutionPartitionerCustomProperties");
    return (SolutionPartitioner<Solution_>) solutionPartitioner;
  }

  private Integer resolveActiveThreadCount(@Nullable String runnablePartThreadLimit) {
    int availableProcessorCount = Runtime.getRuntime().availableProcessors();
    Integer resolvedActiveThreadCount;
    final boolean threadLimitNullOrAuto =
        runnablePartThreadLimit == null
            || runnablePartThreadLimit.equals(
                PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_AUTO);
    if (threadLimitNullOrAuto) {
      resolvedActiveThreadCount = Math.max(1, availableProcessorCount - 2);
    } else if (runnablePartThreadLimit.equals(
        PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED)) {
      resolvedActiveThreadCount = null;
    } else {
      resolvedActiveThreadCount =
          ConfigUtils.resolvePoolSize(
              "runnablePartThreadLimit",
              runnablePartThreadLimit,
              PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_AUTO,
              PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED);
      if (resolvedActiveThreadCount < 1) {
        throw new IllegalArgumentException(
            "The runnablePartThreadLimit ("
                + runnablePartThreadLimit
                + ") resulted in a resolvedActiveThreadCount ("
                + resolvedActiveThreadCount
                + ") that is lower than 1.");
      }
      if (resolvedActiveThreadCount > availableProcessorCount) {
        logger.debug(
            "The resolvedActiveThreadCount ({}) is higher than "
                + "the availableProcessorCount ({}), so the JVM will "
                + "round-robin the CPU instead.",
            resolvedActiveThreadCount,
            availableProcessorCount);
      }
    }
    return resolvedActiveThreadCount;
  }
}
