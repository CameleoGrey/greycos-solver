package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorXMLPropertyTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver-config-xml",
              "ai/greycos/solver/quarkus/customSolverConfig.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addAsResource("ai/greycos/solver/quarkus/customSolverConfig.xml"));

  @Inject SolverConfig solverConfig;
  @Inject SolverFactory<TestdataQuarkusSolution> solverFactory;

  @Test
  void solverConfigXml_property() {
    assertNotNull(solverConfig);
    assertEquals(CotwinAccessType.GIZMO, solverConfig.getCotwinAccessType());
    assertEquals(TestdataQuarkusSolution.class, solverConfig.getSolutionClass());
    assertEquals(
        Collections.singletonList(TestdataQuarkusEntity.class), solverConfig.getEntityClassList());
    assertEquals(
        TestdataQuarkusConstraintProvider.class,
        solverConfig.getScoreDirectorFactoryConfig().getConstraintProviderClass());
    // Properties defined in solverConfig.xml
    assertEquals(3L, solverConfig.getTerminationConfig().getSecondsSpentLimit().longValue());
    assertNotNull(solverFactory);
    assertNotNull(solverFactory.buildSolver());
  }
}
