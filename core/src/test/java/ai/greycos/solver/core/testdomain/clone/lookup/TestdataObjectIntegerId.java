package ai.greycos.solver.core.testdomain.clone.lookup;

import ai.greycos.solver.core.api.domain.lookup.PlanningId;

public class TestdataObjectIntegerId {

  @PlanningId private final Integer id;

  public TestdataObjectIntegerId(Integer id) {
    this.id = id;
  }

  public Integer getId() {
    return id;
  }
}
