package ai.greycos.solver.core.testcotwin.inheritance.entity.single.basenot.interfaces;

import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

public interface TestdataBaseNotAnnotatedInterfaceBaseEntity {

  @PlanningId
  Long getId();

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String getValue();
}
