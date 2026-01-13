package ai.greycos.solver.core.testcotwin.pinned.chained;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariableGraphType;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.chained.TestdataChainedObject;

@PlanningEntity(pinningFilter = TestdataChainedEntityPinningFilter.class)
public class TestdataPinnedChainedEntity extends TestdataObject implements TestdataChainedObject {

  public static EntityDescriptor<TestdataPinnedChainedSolution> buildEntityDescriptor() {
    return TestdataPinnedChainedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataPinnedChainedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataPinnedChainedSolution>
      buildVariableDescriptorForChainedObject() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("chainedObject");
  }

  private TestdataChainedObject chainedObject;
  private boolean pinned;

  public TestdataPinnedChainedEntity() {}

  public TestdataPinnedChainedEntity(String code) {
    super(code);
  }

  public TestdataPinnedChainedEntity(String code, TestdataChainedObject chainedObject) {
    this(code);
    this.chainedObject = chainedObject;
  }

  public TestdataPinnedChainedEntity(
      String code, TestdataChainedObject chainedObject, boolean pinned) {
    this(code, chainedObject);
    this.pinned = pinned;
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

  public boolean isPinned() {
    return pinned;
  }

  public void setPinned(boolean pinned) {
    this.pinned = pinned;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
