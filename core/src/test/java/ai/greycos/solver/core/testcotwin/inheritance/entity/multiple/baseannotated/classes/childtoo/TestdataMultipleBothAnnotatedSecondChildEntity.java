package ai.greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.classes.childtoo;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;

@PlanningEntity
public class TestdataMultipleBothAnnotatedSecondChildEntity
    extends TestdataMultipleBothAnnotatedBaseEntity {

  public TestdataMultipleBothAnnotatedSecondChildEntity() {}

  public TestdataMultipleBothAnnotatedSecondChildEntity(long id) {
    super(id);
  }
}
