package greycos.solver.core.testcotwin.mixed.multientity;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity(comparatorClass = TestdataMixedMultiEntityFirstEntityComparator.class)
public class TestdataMixedMultiEntityFirstEntity extends TestdataObject {

  @PlanningListVariable(valueRangeProviderRefs = "valueRange")
  private List<TestdataMixedMultiEntityFirstValue> valueList;

  private int difficulty;

  public TestdataMixedMultiEntityFirstEntity() {
    // Required for cloner
  }

  public TestdataMixedMultiEntityFirstEntity(String code, int difficulty) {
    super(code);
    valueList = new ArrayList<>();
    this.difficulty = difficulty;
  }

  public List<TestdataMixedMultiEntityFirstValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataMixedMultiEntityFirstValue> valueList) {
    this.valueList = valueList;
  }

  public int getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(int difficulty) {
    this.difficulty = difficulty;
  }
}
