package greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.interfaces.childtoo;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public interface TestdataMultipleBothAnnotatedInterfaceSecondEntity
    extends TestdataMultipleBothAnnotatedInterfaceBaseEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange2")
  String getValue2();

  void setValue2(String value2);
}
