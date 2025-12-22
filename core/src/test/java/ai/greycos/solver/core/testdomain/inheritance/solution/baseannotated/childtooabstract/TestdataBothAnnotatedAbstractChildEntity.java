package ai.greycos.solver.core.testdomain.inheritance.solution.baseannotated.childtooabstract;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;

@PlanningEntity
public class TestdataBothAnnotatedAbstractChildEntity
    extends TestdataBothAnnotatedAbstractBaseEntity {

  public TestdataBothAnnotatedAbstractChildEntity() {}

  public TestdataBothAnnotatedAbstractChildEntity(long id) {
    super(id);
  }
}
