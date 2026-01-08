package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.greycos.solver.quarkus.rest.TestdataQuarkusShadowSolutionConfigResource;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;
import ai.greycos.solver.quarkus.testdomain.shadowvariable.TestdataQuarkusShadowVariableConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.shadowvariable.TestdataQuarkusShadowVariableEntity;
import ai.greycos.solver.quarkus.testdomain.shadowvariable.TestdataQuarkusShadowVariableListener;
import ai.greycos.solver.quarkus.testdomain.shadowvariable.TestdataQuarkusShadowVariableSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

class GreyCOSProcessorMultipleSolversXMLPropertyTest {

  @RegisterExtension
  static final QuarkusUnitTest config2 =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.\"solver1\".environment-mode", "FULL_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver1\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverQuarkusConfig.xml")
          .overrideConfigKey("quarkus.greycos.solver.\"solver2\".environment-mode", "PHASE_ASSERT")
          .overrideConfigKey(
              "quarkus.greycos.solver.\"solver2\".solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverQuarkusShadowVariableConfig.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addClasses(
                          TestdataQuarkusShadowVariableEntity.class,
                          TestdataQuarkusShadowVariableSolution.class,
                          TestdataQuarkusShadowVariableConstraintProvider.class,
                          TestdataQuarkusShadowVariableListener.class,
                          TestdataQuarkusShadowSolutionConfigResource.class)
                      .addAsResource("ai/greycos/solver/quarkus/customSolverQuarkusConfig.xml")
                      .addAsResource(
                          "ai/greycos/solver/quarkus/customSolverQuarkusShadowVariableConfig.xml"));

  @Test
  void solverProperties() {
    String resp = RestAssured.get("/solver-config/seconds-spent-limit").asString();
    assertEquals("secondsSpentLimit=0.50;secondsSpentLimit=0.12", resp);
  }
}
