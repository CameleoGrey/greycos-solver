package ai.greycos.solver.core.testcotwin.invalid.multivar;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariableGraphType;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.chained.TestdataChainedObject;
import ai.greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

@PlanningEntity
public class TestdataInvalidMultiVarEntity extends TestdataObject implements TestdataChainedObject {

  @PlanningVariable(
      valueRangeProviderRefs = {"chainedEntityRange", "chainedAnchorRange"},
      graphType = PlanningVariableGraphType.CHAINED)
  private TestdataChainedObject chainedValue;

  @PlanningListVariable(valueRangeProviderRefs = "valueRange")
  private List<TestdataMixedValue> valueList;

  public TestdataInvalidMultiVarEntity(String code) {
    super(code);
  }

  public TestdataChainedObject getChainedValue() {
    return chainedValue;
  }

  public void setChainedValue(TestdataChainedObject chainedValue) {
    this.chainedValue = chainedValue;
  }

  public List<TestdataMixedValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataMixedValue> valueList) {
    this.valueList = valueList;
  }
}
