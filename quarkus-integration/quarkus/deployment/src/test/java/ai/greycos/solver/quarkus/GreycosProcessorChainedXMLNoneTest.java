package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.testdomain.chained.TestdataChainedQuarkusAnchor;
import ai.greycos.solver.quarkus.testdomain.chained.TestdataChainedQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.chained.TestdataChainedQuarkusEntity;
import ai.greycos.solver.quarkus.testdomain.chained.TestdataChainedQuarkusObject;
import ai.greycos.solver.quarkus.testdomain.chained.TestdataChainedQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorChainedXMLNoneTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataChainedQuarkusObject.class,
                          TestdataChainedQuarkusAnchor.class,
                          TestdataChainedQuarkusEntity.class,
                          TestdataChainedQuarkusSolution.class,
                          TestdataChainedQuarkusConstraintProvider.class));

  @Inject SolverConfig solverConfig;
  @Inject SolverFactory<TestdataChainedQuarkusSolution> solverFactory;

  @Test
  void solverConfigXml_default() {
    assertThat(solverConfig).isNotNull();
    assertThat(solverConfig.getSolutionClass()).isEqualTo(TestdataChainedQuarkusSolution.class);
    assertThat(solverConfig.getEntityClassList())
        .containsExactlyInAnyOrder(
            TestdataChainedQuarkusObject.class,
            TestdataChainedQuarkusEntity.class,
            TestdataChainedQuarkusAnchor.class);
    assertThat(solverConfig.getScoreDirectorFactoryConfig().getConstraintProviderClass())
        .isEqualTo(TestdataChainedQuarkusConstraintProvider.class);
    // No termination defined (solverConfig.xml isn't included)
    assertThat(solverConfig.getTerminationConfig().getSecondsSpentLimit()).isNull();
    assertThat(solverFactory).isNotNull();
    assertThat(solverFactory.buildSolver()).isNotNull();
  }
}
