package greycos.solver.core.testcotwin.valuerange.parameter.invalid;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.invalid.TestdataInvalidCountEntityProvidingWithParameterSolution;

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
