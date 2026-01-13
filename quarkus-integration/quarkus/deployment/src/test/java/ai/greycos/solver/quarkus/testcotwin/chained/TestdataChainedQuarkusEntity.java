package ai.greycos.solver.quarkus.testcotwin.chained;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariableGraphType;

@PlanningEntity
public class TestdataChainedQuarkusEntity implements TestdataChainedQuarkusObject {

  @PlanningVariable(
      valueRangeProviderRefs = {"chainedAnchorRange", "chainedEntityRange"},
      graphType = PlanningVariableGraphType.CHAINED)
  private TestdataChainedQuarkusObject previous;

  private TestdataChainedQuarkusEntity next;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public TestdataChainedQuarkusObject getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataChainedQuarkusObject previous) {
    this.previous = previous;
  }

  @Override
  public TestdataChainedQuarkusEntity getNext() {
    return next;
  }

  @Override
  public void setNext(TestdataChainedQuarkusEntity next) {
    this.next = next;
  }
}
