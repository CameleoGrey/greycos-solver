package ai.greycos.solver.core.testcotwin.difficultyweight;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataDifficultyWeightSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataDifficultyWeightSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDifficultyWeightSolution.class, TestdataDifficultyWeightEntity.class);
  }

  public static TestdataDifficultyWeightSolution generateSolution() {
    return generateSolution(5, 7);
  }

  public static TestdataDifficultyWeightSolution generateSolution(
      int valueListSize, int entityListSize) {
    TestdataDifficultyWeightSolution solution =
        new TestdataDifficultyWeightSolution("Generated Solution 0");
    List<TestdataDifficultyWeightValue> valueList = new ArrayList<>(valueListSize);
    for (int i = 0; i < valueListSize; i++) {
      TestdataDifficultyWeightValue value =
          new TestdataDifficultyWeightValue("Generated Value " + i);
      valueList.add(value);
    }
    solution.setValueList(valueList);
    List<TestdataDifficultyWeightEntity> entityList = new ArrayList<>(entityListSize);
    for (int i = 0; i < entityListSize; i++) {
      TestdataDifficultyWeightValue value = valueList.get(i % valueListSize);
      TestdataDifficultyWeightEntity entity =
          new TestdataDifficultyWeightEntity("Generated Entity " + i, value);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  private List<TestdataDifficultyWeightValue> valueList;
  private List<TestdataDifficultyWeightEntity> entityList;

  private SimpleScore score;

  public TestdataDifficultyWeightSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataDifficultyWeightValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataDifficultyWeightValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataDifficultyWeightEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataDifficultyWeightEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
