package ai.greycos.solver.quarkus.testdomain.superclass;

import ai.greycos.solver.core.api.domain.lookup.PlanningId;

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
