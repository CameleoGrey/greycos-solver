package ai.greycos.solver.core.testcotwin.clone.deepcloning;

import ai.greycos.solver.core.api.cotwin.solution.cloner.DeepPlanningClone;

@DeepPlanningClone
public class ExtraDeepClonedObject {
  public String id;

  public ExtraDeepClonedObject() {}

  public ExtraDeepClonedObject(String id) {
    this.id = id;
  }
}
