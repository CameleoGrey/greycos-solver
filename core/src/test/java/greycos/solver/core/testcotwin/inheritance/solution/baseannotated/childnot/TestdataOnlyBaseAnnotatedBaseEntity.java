package greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataOnlyBaseAnnotatedBaseEntity extends TestdataObject {

  public static final String VALUE_FIELD = "value";

  public static EntityDescriptor<TestdataSolution> buildEntityDescriptor() {
    return TestdataSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataOnlyBaseAnnotatedBaseEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataSolution> buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;

  public TestdataOnlyBaseAnnotatedBaseEntity() {}

  public TestdataOnlyBaseAnnotatedBaseEntity(String code) {
    super(code);
  }

  public TestdataOnlyBaseAnnotatedBaseEntity(String code, TestdataValue value) {
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
}
