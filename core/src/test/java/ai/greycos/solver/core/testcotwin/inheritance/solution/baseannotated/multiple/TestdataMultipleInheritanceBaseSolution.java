package ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.multiple;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;

@PlanningSolution
public class TestdataMultipleInheritanceBaseSolution {

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "valueRange")
  private List<String> valueList;

  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }
}
