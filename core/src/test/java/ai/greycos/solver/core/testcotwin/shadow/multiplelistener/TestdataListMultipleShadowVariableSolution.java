package ai.greycos.solver.core.testcotwin.shadow.multiplelistener;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataListMultipleShadowVariableSolution {

  public static SolutionDescriptor<TestdataListMultipleShadowVariableSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataListMultipleShadowVariableSolution.class,
        TestdataListMultipleShadowVariableEntity.class,
        TestdataListMultipleShadowVariableValue.class);
  }

  public static TestdataListMultipleShadowVariableSolution generateSolution(
      int valueListSize, int entityListSize) {
    var solution = new TestdataListMultipleShadowVariableSolution();
    var valueList = new ArrayList<TestdataListMultipleShadowVariableValue>(valueListSize);
    for (var i = 0; i < valueListSize; i++) {
      var value = new TestdataListMultipleShadowVariableValue("Generated Value " + i);
      valueList.add(value);
    }
    solution.setValueList(valueList);
    var entityList = new ArrayList<TestdataListMultipleShadowVariableEntity>(entityListSize);
    for (var i = 0; i < entityListSize; i++) {
      var value = valueList.get(i % valueListSize);
      var entity = new TestdataListMultipleShadowVariableEntity("Generated Entity " + i, value);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "valueRange")
  private List<TestdataListMultipleShadowVariableValue> valueList;

  @PlanningEntityCollectionProperty
  private List<TestdataListMultipleShadowVariableEntity> entityList;

  @PlanningScore private SimpleScore score;

  public List<TestdataListMultipleShadowVariableValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataListMultipleShadowVariableValue> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataListMultipleShadowVariableEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataListMultipleShadowVariableEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
