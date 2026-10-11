package greycos.solver.greybay.impl.study;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.BusinessVerifier;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.ProblemDescriptor;
import greycos.solver.greybay.impl.evaluation.EvaluationOutcome;

/** Internal deterministic evaluation seam; it is not a public evaluator extension point. */
@FunctionalInterface
interface EvaluationRunner<Solution_, Score_ extends Score<Score_>> {
  EvaluationOutcome<Score_> evaluate(
      SolverConfig config,
      ProblemAdapter<Solution_> adapter,
      ProblemDescriptor descriptor,
      long seed,
      Duration horizon,
      BusinessVerifier<Solution_> verifier,
      BooleanSupplier stopRequested,
      Runnable stopping);
}
