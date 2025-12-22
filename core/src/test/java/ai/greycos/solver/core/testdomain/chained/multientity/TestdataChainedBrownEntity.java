package ai.greycos.solver.core.testdomain.chained.multientity;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.api.domain.variable.PlanningVariableGraphType;
import ai.greycos.solver.core.testdomain.TestdataObject;

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
