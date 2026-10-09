package greycos.solver.core.impl.constructionheuristic.decider.forager;

import greycos.solver.core.impl.constructionheuristic.placer.RandomAssignmentMove;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** Accepts the single random proposal without making its distribution depend on score. */
public final class RandomAssignmentConstructionHeuristicForager<Solution_>
    extends AbstractConstructionHeuristicForager<Solution_> {

  private ConstructionHeuristicMoveScope<Solution_> picked;

  @Override
  public void stepStarted(ConstructionHeuristicStepScope<Solution_> stepScope) {
    picked = null;
  }

  @Override
  public void addMove(ConstructionHeuristicMoveScope<Solution_> moveScope) {
    var phaseScope = moveScope.getStepScope().getPhaseScope();
    phaseScope.addMoveEvaluationCount(moveScope.getMove(), 1L);
    var score = moveScope.getScore();
    if (score.isStructurallyFlawed() || !score.isFullyAssigned()) {
      // CH has already undone temporary evaluation before calling the forager.
      ((RandomAssignmentMove<Solution_>) moveScope.getMove())
          .verifyRestored(
              moveScope.getScoreDirector(), phaseScope.getLastCompletedStepScope().getScore());
      throw new IllegalStateException(
          "RANDOM_ASSIGNMENT construction sampled an incomplete or structurally invalid assignment with score (%s), fullyAssigned (%s) and structurallyFlawed (%s) at phase (%d), step (%d). The original assignments were restored. No retries are performed; use another construction heuristic for models requiring structural repair."
              .formatted(
                  score,
                  score.isFullyAssigned(),
                  score.isStructurallyFlawed(),
                  phaseScope.getPhaseIndex(),
                  moveScope.getStepScope().getStepIndex()));
    }
    picked = moveScope;
  }

  @Override
  public boolean isQuitEarly() {
    return picked != null;
  }

  @Override
  public ConstructionHeuristicMoveScope<Solution_> pickMove(
      ConstructionHeuristicStepScope<Solution_> stepScope) {
    stepScope.setSelectedMoveCount(picked == null ? 0L : 1L);
    return picked;
  }

  @Override
  public void stepEnded(ConstructionHeuristicStepScope<Solution_> stepScope) {
    picked = null;
  }

  @Override
  public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    picked = null;
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    picked = null;
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    picked = null;
  }
}
