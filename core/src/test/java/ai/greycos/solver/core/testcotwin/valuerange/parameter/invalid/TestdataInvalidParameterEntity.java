package ai.greycos.solver.core.testcotwin.valuerange.parameter.invalid;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.invalid.TestdataInvalidCountEntityProvidingWithParameterSolution;

@PlanningEntity
public class TestdataInvalidParameterEntity extends TestdataObject {

  public static EntityDescriptor<TestdataInvalidCountEntityProvidingWithParameterSolution>
      buildEntityDescriptor() {
    return TestdataInvalidCountEntityProvidingWithParameterSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataInvalidParameterEntity.class);
  }

  private TestdataValue value;

  public TestdataInvalidParameterEntity() {
    // Required for cloning
  }

  public TestdataInvalidParameterEntity(String code) {
    this(code, null);
  }

  public TestdataInvalidParameterEntity(String code, TestdataValue value) {
    super(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }
}
