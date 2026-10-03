package greycos.solver.core.impl.solver.recaller;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.event.SolverEventSupport;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remembers the {@link PlanningSolution best solution} that a {@link Solver} encounters.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class BestSolutionRecaller<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BestSolutionRecaller.class);
  protected boolean assertInitialScoreFromScratch = false;
  protected boolean assertShadowVariablesAreNotStale = false;
  protected boolean assertBestScoreIsUnmodified = false;

  protected SolverEventSupport<Solution_> solverEventSupport;

  public void setSolverEventSupport(SolverEventSupport<Solution_> solverEventSupport) {
    this.solverEventSupport = solverEventSupport;
  }

  public void enableAssertions(EnvironmentMode environmentMode) {
    assertInitialScoreFromScratch = environmentMode.isFullyAsserted();
    assertShadowVariablesAreNotStale = environmentMode.isFullyAsserted();
    assertBestScoreIsUnmodified = environmentMode.isFullyAsserted();
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  @SuppressWarnings("unchecked")
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    // Starting bestSolution is already set by Solver.solve(Solution)
    var scoreDirector = solverScope.getScoreDirector();
    @SuppressWarnings("rawtypes")
    InnerScore innerScore = scoreDirector.calculateScore();
    if (innerScore.isStructurallyFlawed()) {
      LOGGER.warn(
          "The initial solution passed to the solver is inconsistent. Unassigning involved"
              + " entities.");
      scoreDirector.unassignInconsistentEntities();
      innerScore = scoreDirector.calculateScore();
      if (innerScore.isStructurallyFlawed()) {
        // If there were a fixed dependency loop, the shadow variable session would fail fast before
        // here
        throw new IllegalStateException(
            "Impossible state: The initial solution passed to the solver is inconsistent even after"
                + " unassigning involved entities.");
      }
    }
    var score = innerScore.raw();
    solverScope.setBestScore(innerScore);
    solverScope.setBestSolutionTimeMillis(solverScope.getClock().millis());
    // Initialization may have repaired shadows or structural inconsistencies without changing the
    // score. Always retain that refreshed state, and never modify a previously published best.
    solverScope.setBestSolution(scoreDirector.cloneWorkingSolution());
    if (innerScore.isFullyAssigned()) {
      solverScope.setStartingInitializedScore(innerScore.raw());
    } else {
      solverScope.setStartingInitializedScore(null);
    }
    if (assertInitialScoreFromScratch) {
      scoreDirector.assertWorkingScoreFromScratch(innerScore, "Initial score calculated");
    }
    if (assertShadowVariablesAreNotStale) {
      scoreDirector.assertShadowVariablesAreNotStale(innerScore, "Initial score calculated");
    }
  }

  public void processWorkingSolutionDuringConstructionHeuristicsStep(
      AbstractStepScope<Solution_> stepScope) {
    AbstractPhaseScope<Solution_> phaseScope = stepScope.getPhaseScope();
    SolverScope<Solution_> solverScope = phaseScope.getSolverScope();
    stepScope.setBestScoreImproved(true);
    phaseScope.setBestSolutionStepIndex(stepScope.getStepIndex());
    Solution_ newBestSolution = stepScope.getWorkingSolution();
    // Construction heuristics don't fire intermediate best solution changed events.
    // But the best solution and score are updated, so that unimproved* terminations work correctly.
    updateBestSolutionWithoutFiring(solverScope, stepScope.getScore(), newBestSolution);
  }

  public <Score_ extends Score<Score_>> void processWorkingSolutionDuringStep(
      AbstractStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var score = stepScope.<Score_>getScore();
    var solverScope = phaseScope.getSolverScope();
    var bestScoreImproved = score.compareTo(solverScope.getBestScore()) > 0;
    stepScope.setBestScoreImproved(bestScoreImproved);
    if (bestScoreImproved) {
      phaseScope.setBestSolutionStepIndex(stepScope.getStepIndex());
      var newBestSolution = stepScope.cloneWorkingSolution();
      var innerScore =
          buildInnerScore(
              solverScope.getSolutionDescriptor().<Score_>getScore(newBestSolution),
              stepScope.getScoreDirector().getWorkingInitScore(),
              true);
      updateBestSolutionAndFire(solverScope, phaseScope, innerScore, newBestSolution);
    } else if (assertBestScoreIsUnmodified) {
      solverScope.assertScoreFromScratch(solverScope.getBestSolution());
    }
  }

  public <Score_ extends Score<Score_>> void processWorkingSolutionDuringMove(
      InnerScore<Score_> moveScore, AbstractStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();
    var bestScoreImproved = moveScore.compareTo(solverScope.getBestScore()) > 0;
    // The method processWorkingSolutionDuringMove() is called 0..* times
    // stepScope.getBestScoreImproved() is initialized on false before the first call here
    if (bestScoreImproved) {
      stepScope.setBestScoreImproved(bestScoreImproved);
    }
    if (bestScoreImproved) {
      phaseScope.setBestSolutionStepIndex(stepScope.getStepIndex());
      var newBestSolution = solverScope.getScoreDirector().cloneWorkingSolution();
      var innerScore =
          buildInnerScore(
              moveScore.raw(),
              solverScope.getScoreDirector().getWorkingInitScore(),
              solverScope
                  .getScoreDirector()
                  .getSolutionDescriptor()
                  .hasBothBasicAndListVariables());
      updateBestSolutionAndFire(solverScope, phaseScope, innerScore, newBestSolution);
    } else if (assertBestScoreIsUnmodified) {
      solverScope.assertScoreFromScratch(solverScope.getBestSolution());
    }
  }

  public void updateBestSolutionAndFire(
      SolverScope<Solution_> solverScope, AbstractPhaseScope<Solution_> phaseScope) {
    updateBestSolutionWithoutFiring(solverScope);
    solverEventSupport.fireBestSolutionChanged(
        solverScope, phaseScope.getPhaseId(), solverScope.getBestSolution());
  }

  public void updateBestSolutionAndFireIfInitialized(
      SolverScope<Solution_> solverScope, EventProducerId eventProducerId) {
    updateBestSolutionWithoutFiring(solverScope);
    if (solverScope.isBestSolutionInitialized()) {
      solverEventSupport.fireBestSolutionChanged(
          solverScope, eventProducerId, solverScope.getBestSolution());
    }
  }

  private void updateBestSolutionAndFire(
      SolverScope<Solution_> solverScope,
      AbstractPhaseScope<Solution_> phaseScope,
      InnerScore<?> bestScore,
      Solution_ bestSolution) {
    updateBestSolutionWithoutFiring(solverScope, bestScore, bestSolution);
    solverEventSupport.fireBestSolutionChanged(solverScope, phaseScope.getPhaseId(), bestSolution);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void updateBestSolutionWithoutFiring(SolverScope<Solution_> solverScope) {
    // We clone the existing working solution to set it as the best current solution
    var newBestSolution = solverScope.getScoreDirector().cloneWorkingSolution();
    var newBestScore = solverScope.getSolutionDescriptor().<Score>getScore(newBestSolution);
    var innerScore =
        buildInnerScore(newBestScore, solverScope.getScoreDirector().getWorkingInitScore(), true);
    updateBestSolutionWithoutFiring(solverScope, innerScore, newBestSolution);
  }

  private void updateBestSolutionWithoutFiring(
      SolverScope<Solution_> solverScope, InnerScore<?> bestScore, Solution_ bestSolution) {
    if (bestScore.isStructurallyFlawed()) {
      throw new IllegalStateException(
          "Cannot publish a structurally flawed solution as the best solution (%s)."
              .formatted(bestScore));
    }
    if (bestScore.isFullyAssigned() && !solverScope.isBestSolutionInitialized()) {
      solverScope.setStartingInitializedScore(bestScore.raw());
    }

    solverScope.setBestSolution(bestSolution);
    solverScope.setBestScore(bestScore);
    solverScope.setBestSolutionTimeMillis(solverScope.getClock().millis());
  }

  private static <Score_ extends Score<Score_>> InnerScore<Score_> buildInnerScore(
      Score_ moveScore, int uninitializedScore, boolean acceptUnassigned) {
    if (acceptUnassigned) {
      var adjustedUninitializedScore =
          uninitializedScore < 0 ? -uninitializedScore : uninitializedScore;
      return InnerScore.withUnassignedCount(moveScore, adjustedUninitializedScore);
    } else {
      return new InnerScore<>(moveScore, uninitializedScore);
    }
  }
}
