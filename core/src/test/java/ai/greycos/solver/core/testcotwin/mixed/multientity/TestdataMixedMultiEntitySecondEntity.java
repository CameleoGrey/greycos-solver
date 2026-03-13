package ai.greycos.solver.core.testcotwin.mixed.multientity;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataMixedMultiEntitySecondEntity extends TestdataObject {

  @PlanningVariable(
      valueRangeProviderRefs = "otherValueRange",
      comparatorClass = TestdataMixedMultiEntitySecondValueComparator.class)
  private TestdataMixedMultiEntitySecondValue basicValue;

  @PlanningVariable(valueRangeProviderRefs = "otherValueRange")
  private TestdataMixedMultiEntitySecondValue secondBasicValue;

  public TestdataMixedMultiEntitySecondEntity() {
    // Required for cloner
  }

  public TestdataMixedMultiEntitySecondEntity(String code) {
    super(code);
  }

  public TestdataMixedMultiEntitySecondValue getBasicValue() {
    return basicValue;
  }

  public void setBasicValue(TestdataMixedMultiEntitySecondValue basicValue) {
    this.basicValue = basicValue;
  }

  public TestdataMixedMultiEntitySecondValue getSecondBasicValue() {
    return secondBasicValue;
  }

  public void setSecondBasicValue(TestdataMixedMultiEntitySecondValue secondBasicValue) {
    this.secondBasicValue = secondBasicValue;
  }
}
