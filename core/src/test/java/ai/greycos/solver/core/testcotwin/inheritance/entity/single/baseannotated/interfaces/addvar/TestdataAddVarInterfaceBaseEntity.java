package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.interfaces.addvar;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public interface TestdataAddVarInterfaceBaseEntity {

  @PlanningId
  Long getId();

  void setId(Long id);

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String getValue();

  void setValue(String value);
}
