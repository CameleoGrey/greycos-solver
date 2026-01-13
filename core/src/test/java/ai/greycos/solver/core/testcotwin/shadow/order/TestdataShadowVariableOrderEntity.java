package ai.greycos.solver.core.testcotwin.shadow.order;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PiggybackShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.DummyVariableListener;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataShadowVariableOrderEntity extends TestdataObject {

  public static EntityDescriptor<TestdataShadowVariableOrderSolution> buildEntityDescriptor() {
    return TestdataShadowVariableOrderSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataShadowVariableOrderEntity.class);
  }

  /*
   * The variables correspond to the scenario described in shadowVariableOrder.png. The last letter matches the variable name
   * on the diagram. 'xN' is an artificial prefix to force an order in which the fields are iterated that is different from
   * the alphabetical order of the original variable names (which, coincidentally, is the expected order of variable
   * listeners).
   */

  /** G -> F */
  @PiggybackShadowVariable(shadowVariableName = "x4F")
  private String x0G;

  /** D -> C */
  @ShadowVariable(variableListenerClass = DVariableListener.class, sourceVariableName = "x3C")
  private String x1D;

  /** E -> {B, C} */
  @ShadowVariable(variableListenerClass = EVariableListener.class, sourceVariableName = "x5B")
  @ShadowVariable(variableListenerClass = EVariableListener.class, sourceVariableName = "x3C")
  private String x2E;

  /** C -> A */
  @ShadowVariable(variableListenerClass = CVariableListener.class, sourceVariableName = "x6A")
  private String x3C;

  /** F -> E */
  @ShadowVariable(variableListenerClass = FGVariableListener.class, sourceVariableName = "x2E")
  private String x4F;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private TestdataValue x5B;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private TestdataValue x6A;

  public TestdataShadowVariableOrderEntity() {}

  public TestdataShadowVariableOrderEntity(String code) {
    super(code);
  }

  // ************************************************************************
  // Static inner classes
  // ************************************************************************

  abstract static class VariableListenerWithToString
      extends DummyVariableListener<
          TestdataShadowVariableOrderSolution, TestdataShadowVariableOrderEntity> {

    @Override
    public String toString() {
      return getClass().getSimpleName().replace("VariableListener", "");
    }
  }

  public static class CVariableListener extends VariableListenerWithToString {}

  public static class DVariableListener extends VariableListenerWithToString {}

  public static class EVariableListener extends VariableListenerWithToString {}

  public static class FGVariableListener extends VariableListenerWithToString {}
}
