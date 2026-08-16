package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.spring.boot.autoconfigure.invalid.type.InvalidEntityTypeSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.invalid.type.InvalidFieldTestdataSpringEntity;
import greycos.solver.spring.boot.autoconfigure.invalid.type.InvalidMethodTestdataSpringEntity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.TestExecutionListeners;

@TestExecutionListeners
class IncludeAbstractClassesEntityScannerTest {

  private static final Class<? extends Annotation>[] PLANNING_ENTITY_FIELD_ANNOTATIONS =
      new Class[] {
        PlanningPin.class,
        PlanningVariable.class,
        PlanningListVariable.class,
        IndexShadowVariable.class,
        InverseRelationShadowVariable.class,
        NextElementShadowVariable.class,
        PreviousElementShadowVariable.class,
        ShadowVariable.class
      };

  private final ApplicationContextRunner contextRunner;

  public IncludeAbstractClassesEntityScannerTest() {
    contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(InvalidEntityTypeSpringTestConfiguration.class);
  }

  @Test
  void testInvalidProperties() {
    contextRunner.run(
        context -> {
          IncludeAbstractClassesEntityScanner scanner =
              new IncludeAbstractClassesEntityScanner(context);

          // Each field
          Arrays.stream(PLANNING_ENTITY_FIELD_ANNOTATIONS)
              .forEach(
                  annotation -> {
                    List<Class<?>> classes = scanner.findClassesWithAnnotation(annotation);
                    assertThat(classes).hasSize(2);
                    assertThat(classes)
                        .contains(
                            InvalidFieldTestdataSpringEntity.class,
                            InvalidMethodTestdataSpringEntity.class);
                  });

          // All fields
          List<Class<?>> classes =
              scanner.findClassesWithAnnotation(PLANNING_ENTITY_FIELD_ANNOTATIONS);
          assertThat(classes).hasSize(2);
          assertThat(classes)
              .contains(
                  InvalidFieldTestdataSpringEntity.class, InvalidMethodTestdataSpringEntity.class);
        });
  }
}
