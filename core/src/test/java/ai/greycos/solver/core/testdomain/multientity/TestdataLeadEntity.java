package ai.greycos.solver.core.testdomain.multientity;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
