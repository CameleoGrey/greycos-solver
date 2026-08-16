package greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class GreyCOSProcessorNodeSharingTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver-config-xml",
              "greycos/solver/quarkus/solverConfigWithNodeSharing.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addAsResource("greycos/solver/quarkus/solverConfigWithNodeSharing.xml"));

  @Inject SolverConfig solverConfig;

  @Test
  void enabledInSolverConfig() {
    assertTrue(
        solverConfig.getScoreDirectorFactoryConfig().getConstraintStreamAutomaticNodeSharing());
  }
}
