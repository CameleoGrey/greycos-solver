package ai.greycos.solver.core.testdomain.clone.deepcloning;

import ai.greycos.solver.core.api.domain.solution.cloner.DeepPlanningClone;

@DeepPlanningClone
public class ExtraDeepClonedObject {
  public String id;

  public ExtraDeepClonedObject() {}

  public ExtraDeepClonedObject(String id) {
    this.id = id;
  }
}
