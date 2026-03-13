package ai.greycos.solver.quarkus.jackson.it;

import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import ai.greycos.solver.core.api.solver.SolverJob;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.quarkus.jackson.it.cotwin.ITestdataPlanningSolution;

@Path("/greycos/test")
public class GreyCOSTestResource {

  private final SolverManager<ITestdataPlanningSolution> solverManager;

  @Inject
  public GreyCOSTestResource(SolverManager<ITestdataPlanningSolution> solverManager) {
    this.solverManager = solverManager;
  }

  @POST
  @Path("/solver-factory")
  public ITestdataPlanningSolution solveWithSolverFactory(ITestdataPlanningSolution problem) {
    SolverJob<ITestdataPlanningSolution> solverJob = solverManager.solve(1L, problem);
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
