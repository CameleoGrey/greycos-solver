package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.quarkus.testcotwin.gizmo.OnlyMultiArgsConstructorEntity;
import ai.greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorEntity;
import ai.greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorOnlyMultiConstructorTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          PrivateNoArgsConstructorConstraintProvider.class,
                          PrivateNoArgsConstructorSolution.class,
                          PrivateNoArgsConstructorEntity.class,
                          OnlyMultiArgsConstructorEntity.class))
          .assertException(
              t ->
                  assertThat(t)
                      .hasMessageContainingAll(
                          "Class (",
                          OnlyMultiArgsConstructorEntity.class.getName(),
                          ") must have a no-args constructor so it can be constructed by GreyCOS."));

  @Inject SolverManager<PrivateNoArgsConstructorSolution, Long> solverManager;

  @Test
  void canConstructBeansWithPrivateConstructors() {
    fail("The build should fail");
  }
}
