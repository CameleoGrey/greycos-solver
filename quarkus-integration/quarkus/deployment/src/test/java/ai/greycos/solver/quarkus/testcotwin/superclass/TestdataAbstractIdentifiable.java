package ai.greycos.solver.quarkus.testcotwin.superclass;

import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;

abstract class TestdataAbstractIdentifiable {

  private Long id;

  public TestdataAbstractIdentifiable() {}

  public TestdataAbstractIdentifiable(long id) {
    this.id = id;
  }

  @PlanningId
  public Long getId() {
    return id;
  }
}
