package greycos.solver.greybay.impl.evaluation;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.SolutionAssessment;
import greycos.solver.core.api.solver.Solver;

/** Internal seam for deterministic deadline and resource-ownership tests. */
public interface EvaluationSession<Solution_, Score_ extends Score<Score_>> {
  Solver<Solution_> solver();

  SolutionAssessment<Score_> assess(Solution_ solution);

  void awaitWorkers(Runnable stopping);

  void disposeBeforeSolve();
}
