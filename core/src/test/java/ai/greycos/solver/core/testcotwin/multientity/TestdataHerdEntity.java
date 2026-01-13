package ai.greycos.solver.core.testcotwin.multientity;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataHerdEntity extends TestdataObject {

  public static EntityDescriptor<TestdataMultiEntitySolution> buildEntityDescriptor() {
    return TestdataMultiEntitySolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataHerdEntity.class);
  }

  private TestdataLeadEntity leadEntity;

  public TestdataHerdEntity() {}

  public TestdataHerdEntity(String code) {
    super(code);
  }

  public TestdataHerdEntity(String code, TestdataLeadEntity leadEntity) {
    super(code);
    this.leadEntity = leadEntity;
  }

  @PlanningVariable(valueRangeProviderRefs = "leadEntityRange")
  public TestdataLeadEntity getLeadEntity() {
    return leadEntity;
  }

  public void setLeadEntity(TestdataLeadEntity leadEntity) {
    this.leadEntity = leadEntity;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
