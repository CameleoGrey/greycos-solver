package greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.interfaces.replacevar;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public interface TestdataReplaceVarInterfaceBaseEntity {

  @PlanningId
  Long getId();

  void setId(Long id);

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String getValue();

  void setValue(String value);
}
