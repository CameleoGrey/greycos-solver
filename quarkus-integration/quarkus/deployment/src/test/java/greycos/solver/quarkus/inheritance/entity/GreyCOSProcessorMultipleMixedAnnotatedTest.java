package greycos.solver.quarkus.inheritance.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.classes.mixed.TestMultipleMixedConstraintProvider;
import greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.classes.mixed.TestdataMultipleMixedBaseEntity;
import greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.classes.mixed.TestdataMultipleMixedChildEntity;
import greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.classes.mixed.TestdataMultipleMixedSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorMultipleMixedAnnotatedTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestMultipleMixedConstraintProvider.class,
                          TestdataMultipleMixedSolution.class,
                          TestdataMultipleMixedChildEntity.class,
                          TestdataMultipleMixedBaseEntity.class))
          .assertException(
              exception -> {
                assertEquals(IllegalStateException.class, exception.getClass());
                assertTrue(exception.getMessage().contains("Mixed inheritance is not permitted."));
              });

  /**
   * This test validates the behavior of the solver when multiple inheritance is used, the child is
   * annotated with {@code @PlanningEntity} and it inherits from class and interface.
   */
  @Test
  void testMultipleBothClassesAnnotatedMixedPattern() {
    fail("The build should fail");
  }
}
