package greycos.solver.core.impl.iteratedlocalsearch;

import java.util.Objects;

import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhaseFactory;
import greycos.solver.core.impl.phase.AbstractPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.IteratedLocalSearchTerminationSupport;
import greycos.solver.core.impl.solver.termination.SolverTermination;

/** Resolves one resource owner and validates all ILS settings before acquiring worker resources. */
public final class DefaultIteratedLocalSearchPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, IteratedLocalSearchPhaseConfig> {
  public DefaultIteratedLocalSearchPhaseFactory(IteratedLocalSearchPhaseConfig config) {
    super(config);
  }

  @Override
  public IteratedLocalSearchPhase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {
    validateSettings();
    var environmentMode = resolveEnvironmentMode(solverConfigPolicy);
    var moveThreadCount =
        resolveMoveThreadCount(
            phaseConfig.getMoveThreadCount(), solverConfigPolicy.getMoveThreadCount(), true);
    var localSearch = phaseConfig.getLocalSearchConfig();
    if (localSearch.getEnvironmentMode() != null
        && localSearch.getEnvironmentMode() != environmentMode) {
      throw new IllegalArgumentException(
          "The iteratedLocalSearch.localSearch.environmentMode (%s) conflicts with the effective iteratedLocalSearch.environmentMode (%s)."
              .formatted(localSearch.getEnvironmentMode(), environmentMode));
    }
    if (localSearch.getMoveThreadCount() != null
        && !Objects.equals(
            resolveMoveThreadCount(localSearch.getMoveThreadCount(), true), moveThreadCount)) {
      throw new IllegalArgumentException(
          "The iteratedLocalSearch.localSearch.moveThreadCount (%s) conflicts with the effective iteratedLocalSearch.moveThreadCount (%s)."
              .formatted(
                  localSearch.getMoveThreadCount(),
                  moveThreadCount == null ? "NONE" : moveThreadCount));
    }
    if (moveThreadCount != null && solverConfigPolicy.isConstraintStreamProfilingEnabled()) {
      throw new UnsupportedOperationException(
          "Iterated Local Search move workers are not supported together with constraintStreamProfilingEnabled (true).");
    }
    var policy =
        solverConfigPolicy
            .cloneBuilder()
            .withEnvironmentMode(environmentMode)
            .withMoveThreadCount(moveThreadCount)
            .build();
    var termination = buildPhaseTermination(policy, solverTermination);
    if (phaseConfig.getIterationCountLimit() == null
        && !IteratedLocalSearchTerminationSupport.hasApplicableLimit(termination)) {
      throw new IllegalArgumentException(
          "The iteratedLocalSearch requires an applicable solver/phase termination or a positive iterationCountLimit. Episode limits alone do not bound the outer search.");
    }
    var ownedConfig = phaseConfig.copyConfig();
    var inner = ownedConfig.getLocalSearchConfig();
    inner.setEnvironmentMode(environmentMode);
    inner.setMoveThreadCount(
        moveThreadCount == null ? SolverConfig.MOVE_THREAD_COUNT_NONE : moveThreadCount.toString());
    var episodeRunner =
        new DefaultLocalSearchPhaseFactory<Solution_>(inner)
            .buildEpisodeRunner(
                policy, phaseIndex, termination, ownedConfig.getEpisodeCandidateAttemptLimit());
    var perturbationConfig =
        new LocalSearchPhaseConfig()
            .withMoveSelectorConfig(ownedConfig.getPerturbationMoveSelectorConfig());
    var perturbation =
        new DefaultLocalSearchPhaseFactory<Solution_>(perturbationConfig)
            .buildPerturbationMoveSelector(policy);
    return new DefaultIteratedLocalSearchPhase.Builder<>(
            phaseIndex,
            environmentMode,
            policy.getLogIndentation(),
            termination,
            ownedConfig,
            episodeRunner,
            perturbation,
            bestSolutionRecaller,
            moveThreadCount)
        .enableAssertions()
        .build();
  }

  private void validateSettings() {
    if (phaseConfig.getLocalSearchConfig() == null) {
      throw new IllegalArgumentException("The iteratedLocalSearch.localSearch (null) is required.");
    }
    var strengths = phaseConfig.getPerturbationStrengths();
    if (strengths == null || strengths.isEmpty()) {
      throw new IllegalArgumentException(
          "The iteratedLocalSearch.perturbationStrengths (" + strengths + ") must be nonempty.");
    }
    int previous = 0;
    for (var strength : strengths) {
      if (strength == null || strength <= previous) {
        throw new IllegalArgumentException(
            "The iteratedLocalSearch.perturbationStrengths ("
                + strengths
                + ") must contain strictly increasing positive integers.");
      }
      previous = strength;
    }
    requirePositive("perturbationAttemptLimit", phaseConfig.getPerturbationAttemptLimit());
    requirePositive("episodeCandidateAttemptLimit", phaseConfig.getEpisodeCandidateAttemptLimit());
    if (phaseConfig.getPerturbationAttemptLimit() < previous) {
      throw new IllegalArgumentException(
          "The iteratedLocalSearch.perturbationAttemptLimit ("
              + phaseConfig.getPerturbationAttemptLimit()
              + ") must be at least the largest perturbation strength ("
              + previous
              + ").");
    }
    if (phaseConfig.getIterationCountLimit() != null) {
      requirePositive("iterationCountLimit", phaseConfig.getIterationCountLimit());
    }
  }

  private static void requirePositive(String property, Long value) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(
          "The iteratedLocalSearch." + property + " (" + value + ") must be positive.");
    }
  }
}
