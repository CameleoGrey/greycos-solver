package ai.greycos.solver.quarkus.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreycosProcessorConstraintProviderTest {

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
    assertEquals(
        TestdataQuarkusConstraintProvider.class,
        solverConfig.getScoreDirectorFactoryConfig().getConstraintProviderClass());
    assertNotNull(solverFactory.buildSolver());
  }
}
