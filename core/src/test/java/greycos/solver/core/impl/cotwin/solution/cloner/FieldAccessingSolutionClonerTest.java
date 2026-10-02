package greycos.solver.core.impl.cotwin.solution.cloner;

import static greycos.solver.core.testutil.PlannerAssert.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.reflect.accessmodifier.TestdataAccessModifierSolution;

import org.junit.jupiter.api.Test;

class FieldAccessingSolutionClonerTest extends AbstractSolutionClonerTest {

  @Override
  protected <Solution_> SolutionCloner<Solution_> createSolutionCloner(
      SolutionDescriptor<Solution_> solutionDescriptor) {
    return new FieldAccessingSolutionCloner<>(solutionDescriptor);
  }

  @Test
  void cloneAccessModifierSolution() {
    var staticObject = new Object();
    TestdataAccessModifierSolution.setStaticField(staticObject);

    var solutionDescriptor = TestdataAccessModifierSolution.buildSolutionDescriptor();
    var cloner = createSolutionCloner(solutionDescriptor);

    var val1 = new TestdataValue("1");
    var val2 = new TestdataValue("2");
    var val3 = new TestdataValue("3");
    var a = new TestdataEntity("a", val1);
    var b = new TestdataEntity("b", val1);
    var c = new TestdataEntity("c", val3);
    var d = new TestdataEntity("d", val3);

    var original = new TestdataAccessModifierSolution("solution");
    original.setWriteOnlyField("writeHello");
    var valueList = Arrays.asList(val1, val2, val3);
    original.setValueList(valueList);
    var originalEntityList = Arrays.asList(a, b, c, d);
    original.setEntityList(originalEntityList);

    var clone = cloner.cloneSolution(original);

    assertThat(TestdataAccessModifierSolution.getStaticFinalField())
        .isSameAs("staticFinalFieldValue");
    assertThat(TestdataAccessModifierSolution.getStaticField()).isSameAs(staticObject);

    assertThat(clone).isNotSameAs(original);
    assertCode("solution", clone);
    assertThat(clone.getFinalField()).isEqualTo(original.getFinalField());
    assertThat(clone.getReadOnlyField()).isEqualTo("readHello");
    assertThat(clone.getValueList()).isSameAs(valueList);
    assertThat(clone.getScore()).isEqualTo(original.getScore());

    var cloneEntityList = clone.getEntityList();
    assertThat(cloneEntityList).hasSize(4).isNotSameAs(originalEntityList);
    var cloneA = cloneEntityList.get(0);
    var cloneB = cloneEntityList.get(1);
    var cloneC = cloneEntityList.get(2);
    var cloneD = cloneEntityList.get(3);
    assertEntityClone(a, cloneA, "a", "1");
    assertEntityClone(b, cloneB, "b", "1");
    assertEntityClone(c, cloneC, "c", "3");
    assertEntityClone(d, cloneD, "d", "3");

    assertThat(cloneB).isNotSameAs(b);
    b.setValue(val2);
    assertCode("2", b.getValue());
    // Clone remains unchanged
    assertCode("1", cloneB.getValue());
  }
}
