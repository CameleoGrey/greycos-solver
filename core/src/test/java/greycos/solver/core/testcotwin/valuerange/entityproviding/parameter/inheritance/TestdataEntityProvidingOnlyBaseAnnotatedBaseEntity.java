package greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.inheritance;

import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

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
