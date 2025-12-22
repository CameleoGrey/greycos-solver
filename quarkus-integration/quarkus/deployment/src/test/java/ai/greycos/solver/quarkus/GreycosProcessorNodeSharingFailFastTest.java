package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class GreycosProcessorNodeSharingFailFastTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver-config-xml",
              "ai/greycos/solver/quarkus/solverConfigWithNodeSharing.xml")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class)
                      .addAsResource("ai/greycos/solver/quarkus/solverConfigWithNodeSharing.xml"))
          .assertException(
              exception -> {
                assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAll(
                        "enabled automatic node sharing via SolverConfig, which is not allowed.",
                        "Enable automatic node sharing with the property",
                        "quarkus.greycos.solver.constraint-stream-automatic-node-sharing=true");
              });

  @Test
  void test() {
    fail("Should not call this method.");
  }
}
