package greycos.solver.core.testcotwin.shadow.declarative.basicinverse;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataObject;

/**
 * A basic (non-list) {@link PlanningVariable} value that is itself a planning entity holding its
 * own basic variable ("owner"). No {@code @InverseRelationShadowVariable} is declared anywhere for
 * the "group" variable of {@link TestdataBasicInverseEntity} that points here.
 */
@PlanningEntity
public class TestdataBasicInverseGroup extends TestdataObject {

  @PlanningVariable(valueRangeProviderRefs = "ownerRange")
  TestdataBasicInverseOwner owner;

  public TestdataBasicInverseGroup() {}

  public TestdataBasicInverseGroup(String code) {
    super(code);
  }

  public TestdataBasicInverseOwner getOwner() {
    return owner;
  }

  public void setOwner(TestdataBasicInverseOwner owner) {
    this.owner = owner;
  }
}
