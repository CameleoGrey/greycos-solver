package greycos.solver.quarkus.jackson.it;

import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

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
