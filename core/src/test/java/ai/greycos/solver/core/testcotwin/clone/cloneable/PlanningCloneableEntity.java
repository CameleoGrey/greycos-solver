package ai.greycos.solver.core.testcotwin.clone.cloneable;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.solution.cloner.PlanningCloneable;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class PlanningCloneableEntity implements PlanningCloneable<PlanningCloneableEntity> {
  public String code;
  @PlanningVariable public TestdataValue value;

  public PlanningCloneableEntity(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  @Override
  public PlanningCloneableEntity createNewInstance() {
    return new PlanningCloneableEntity(code);
  }
}
