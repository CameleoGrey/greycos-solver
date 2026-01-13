package ai.greycos.solver.quarkus.it.cotwin;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public interface TestdataStringLengthShadowEntityInterface {

  @PlanningVariable(valueRangeProviderRefs = {"valueRange", "valueRangeWithParameter"})
  String getValue();

  void setValue(String value);

  List<String> getValueList();

  @ValueRangeProvider(id = "valueRangeWithParameter")
  default List<String> getValueRangeWithParameter(TestdataStringLengthShadowSolution solution) {
    return solution.getValueList();
  }

  @ValueRangeProvider(id = "valueRange")
  default List<String> getValueRange() {
    return getValueList();
  }
}
