package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

class GreyCOSProcessorXMLNoneTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class));

  @Inject SolverConfig solverConfig;
  @Inject SolverFactory<TestdataQuarkusSolution> solverFactory;

  @Test
  void solverConfigXml_default() {
    assertNotNull(solverConfig);
    assertEquals(TestdataQuarkusSolution.class, solverConfig.getSolutionClass());
    assertEquals(CotwinAccessType.GIZMO, solverConfig.getCotwinAccessType());
    assertEquals(
        Collections.singletonList(TestdataQuarkusEntity.class), solverConfig.getEntityClassList());
    assertEquals(
        TestdataQuarkusConstraintProvider.class,
        solverConfig.getScoreDirectorFactoryConfig().getConstraintProviderClass());
    // No termination defined (solverConfig.xml isn't included)
    assertNull(solverConfig.getTerminationConfig().getSecondsSpentLimit());
    assertNotNull(solverFactory);
    assertNotNull(solverFactory.buildSolver());
  }
}
