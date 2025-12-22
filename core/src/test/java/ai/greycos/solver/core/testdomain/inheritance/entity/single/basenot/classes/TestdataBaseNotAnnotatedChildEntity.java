package ai.greycos.solver.core.testdomain.inheritance.entity.single.basenot.classes;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;

@PlanningEntity
public class TestdataBaseNotAnnotatedChildEntity extends TestdataBaseNotAnnotatedBaseEntity {

  public TestdataBaseNotAnnotatedChildEntity() {}

  public TestdataBaseNotAnnotatedChildEntity(long id) {
    super(id);
  }
}
