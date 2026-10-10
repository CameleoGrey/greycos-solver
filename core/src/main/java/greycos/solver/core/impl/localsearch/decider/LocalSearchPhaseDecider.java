package greycos.solver.core.impl.localsearch.decider;

import java.util.function.BooleanSupplier;

import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

/** The decision and lifecycle contract shared by local-search algorithms. */
public interface LocalSearchPhaseDecider<Solution_> {

  /** Keeps evaluation workers alive while algorithm histories are reset between episodes. */
  default void setExternalEvaluationResources(boolean external) {}

  /** The enclosing stop condition; an ordinary episode limit must not retire shared workers. */
  default void setEvaluationResourceTermination(BooleanSupplier terminated) {}

  default void startEvaluationResources(LocalSearchPhaseScope<Solution_> phaseScope) {}

  default void endEvaluationResources(LocalSearchPhaseScope<Solution_> phaseScope) {}

  default boolean cancelAndQuiesceEvaluation() {
    return true;
  }

  /** False after an unsuccessful worker join: referenced provider state must remain alive. */
  default boolean isEvaluationStateSafeToDispose() {
    return true;
  }

  /** Replays a coordinator transition after candidate evaluation has become quiescent. */
  default void replayWorkingState(Move<Solution_> move, InnerScore<?> score) {}

  default void beginEpisode(long episodeId) {}

  /** Releases a decision without advancing selector, acceptor, or forager histories. */
  default void stepAborted(LocalSearchStepScope<Solution_> stepScope) {}

  void enableAssertions(EnvironmentMode environmentMode);

  void solvingStarted(SolverScope<Solution_> solverScope);

  void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope);

  void stepStarted(LocalSearchStepScope<Solution_> stepScope);

  void decideNextStep(LocalSearchStepScope<Solution_> stepScope);

  void stepEnded(LocalSearchStepScope<Solution_> stepScope);

  void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope);

  void solvingEnded(SolverScope<Solution_> solverScope);

  void solvingError(SolverScope<Solution_> solverScope, Throwable failure);

  default long getUncreditedCalculationCount() {
    return 0L;
  }

  default long getWorkerStartupCount() {
    return 0L;
  }

  default long getConsumedWorkerCalculationCount() {
    return 0L;
  }

  default long getAdditionalWorkerCalculationCount() {
    return 0L;
  }
}
