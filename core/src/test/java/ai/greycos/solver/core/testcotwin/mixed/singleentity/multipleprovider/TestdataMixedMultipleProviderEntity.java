package ai.greycos.solver.core.testcotwin.mixed.singleentity.multipleprovider;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.multivar.TestdataOtherValue;

@PlanningEntity
public class TestdataMixedMultipleProviderEntity extends TestdataObject {

  @PlanningVariable private TestdataOtherValue basicValue;

  @PlanningListVariable private List<TestdataValue> valueList;

  private final List<TestdataValue> valueRange;

  public TestdataMixedMultipleProviderEntity() {
    this.valueRange = new ArrayList<>();
  }

  public TestdataMixedMultipleProviderEntity(String code, List<TestdataValue> valueRange) {
    super(code);
    this.valueRange = valueRange;
    valueList = new ArrayList<>();
  }

  public TestdataOtherValue getBasicValue() {
    return basicValue;
  }

  public void setBasicValue(TestdataOtherValue basicValue) {
    this.basicValue = basicValue;
  }

  @ValueRangeProvider
  public List<TestdataValue> getValueRange() {
    return valueRange;
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }
}
