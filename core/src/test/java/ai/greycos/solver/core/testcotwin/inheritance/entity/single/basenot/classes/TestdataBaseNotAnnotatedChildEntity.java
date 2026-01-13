package ai.greycos.solver.core.testcotwin.inheritance.entity.single.basenot.classes;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;

@PlanningEntity
public class TestdataBaseNotAnnotatedChildEntity extends TestdataBaseNotAnnotatedBaseEntity {

  public TestdataBaseNotAnnotatedChildEntity() {}

  public TestdataBaseNotAnnotatedChildEntity(long id) {
    super(id);
  }
}
