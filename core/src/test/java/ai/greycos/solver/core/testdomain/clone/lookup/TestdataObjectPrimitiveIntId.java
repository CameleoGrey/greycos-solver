package ai.greycos.solver.core.testdomain.clone.lookup;

import ai.greycos.solver.core.api.domain.lookup.PlanningId;

public class TestdataObjectPrimitiveIntId {

  @PlanningId private final int id;

  public TestdataObjectPrimitiveIntId(int id) {
    this.id = id;
  }

  public Integer getId() {
    return id;
  }
}
