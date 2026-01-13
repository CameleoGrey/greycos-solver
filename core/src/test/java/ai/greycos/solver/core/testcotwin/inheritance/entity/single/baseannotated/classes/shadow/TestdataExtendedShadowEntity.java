package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataExtendedShadowEntity {

  public int desiredId;

  @PlanningVariable(allowsUnassigned = true)
  public TestdataExtendedShadowVariable myPlanningVariable;

  public TestdataExtendedShadowEntity() {}

  public TestdataExtendedShadowEntity(int desiredId) {
    this.desiredId = desiredId;
  }
}
