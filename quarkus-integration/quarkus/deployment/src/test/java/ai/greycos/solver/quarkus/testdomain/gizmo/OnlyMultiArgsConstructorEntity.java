package ai.greycos.solver.quarkus.testdomain.gizmo;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class OnlyMultiArgsConstructorEntity extends PrivateNoArgsConstructorEntity {
  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String anotherValue;

  public OnlyMultiArgsConstructorEntity(String id) {
    super(id);
  }
}
