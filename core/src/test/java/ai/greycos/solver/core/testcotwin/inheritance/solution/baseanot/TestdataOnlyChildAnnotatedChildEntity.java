package ai.greycos.solver.core.testcotwin.inheritance.solution.baseanot;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;

@PlanningEntity
public class TestdataOnlyChildAnnotatedChildEntity extends TestdataOnlyAnnotatedBaseEntity {

  public TestdataOnlyChildAnnotatedChildEntity() {}

  public TestdataOnlyChildAnnotatedChildEntity(long id) {
    super(id);
  }
}
