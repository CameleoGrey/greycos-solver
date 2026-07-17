package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.inject.Inject;

import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class GreyCOSProcessorNodeSharingOverrideTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver-config-xml",
              "ai/greycos/solver/quarkus/solverConfigWithNodeSharing.xml")
          .overrideConfigKey(
              "quarkus.greycos.solver.constraint-stream-automatic-node-sharing", "false")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addAsResource("ai/greycos/solver/quarkus/solverConfigWithNodeSharing.xml"));

  @Inject SolverConfig solverConfig;

  @Test
  void propertyOverridesSolverConfig() {
    assertFalse(
        solverConfig.getScoreDirectorFactoryConfig().getConstraintStreamAutomaticNodeSharing());
  }
}
