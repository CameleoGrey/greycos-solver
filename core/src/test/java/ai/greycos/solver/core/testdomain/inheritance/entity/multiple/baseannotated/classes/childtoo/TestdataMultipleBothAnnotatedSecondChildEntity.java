package ai.greycos.solver.core.testdomain.inheritance.entity.multiple.baseannotated.classes.childtoo;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;

@PlanningEntity
public class TestdataMultipleBothAnnotatedSecondChildEntity
    extends TestdataMultipleBothAnnotatedBaseEntity {

  public TestdataMultipleBothAnnotatedSecondChildEntity() {}

  public TestdataMultipleBothAnnotatedSecondChildEntity(long id) {
    super(id);
  }
}
