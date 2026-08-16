package greycos.solver.core.testcotwin.invalid.constraintweightoverrides;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataInvalidConstraintWeightOverridesSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataInvalidConstraintWeightOverridesSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataInvalidConstraintWeightOverridesSolution.class, TestdataEntity.class);
  }

  public static TestdataInvalidConstraintWeightOverridesSolution generateSolution(
      int valueListSize, int entityListSize) {
    var solution = new TestdataInvalidConstraintWeightOverridesSolution("Generated Solution 0");
    var valueList = new ArrayList<TestdataValue>(valueListSize);
    for (var i = 0; i < valueListSize; i++) {
      var value = new TestdataValue("Generated Value " + i);
      valueList.add(value);
    }
    solution.setValueList(valueList);
    var entityList = new ArrayList<TestdataEntity>(entityListSize);
    for (var i = 0; i < entityListSize; i++) {
      var value = valueList.get(i % valueListSize);
      var entity = new TestdataEntity("Generated Entity " + i, value);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    solution.setConstraintWeightOverrides(ConstraintWeightOverrides.none());
    return solution;
  }

  private ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides;
  private ConstraintWeightOverrides<SimpleScore> secondConstraintWeightOverrides;
  private List<TestdataValue> valueList;
  private List<TestdataEntity> entityList;

  private SimpleScore score;

  public TestdataInvalidConstraintWeightOverridesSolution() {}

  public TestdataInvalidConstraintWeightOverridesSolution(String code) {
    super(code);
  }

  public ConstraintWeightOverrides<SimpleScore> getConstraintWeightOverrides() {
    return constraintWeightOverrides;
  }

  public void setConstraintWeightOverrides(
      ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides) {
    this.constraintWeightOverrides = constraintWeightOverrides;
  }

  public ConstraintWeightOverrides<SimpleScore> getSecondConstraintWeightOverrides() {
    return secondConstraintWeightOverrides;
  }

  public void setSecondConstraintWeightOverrides(
      ConstraintWeightOverrides<SimpleScore> secondConstraintWeightOverrides) {
    this.secondConstraintWeightOverrides = secondConstraintWeightOverrides;
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
