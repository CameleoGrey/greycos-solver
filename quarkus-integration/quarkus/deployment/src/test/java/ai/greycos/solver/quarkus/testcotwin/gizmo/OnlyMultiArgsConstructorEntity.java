package ai.greycos.solver.quarkus.testcotwin.gizmo;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class OnlyMultiArgsConstructorEntity extends PrivateNoArgsConstructorEntity {
  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String anotherValue;

  public OnlyMultiArgsConstructorEntity(String id) {
    super(id);
  }
}
