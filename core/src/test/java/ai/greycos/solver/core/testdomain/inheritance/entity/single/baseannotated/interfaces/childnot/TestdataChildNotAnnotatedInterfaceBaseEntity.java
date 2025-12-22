package ai.greycos.solver.core.testdomain.inheritance.entity.single.baseannotated.interfaces.childnot;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.lookup.PlanningId;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public interface TestdataChildNotAnnotatedInterfaceBaseEntity {

  @PlanningId
  Long getId();

  void setId(Long id);

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String getValue();

  void setValue(String value);
}
