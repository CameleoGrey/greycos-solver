package greycos.solver.quarkus.benchmark.it;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import greycos.solver.benchmark.api.PlannerBenchmark;
import greycos.solver.benchmark.api.PlannerBenchmarkException;
import greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import greycos.solver.benchmark.impl.DefaultPlannerBenchmark;
import greycos.solver.quarkus.benchmark.it.cotwin.TestdataListValueShadowEntity;
import greycos.solver.quarkus.benchmark.it.cotwin.TestdataStringLengthShadowEntity;
import greycos.solver.quarkus.benchmark.it.cotwin.TestdataStringLengthShadowSolution;

@Path("/greycos/test")
public class GreyCOSBenchmarkTestResource {

  private final PlannerBenchmarkFactory benchmarkFactory;

  @Inject
  public GreyCOSBenchmarkTestResource(PlannerBenchmarkFactory benchmarkFactory) {
    this.benchmarkFactory = benchmarkFactory;
  }

  @POST
  @Path("/benchmark")
  @Produces(MediaType.TEXT_PLAIN)
  public String benchmark() {
    TestdataStringLengthShadowSolution planningProblem = new TestdataStringLengthShadowSolution();
    planningProblem.setEntityList(
        List.of(
            new TestdataStringLengthShadowEntity(1L), new TestdataStringLengthShadowEntity(2L)));
    planningProblem.setValueList(
        List.of(
            new TestdataListValueShadowEntity("a"),
            new TestdataListValueShadowEntity("bb"),
            new TestdataListValueShadowEntity("ccc")));
    PlannerBenchmark benchmark = benchmarkFactory.buildPlannerBenchmark(planningProblem);
    try {
      return benchmark.benchmark().toPath().toAbsolutePath().toString();
    } catch (PlannerBenchmarkException e) {
      // ignore the exception
      return ((DefaultPlannerBenchmark) benchmark).getBenchmarkDirectory().getAbsolutePath();
    }
  }
}
