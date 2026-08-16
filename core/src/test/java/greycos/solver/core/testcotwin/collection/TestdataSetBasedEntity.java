package greycos.solver.core.testcotwin.collection;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataSetBasedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataSetBasedSolution> buildEntityDescriptor() {
    return TestdataSetBasedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataSetBasedEntity.class);
  }

  private TestdataValue value;

  public TestdataSetBasedEntity() {}

  public TestdataSetBasedEntity(String code) {
    super(code);
  }

  public TestdataSetBasedEntity(String code, TestdataValue value) {
    this(code);
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
