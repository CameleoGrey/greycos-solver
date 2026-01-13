package ai.greycos.solver.spring.boot.it.cotwin;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class IntegrationTestEntity {
  @PlanningId private String id;

  @PlanningVariable(valueRangeProviderRefs = {"valueRange", "valueRangeWithParameter"})
  private IntegrationTestValue value;

  private List<IntegrationTestValue> valueList;

  public IntegrationTestEntity() {}

  public IntegrationTestEntity(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public IntegrationTestValue getValue() {
    return value;
  }

  public void setValue(IntegrationTestValue value) {
    this.value = value;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<IntegrationTestValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<IntegrationTestValue> valueList) {
    this.valueList = valueList;
  }

  @ValueRangeProvider(id = "valueRangeWithParameter")
  public List<IntegrationTestValue> getValueRangeWithParameter(IntegrationTestSolution solution) {
    return solution.getValueList();
  }
}
