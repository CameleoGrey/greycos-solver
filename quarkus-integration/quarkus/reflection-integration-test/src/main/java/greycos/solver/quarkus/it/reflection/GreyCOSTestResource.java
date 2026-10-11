package greycos.solver.quarkus.it.reflection;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import greycos.solver.core.api.solver.SolverJob;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.cotwin.common.CotwinAccessType;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.quarkus.it.reflection.cotwin.TestdataMultiListFixture;
import greycos.solver.quarkus.it.reflection.cotwin.TestdataReflectionEntity;
import greycos.solver.quarkus.it.reflection.cotwin.TestdataReflectionSolution;

@Path("/greycos/test")
public class GreyCOSTestResource {

  private final SolverManager<TestdataReflectionSolution> solverManager;

  @Inject SolverConfig solverConfig;

  @Inject
  public GreyCOSTestResource(SolverManager<TestdataReflectionSolution> solverManager) {
    this.solverManager = solverManager;
  }

  @POST
  @Path("/solver-factory")
  @Produces(MediaType.TEXT_PLAIN)
  public String solveWithSolverFactory() {
    TestdataReflectionSolution planningProblem = new TestdataReflectionSolution();
    planningProblem.setEntityList(
        Arrays.asList(new TestdataReflectionEntity(), new TestdataReflectionEntity()));
    planningProblem.setFieldValueList(Arrays.asList("a", "bb", "ccc"));
    planningProblem.setMethodValueList(Arrays.asList("a", "bb", "ccc", "ddd"));
    SolverJob<TestdataReflectionSolution> solverJob = solverManager.solve(1L, planningProblem);
    try {
      return solverJob.getFinalBestSolution().getScore().toString();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Solving was interrupted.", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Solving failed.", e);
    }
  }

  @POST
  @Path("/multiple-lists")
  @Produces(MediaType.TEXT_PLAIN)
  public String solveMultipleLists() {
    var problem = new TestdataReflectionSolution();
    problem.setEntityList(
        Arrays.asList(new TestdataReflectionEntity(), new TestdataReflectionEntity()));
    problem.setFieldValueList(Arrays.asList("a", "bb", "ccc"));
    problem.setMethodValueList(Arrays.asList("a", "bb", "ccc", "ddd"));
    TestdataMultiListFixture.initialize(problem);
    var reflectionConfig = solverConfig.copyConfig();
    reflectionConfig.setGizmoMemberAccessorMap(Map.of());
    reflectionConfig.setGizmoSolutionClonerMap(Map.of());
    var factory =
        new DefaultSolverFactory<TestdataReflectionSolution>(
            reflectionConfig, CotwinAccessType.FORCE_REFLECTION);
    if (!factory
        .getSolutionDescriptor()
        .getSolutionCloner()
        .getClass()
        .getName()
        .equals("greycos.solver.core.impl.cotwin.solution.cloner.FieldAccessingSolutionCloner")) {
      throw new IllegalStateException("The reflection smoke test must use the reflection cloner.");
    }
    var solution = factory.buildSolver().solve(problem);
    TestdataMultiListFixture.verify(problem, solution);
    return solution.getScore() + ";lists=3/4;pins=1/2;shadows=valid;clone=independent";
  }
}
