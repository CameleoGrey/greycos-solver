package ai.greycos.solver.core.testcotwin.chained;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariableGraphType;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataChainedEntity extends TestdataObject implements TestdataChainedObject {

  public static EntityDescriptor<TestdataChainedSolution> buildEntityDescriptor() {
    return TestdataChainedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataChainedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataChainedSolution>
      buildVariableDescriptorForChainedObject() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("chainedObject");
  }

  public static GenuineVariableDescriptor<TestdataChainedSolution>
      buildVariableDescriptorForUnchainedValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("unchainedValue");
  }

  private TestdataChainedObject chainedObject;
  private TestdataValue unchainedValue;

  public TestdataChainedEntity() {}

  public TestdataChainedEntity(String code) {
    super(code);
  }

  public TestdataChainedEntity(String code, TestdataChainedObject chainedObject) {
    this(code);
    this.chainedObject = chainedObject;
  }

  @PlanningVariable(
      valueRangeProviderRefs = {"chainedAnchorRange", "chainedEntityRange"},
      graphType = PlanningVariableGraphType.CHAINED)
  public TestdataChainedObject getChainedObject() {
    return chainedObject;
  }

  public void setChainedObject(TestdataChainedObject chainedObject) {
    this.chainedObject = chainedObject;
  }

  @PlanningVariable(valueRangeProviderRefs = {"unchainedRange"})
  public TestdataValue getUnchainedValue() {
    return unchainedValue;
  }

  public void setUnchainedValue(TestdataValue unchainedValue) {
    this.unchainedValue = unchainedValue;
  }

  public void getUnchainedObject(TestdataChainedObject chainedObject) {
    this.chainedObject = chainedObject;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
