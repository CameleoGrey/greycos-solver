package ai.greycos.solver.core.testdomain.shadow.full;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningListVariable;
import ai.greycos.solver.core.api.domain.variable.ShadowVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;

@PlanningEntity
public class TestdataShadowedFullEntity extends TestdataObject {
  @PlanningListVariable List<TestdataShadowedFullValue> valueList;

  @ShadowVariable(
      variableListenerClass = TestdataShadowedFullConsistencyListVariableListener.class,
      sourceVariableName = "valueList")
  Boolean isConsistent;

  public TestdataShadowedFullEntity() {
    this.valueList = new ArrayList<>();
  }

  public TestdataShadowedFullEntity(String code) {
    super(code);
    this.valueList = new ArrayList<>();
  }

  public List<TestdataShadowedFullValue> getValueList() {
    return valueList;
  }
}
