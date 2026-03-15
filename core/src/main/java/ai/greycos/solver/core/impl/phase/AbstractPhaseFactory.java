package ai.greycos.solver.core.impl.phase;

import java.util.Collections;
import java.util.Objects;

import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import ai.greycos.solver.core.config.phase.PhaseConfig;
import ai.greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import ai.greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.islandmodel.IslandModelPhaseScope;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.termination.SolverTermination;
import ai.greycos.solver.core.impl.solver.termination.TerminationFactory;
import ai.greycos.solver.core.impl.solver.termination.UniversalTermination;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPhaseFactory<
        Solution_, PhaseConfig_ extends PhaseConfig<PhaseConfig_>>
    implements PhaseFactory<Solution_> {

  protected final Logger logger = LoggerFactory.getLogger(getClass());
  protected final PhaseConfig_ phaseConfig;

  public AbstractPhaseFactory(PhaseConfig_ phaseConfig) {
    this.phaseConfig = phaseConfig;
  }

  protected PhaseTermination<Solution_> buildPhaseTermination(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SolverTermination<Solution_> solverTermination) {
    var terminationConfig_ =
        Objects.requireNonNullElseGet(phaseConfig.getTerminationConfig(), TerminationConfig::new);
    var phaseTermination = PhaseTermination.bridge(solverTermination);
    var resultingTermination =
        TerminationFactory.<Solution_>create(terminationConfig_)
            .buildTermination(configPolicy, phaseTermination);
    var inapplicableTerminationList =
        resultingTermination instanceof UniversalTermination<Solution_> universalTermination
            ? universalTermination.getPhaseTerminationsInapplicableTo(getPhaseScopeClass())
            : Collections.emptyList();
    var phaseName =
        this.getClass().getSimpleName().replace("PhaseFactory", "").replace("Default", "");
    if (solverTermination != resultingTermination) {
      // Only fail if the user put the inapplicable termination on the phase, not on the solver.
      // On the solver level, inapplicable phase terminations are skipped.
      // Otherwise you would only be able to configure a global phase-level termination on the
      // solver
      // if it was applicable to all phases.
      if (!inapplicableTerminationList.isEmpty()) {
        throw new IllegalStateException(
            """
                                The phase (%s) configured with terminations (%s) includes some terminations which are not applicable to it (%s).
                                Maybe remove these terminations from the phase's configuration."""
                .formatted(phaseName, phaseTermination, inapplicableTerminationList));
      }
    } else if (!inapplicableTerminationList.isEmpty()) {
      logger.trace(
          """
                    The solver-level termination ({}) includes phase-level terminations ({}) \
                    which are not applicable to the phase ({}).
                    These phase-level terminations will not take effect in this phase.""",
          solverTermination,
          inapplicableTerminationList,
          phaseName);
    }
    return resultingTermination;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Class<? extends AbstractPhaseScope> getPhaseScopeClass() {
    if (phaseConfig instanceof ConstructionHeuristicPhaseConfig) {
      return ConstructionHeuristicPhaseScope.class;
    } else if (phaseConfig instanceof CustomPhaseConfig) {
      return CustomPhaseScope.class;
    } else if (phaseConfig instanceof IslandModelPhaseConfig) {
      return IslandModelPhaseScope.class;
    } else if (phaseConfig instanceof LocalSearchPhaseConfig) {
      return LocalSearchPhaseScope.class;
    } else if (phaseConfig instanceof ExhaustiveSearchPhaseConfig) {
      return ExhaustiveSearchPhaseScope.class;
    } else if (phaseConfig instanceof PartitionedSearchPhaseConfig) {
      return ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope.class;
    } else {
      throw new IllegalStateException(
          "Unsupported phaseConfig class: %s".formatted(phaseConfig.getClass()));
    }
  }

  protected @Nullable Integer resolveMoveThreadCount(
      @Nullable String moveThreadCount, boolean enforceMaximum) {
    if (moveThreadCount == null || moveThreadCount.equals(SolverConfig.MOVE_THREAD_COUNT_NONE)) {
      return null;
    }
    var availableProcessorCount = getAvailableProcessors();
    int resolvedMoveThreadCount;
    if (moveThreadCount.equals(SolverConfig.MOVE_THREAD_COUNT_AUTO)) {
      // Leave one for the Operating System and 1 for the solver thread, take the rest.
      resolvedMoveThreadCount = (availableProcessorCount - 2);
      if (enforceMaximum && resolvedMoveThreadCount > 4) {
        // A moveThreadCount beyond 4 is currently typically slower.
        resolvedMoveThreadCount = 4;
      }
      if (resolvedMoveThreadCount <= 1) {
        // Fall back to single threaded solving with no move threads.
        // To deliberately enforce 1 moveThread, set the moveThreadCount explicitly to 1.
        return null;
      }
    } else {
      resolvedMoveThreadCount =
          ConfigUtils.resolvePoolSize(
              "moveThreadCount",
              moveThreadCount,
              SolverConfig.MOVE_THREAD_COUNT_NONE,
              SolverConfig.MOVE_THREAD_COUNT_AUTO);
    }
    if (resolvedMoveThreadCount < 1) {
      throw new IllegalArgumentException(
          "The moveThreadCount (%s) resulted in a resolvedMoveThreadCount (%d) that is lower than 1."
              .formatted(moveThreadCount, resolvedMoveThreadCount));
    }
    if (resolvedMoveThreadCount > availableProcessorCount) {
      logger.warn(
          "The resolvedMoveThreadCount ({}) is higher than the availableProcessorCount ({}), which is counter-efficient.",
          resolvedMoveThreadCount,
          availableProcessorCount);
    }
    return resolvedMoveThreadCount;
  }

  protected @Nullable Integer resolveMoveThreadCount(
      @Nullable String phaseMoveThreadCount,
      @Nullable Integer solverMoveThreadCount,
      boolean enforceMaximum) {
    return phaseMoveThreadCount == null
        ? solverMoveThreadCount
        : resolveMoveThreadCount(phaseMoveThreadCount, enforceMaximum);
  }

  protected int getAvailableProcessors() {
    return Runtime.getRuntime().availableProcessors();
  }
}
