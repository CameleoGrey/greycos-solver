package ai.greycos.solver.core.testdomain.inheritance.entity.single.basenot.interfaces;

import ai.greycos.solver.core.api.domain.lookup.PlanningId;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

public interface TestdataBaseNotAnnotatedInterfaceBaseEntity {

  @PlanningId
  Long getId();

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String getValue();
}
