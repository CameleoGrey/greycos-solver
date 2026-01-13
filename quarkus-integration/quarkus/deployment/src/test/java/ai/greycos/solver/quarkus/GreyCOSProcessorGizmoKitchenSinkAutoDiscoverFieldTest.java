package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import ai.greycos.solver.quarkus.testcotwin.gizmo.DummyConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.gizmo.DummyVariableListener;
import ai.greycos.solver.quarkus.testcotwin.gizmo.TestDataKitchenSinkAutoDiscoverFieldSolution;
import ai.greycos.solver.quarkus.testcotwin.gizmo.TestDataKitchenSinkEntity;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorGizmoKitchenSinkAutoDiscoverFieldTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestDataKitchenSinkAutoDiscoverFieldSolution.class,
                          TestDataKitchenSinkEntity.class,
                          DummyConstraintProvider.class,
                          DummyVariableListener.class))
          .assertException(
              t ->
                  assertThat(t)
                      .isInstanceOf(UnsupportedOperationException.class)
                      .hasMessageContaining("autoDiscoverMemberType"));

  @Test
  void solve() {
    fail("The build should fail");
  }
}
