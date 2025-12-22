package ai.greycos.solver.core.testdomain.inheritance.solution.baseannotated.childnot;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
