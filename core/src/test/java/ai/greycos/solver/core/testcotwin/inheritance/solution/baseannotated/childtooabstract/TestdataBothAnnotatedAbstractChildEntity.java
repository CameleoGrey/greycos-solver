package ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtooabstract;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;

@PlanningEntity
public class TestdataBothAnnotatedAbstractChildEntity
    extends TestdataBothAnnotatedAbstractBaseEntity {

  public TestdataBothAnnotatedAbstractChildEntity() {}

  public TestdataBothAnnotatedAbstractChildEntity(long id) {
    super(id);
  }
}
