package ai.greycos.solver.core.testdomain.valuerange.parameter.invalid;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testdomain.valuerange.entityproviding.parameter.invalid.TestdataInvalidCountEntityProvidingWithParameterSolution;

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
