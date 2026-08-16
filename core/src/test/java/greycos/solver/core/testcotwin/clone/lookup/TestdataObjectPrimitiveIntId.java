package greycos.solver.core.testcotwin.clone.lookup;

import greycos.solver.core.api.cotwin.lookup.PlanningId;

public class TestdataObjectPrimitiveIntId {

  @PlanningId private final int id;

  public TestdataObjectPrimitiveIntId(int id) {
    this.id = id;
  }

  public Integer getId() {
    return id;
  }
}
