package greycos.solver.core.testcotwin.multientity;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataLeadEntity extends TestdataObject {

  public static EntityDescriptor<TestdataMultiEntitySolution> buildEntityDescriptor() {
    return TestdataMultiEntitySolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataLeadEntity.class);
  }

  private TestdataValue value;

  public TestdataLeadEntity() {}

  public TestdataLeadEntity(String code) {
    super(code);
  }

  public TestdataLeadEntity(String code, TestdataValue value) {
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

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
