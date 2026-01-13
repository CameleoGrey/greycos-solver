package ai.greycos.solver.quarkus;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.score.stream.ConstraintMetaModel;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorConstraintMetaModelTest {

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

  @Inject ConstraintMetaModel constraintMetaModel;

  @Test
  void singletonSolverFactory() {
    Assertions.assertThat(constraintMetaModel.getConstraints()).isNotEmpty();
  }
}
