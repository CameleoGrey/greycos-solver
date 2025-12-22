package ai.greycos.solver.quarkus.it;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.api.solver.SolverConfigOverride;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.impl.solver.DefaultSolverJob;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.quarkus.it.domain.TestdataStringLengthShadowEntity;
import ai.greycos.solver.quarkus.it.domain.TestdataStringLengthShadowSolution;

@Path("/greycos/test")
public class GreycosTestResource {

  private final SolverManager<TestdataStringLengthShadowSolution, Long> solverManager;

  @Inject
  public GreycosTestResource(
      SolverManager<TestdataStringLengthShadowSolution, Long> solverManager) {
    this.solverManager = solverManager;
  }

  private static TestdataStringLengthShadowSolution generateProblem() {
    var planningProblem = new TestdataStringLengthShadowSolution();
    var firstEntity = new TestdataStringLengthShadowEntity();
    firstEntity.setValueList(List.of("ccc"));
    var secondEntity = new TestdataStringLengthShadowEntity();
    secondEntity.setValueList(List.of("ccc"));
    planningProblem.setEntityList(Arrays.asList(firstEntity, secondEntity));
    planningProblem.setValueList(Arrays.asList("a", "bb"));

    return planningProblem;
  }

  @POST
  @Path("/solver-factory")
  @Produces(MediaType.TEXT_PLAIN)
  public String solveWithSolverFactory() {
    var solverJob = solverManager.solve(1L, generateProblem());
    try {
      return solverJob.getFinalBestSolution().getScore().toString();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Solving was interrupted.", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Solving failed.", e);
    }
  }

  @GET
  @Path("/solver-factory/override")
  @Produces(MediaType.TEXT_PLAIN)
  public String solveWithOverriddenTime(@QueryParam("seconds") Integer seconds) {
    var solverJobBuilder =
        solverManager
            .solveBuilder()
            .withProblemId(1L)
            .withProblem(generateProblem())
            .withConfigOverride(
                new SolverConfigOverride<TestdataStringLengthShadowSolution>()
                    .withTerminationConfig(
                        new TerminationConfig().withSpentLimit(Duration.ofSeconds(seconds))));
    var solverJob =
        (DefaultSolverJob<TestdataStringLengthShadowSolution, Long>) solverJobBuilder.run();
    SolverScope<TestdataStringLengthShadowSolution> customScope =
        new SolverScope<>() {
          @Override
          public long calculateTimeMillisSpentUpToNow() {
            // Return five seconds to make the time gradient predictable
            return 5000L;
          }
        };
    // We ensure the best-score limit won't take priority
    customScope.setStartingInitializedScore(HardSoftScore.of(-1, -1));
    customScope.setInitializedBestScore(HardSoftScore.of(-1, -1));
    try {
      var score = solverJob.getFinalBestSolution().getScore().toString();
      var decimalFormatSymbols = DecimalFormatSymbols.getInstance();
      decimalFormatSymbols.setDecimalSeparator('.');
      var decimalFormat = new DecimalFormat("0.00", decimalFormatSymbols);
      var gradientTime = solverJob.getSolverTermination().calculateSolverTimeGradient(customScope);
      solverManager.terminateEarly(1L);
      return String.format("%s,%s", score, decimalFormat.format(gradientTime));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Solving was interrupted.", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Solving failed.", e);
    }
  }
}
