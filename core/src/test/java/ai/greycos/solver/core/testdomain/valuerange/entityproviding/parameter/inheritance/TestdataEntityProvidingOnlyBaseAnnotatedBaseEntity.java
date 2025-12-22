package ai.greycos.solver.core.testdomain.valuerange.entityproviding.parameter.inheritance;

import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity
public class TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity extends TestdataObject {

  public static final String VALUE_FIELD = "value";

  public static EntityDescriptor<TestdataSolution> buildEntityDescriptor() {
    return TestdataSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataSolution> buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;

  public TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity() {}

  public TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity(String code) {
    super(code);
  }

  public TestdataEntityProvidingOnlyBaseAnnotatedBaseEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = {"valueRange", "otherValueRange"})
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getValueList(
      TestdataEntityProvidingEntityProvidingOnlyBaseAnnotatedExtendedSolution solution) {
    return solution.getValueList();
  }

  @ValueRangeProvider(id = "otherValueRange")
  public List<TestdataValue> getOtherValueList(
      TestdataEntityProvidingOnlyBaseAnnotatedSolution solution) {
    return solution.getValueList();
  }
}
