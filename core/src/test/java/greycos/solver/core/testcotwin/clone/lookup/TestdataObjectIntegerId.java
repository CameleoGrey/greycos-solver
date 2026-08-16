package greycos.solver.core.testcotwin.clone.lookup;

import greycos.solver.core.api.cotwin.lookup.PlanningId;

public class TestdataObjectIntegerId {

  @PlanningId private final Integer id;

  public TestdataObjectIntegerId(Integer id) {
    this.id = id;
  }

  public Integer getId() {
    return id;
  }
}
