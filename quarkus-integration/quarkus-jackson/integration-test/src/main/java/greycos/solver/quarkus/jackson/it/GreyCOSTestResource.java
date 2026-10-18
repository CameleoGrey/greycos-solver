package greycos.solver.quarkus.jackson.it;

import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisOptions;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverJob;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.quarkus.jackson.it.testdata.TestdataJacksonPlanningSolution;

@Path("/greycos/test")
public class GreyCOSTestResource {

  private final SolverManager<TestdataJacksonPlanningSolution> solverManager;

  @Inject
  public GreyCOSTestResource(SolverManager<TestdataJacksonPlanningSolution> solverManager) {
    this.solverManager = solverManager;
  }

  @POST
  @Path("/indictments")
  public ScoreAnalysis<SimpleScore> analyzeIndictments(TestdataJacksonPlanningSolution problem) {
    return SolutionManager.<TestdataJacksonPlanningSolution, SimpleScore>create(solverManager)
        .analyzeWithIndictments(problem, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
  }

  @POST
  @Path("/raw-analysis")
  public ScoreAnalysis<SimpleScore> analyzeRaw(TestdataJacksonPlanningSolution problem) {
    return SolutionManager.<TestdataJacksonPlanningSolution, SimpleScore>create(solverManager)
        .analyzeWithOptions(
            problem,
            new ScoreAnalysisOptions(
                ScoreAnalysisFetchPolicy.FETCH_ALL,
                IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL,
                true));
  }

  @POST
  @Path("/solver-factory")
  public TestdataJacksonPlanningSolution solveWithSolverFactory(
      TestdataJacksonPlanningSolution problem) {
    SolverJob<TestdataJacksonPlanningSolution> solverJob = solverManager.solve(1L, problem);
    try {
      return solverJob.getFinalBestSolution();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Solving was interrupted.", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Solving failed.", e);
    }
  }
}
