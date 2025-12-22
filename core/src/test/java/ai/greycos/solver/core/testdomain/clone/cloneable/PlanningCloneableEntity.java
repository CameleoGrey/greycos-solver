package ai.greycos.solver.core.testdomain.clone.cloneable;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.solution.cloner.PlanningCloneable;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
