package ai.greycos.solver.spring.boot.autoconfigure.chained.cotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariableGraphType;

@PlanningEntity
public class TestdataChainedSpringEntity implements TestdataChainedSpringObject {

  @PlanningVariable(
      valueRangeProviderRefs = {"chainedAnchorRange", "chainedEntityRange"},
      graphType = PlanningVariableGraphType.CHAINED)
  private TestdataChainedSpringObject previous;

  private TestdataChainedSpringEntity next;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public TestdataChainedSpringObject getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataChainedSpringObject previous) {
    this.previous = previous;
  }

  @Override
  public TestdataChainedSpringEntity getNext() {
    return next;
  }

  @Override
  public void setNext(TestdataChainedSpringEntity next) {
    this.next = next;
  }
}
