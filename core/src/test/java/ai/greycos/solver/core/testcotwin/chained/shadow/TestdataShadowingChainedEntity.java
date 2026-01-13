package ai.greycos.solver.core.testcotwin.chained.shadow;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.AnchorShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariableGraphType;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataShadowingChainedEntity extends TestdataObject
    implements TestdataShadowingChainedObject {

  public static EntityDescriptor<TestdataShadowingChainedSolution> buildEntityDescriptor() {
    return TestdataShadowingChainedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataShadowingChainedEntity.class);
  }

  private TestdataShadowingChainedObject chainedObject;

  // Shadow variables
  private TestdataShadowingChainedEntity nextEntity;
  private TestdataShadowingChainedAnchor anchor;

  public TestdataShadowingChainedEntity() {}

  public TestdataShadowingChainedEntity(String code) {
    super(code);
  }

  public TestdataShadowingChainedEntity(String code, TestdataShadowingChainedObject chainedObject) {
    this(code);
    this.chainedObject = chainedObject;
  }

  @PlanningVariable(
      valueRangeProviderRefs = {"chainedAnchorRange", "chainedEntityRange"},
      graphType = PlanningVariableGraphType.CHAINED)
  public TestdataShadowingChainedObject getChainedObject() {
    return chainedObject;
  }

  public void setChainedObject(TestdataShadowingChainedObject chainedObject) {
    this.chainedObject = chainedObject;
  }

  @Override
  public TestdataShadowingChainedEntity getNextEntity() {
    return nextEntity;
  }

  @Override
  public void setNextEntity(TestdataShadowingChainedEntity nextEntity) {
    this.nextEntity = nextEntity;
  }

  @AnchorShadowVariable(sourceVariableName = "chainedObject")
  public TestdataShadowingChainedAnchor getAnchor() {
    return anchor;
  }

  public void setAnchor(TestdataShadowingChainedAnchor anchor) {
    this.anchor = anchor;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
