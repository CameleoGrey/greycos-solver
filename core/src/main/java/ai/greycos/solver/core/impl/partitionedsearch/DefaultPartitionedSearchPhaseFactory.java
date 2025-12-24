package ai.greycos.solver.core.impl.partitionedsearch;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadFactory;

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

/**
 * Factory for building partitioned search phases.
 *
 * @param <Solution_> solution type, class with {@link
 *     ai.greycos.solver.core.api.domain.solution.PlanningSolution} annotation
 */
public class DefaultPartitionedSearchPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, PartitionedSearchPhaseConfig> {

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
      throw new IllegalStateException(
          "The partitionedSearchPhaseConfig ("
              + phaseConfig
              + ") does not specify a solutionPartitionerClass.");
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

  private Integer resolveActiveThreadCount(@NonNull String runnablePartThreadLimit) {
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
}
