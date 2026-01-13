package ai.greycos.solver.core.testcotwin.collection;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataArrayBasedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataArrayBasedSolution> buildEntityDescriptor() {
    return TestdataArrayBasedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataArrayBasedEntity.class);
  }

  private TestdataArrayBasedEntity[] entities;

  private TestdataValue value;

  public TestdataArrayBasedEntity() {}

  public TestdataArrayBasedEntity(String code) {
    super(code);
  }

  public TestdataArrayBasedEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  public TestdataArrayBasedEntity[] getEntities() {
    return entities;
  }

  public void setEntities(TestdataArrayBasedEntity[] entities) {
    this.entities = entities;
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
