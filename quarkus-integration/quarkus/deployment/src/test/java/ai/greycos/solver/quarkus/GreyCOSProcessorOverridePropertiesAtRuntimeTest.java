package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.SolverManagerConfig;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.test.QuarkusProdModeTest;
import io.restassured.RestAssured;

class GreyCOSProcessorOverridePropertiesAtRuntimeTest {

  private static final String QUARKUS_VERSION = getRequiredProperty("version.io.quarkus");

  private static String getRequiredProperty(String name) {
    final String v = System.getProperty(name);
    if (v == null || v.isEmpty()) {
      throw new IllegalStateException("The system property (%s) has not been set.".formatted(name));
    }
    return v;
  }

  @RegisterExtension
  static final QuarkusProdModeTest config =
      new QuarkusProdModeTest()
          .setForcedDependencies(
              List.of(new AppArtifact("io.quarkus", "quarkus-rest", QUARKUS_VERSION)))
          // We want to check if these are overridden at runtime
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .overrideConfigKey("quarkus.greycos.solver.move-thread-count", "4")
          .overrideConfigKey("quarkus.greycos.solver-manager.parallel-solver-count", "1")
          .overrideConfigKey(
              "quarkus.greycos.solver.termination.diminished-returns.enabled", "false")
          .overrideConfigKey(
              "quarkus.greycos.solver.termination.diminished-returns.sliding-window-duration", "3h")
          .overrideConfigKey(
              "quarkus.greycos.solver.termination.diminished-returns.minimum-improvement-ratio",
              "0.25")
          .overrideConfigKey("quarkus.greycos.solver.random-seed", "123")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class,
                          GreyCOSTestResource.class))
          .setRuntimeProperties(getRuntimeProperties())
          .setRun(true);

  private static Map<String, String> getRuntimeProperties() {
    Map<String, String> out = new HashMap<>();
    out.put("quarkus.greycos.solver.termination.best-score-limit", "7");
    out.put("quarkus.greycos.solver.move-thread-count", "3");
    out.put("quarkus.greycos.solver-manager.parallel-solver-count", "10");
    out.put("quarkus.greycos.solver.termination.diminished-returns.enabled", "true");
    out.put("quarkus.greycos.solver.termination.diminished-returns.sliding-window-duration", "6h");
    out.put(
        "quarkus.greycos.solver.termination.diminished-returns.minimum-improvement-ratio", "0.5");
    return out;
  }

  // Can't use injection, so we need a resource to fetch the properties
  @Path("/greycos/test")
  public static class GreyCOSTestResource {
    @Inject SolverConfig solverConfig;

    @Inject SolverManagerConfig solverManagerConfig;

    @GET
    @Path("/solver-config")
    @Produces(MediaType.TEXT_PLAIN)
    public String getSolverConfig() {
      var diminishedReturnsConfig =
          solverConfig.getTerminationConfig().getDiminishedReturnsConfig();
      return """
                    termination.diminished-returns.sliding-window-duration=%sh
                    termination.diminished-returns.minimum-improvement-ratio=%s
                    termination.bestScoreLimit=%s
                    moveThreadCount=%s
                    randomSeed=%d
                    """
          .formatted(
              diminishedReturnsConfig.getSlidingWindowDuration().toHours(),
              diminishedReturnsConfig.getMinimumImprovementRatio(),
              solverConfig.getTerminationConfig().getBestScoreLimit(),
              solverConfig.getMoveThreadCount(),
              solverConfig.getRandomSeed());
    }

    @GET
    @Path("/solver-manager-config")
    @Produces(MediaType.TEXT_PLAIN)
    public String getSolverManagerConfig() {
      StringBuilder sb = new StringBuilder();
      sb.append("parallelSolverCount=")
          .append(solverManagerConfig.getParallelSolverCount())
          .append("\n");
      return sb.toString();
    }
  }

  @Test
  void solverConfigPropertiesShouldBeOverwritten() throws IOException {
    Properties solverConfigProperties = new Properties();
    solverConfigProperties.load(
        RestAssured.given()
            .contentType(MediaType.TEXT_PLAIN)
            .accept(MediaType.TEXT_PLAIN)
            .when()
            .get("/greycos/test/solver-config")
            .asInputStream());
    assertEquals(
        "6h", solverConfigProperties.get("termination.diminished-returns.sliding-window-duration"));
    assertEquals(
        "0.5",
        solverConfigProperties.get("termination.diminished-returns.minimum-improvement-ratio"));
    assertEquals("7", solverConfigProperties.get("termination.bestScoreLimit"));
    assertEquals("3", solverConfigProperties.get("moveThreadCount"));
    assertEquals("123", solverConfigProperties.get("randomSeed"));
  }

  @Test
  void solverManagerConfigPropertiesShouldBeOverwritten() throws IOException {
    Properties solverManagerProperties = new Properties();
    solverManagerProperties.load(
        RestAssured.given()
            .contentType(MediaType.TEXT_PLAIN)
            .accept(MediaType.TEXT_PLAIN)
            .when()
            .get("/greycos/test/solver-manager-config")
            .asInputStream());
    assertEquals("10", solverManagerProperties.get("parallelSolverCount"));
  }
}
