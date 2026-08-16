package greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtoo;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataBothAnnotatedSolution extends TestdataObject {

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "valueRange")
  private List<TestdataValue> valueList;

  @ValueRangeProvider(id = "subValueRange")
  @ProblemFactCollectionProperty
  private List<TestdataValue> subValueList;

  @PlanningEntityCollectionProperty private List<? extends TestdataEntity> entityList;
  @PlanningScore private SimpleScore score;
  private ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides;

  public TestdataBothAnnotatedSolution() {}

  public TestdataBothAnnotatedSolution(String code) {
    super(code);
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataValue> getSubValueList() {
    return subValueList;
  }

  public void setSubValueList(List<TestdataValue> subValueList) {
    this.subValueList = subValueList;
  }

  public List<? extends TestdataEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<? extends TestdataEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  public ConstraintWeightOverrides<SimpleScore> getConstraintWeightOverrides() {
    return constraintWeightOverrides;
  }

  public void setConstraintWeightOverrides(
      ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides) {
    this.constraintWeightOverrides = constraintWeightOverrides;
  }
}
