package greycos.solver.core.impl.cotwin.solution.cloner.gizmo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.testcotwin.clone.deepcloning.AnnotatedTestdataVariousTypes;
import greycos.solver.core.testcotwin.clone.deepcloning.ExtraDeepClonedObject;
import greycos.solver.core.testcotwin.clone.deepcloning.TestdataDeepCloningEntity;
import greycos.solver.core.testcotwin.clone.deepcloning.TestdataVariousTypes;
import greycos.solver.core.testcotwin.clone.deepcloning.field.TestdataFieldAnnotatedDeepCloningEntity;
import greycos.solver.core.testcotwin.clone.deepcloning.field.TestdataFieldAnnotatedDeepCloningSolution;

import org.junit.jupiter.api.Test;

class GizmoCloningUtilsTest {

  @Test
  void getDeepClonedClasses() {
    assertThat(
            GizmoCloningUtils.getDeepClonedClasses(
                TestdataFieldAnnotatedDeepCloningSolution.buildSolutionDescriptor(),
                List.of(TestdataDeepCloningEntity.class)))
        .containsExactlyInAnyOrder(
            TestdataDeepCloningEntity.class,
            ExtraDeepClonedObject.class,
            TestdataFieldAnnotatedDeepCloningEntity.class,
            AnnotatedTestdataVariousTypes.class,
            TestdataFieldAnnotatedDeepCloningSolution.class,
            TestdataVariousTypes.class);
  }
}
