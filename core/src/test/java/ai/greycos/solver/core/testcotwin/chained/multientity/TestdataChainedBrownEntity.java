package ai.greycos.solver.core.testcotwin.chained.multientity;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariableGraphType;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataChainedBrownEntity extends TestdataObject
    implements TestdataChainedMultiEntityChainElement {

  private TestdataChainedMultiEntityChainElement previousChainElement;

  public TestdataChainedBrownEntity() {}

  public TestdataChainedBrownEntity(String code) {
    super(code);
  }

  @PlanningVariable(
      valueRangeProviderRefs = {"brownRange", "anchorRange"},
      graphType = PlanningVariableGraphType.CHAINED)
  public TestdataChainedMultiEntityChainElement getPreviousChainElement() {
    return previousChainElement;
  }

  public void setPreviousChainElement(TestdataChainedMultiEntityChainElement previousChainElement) {
    this.previousChainElement = previousChainElement;
  }
}
