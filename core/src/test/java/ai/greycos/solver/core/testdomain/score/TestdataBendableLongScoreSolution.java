package ai.greycos.solver.core.testdomain.score;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.bendablelong.BendableLongScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningSolution
public class TestdataBendableLongScoreSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataBendableLongScoreSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataBendableLongScoreSolution.class, TestdataEntity.class);
  }

  public static TestdataBendableLongScoreSolution generateSolution() {
    return generateSolution(5, 7);
  }

  public static TestdataBendableLongScoreSolution generateSolution(
      int valueListSize, int entityListSize) {
    TestdataBendableLongScoreSolution solution =
        new TestdataBendableLongScoreSolution("Generated Solution 0");
    List<TestdataValue> valueList = new ArrayList<>(valueListSize);
    for (int i = 0; i < valueListSize; i++) {
      TestdataValue value = new TestdataValue("Generated Value " + i);
      valueList.add(value);
    }
    solution.setValueList(valueList);
    List<TestdataEntity> entityList = new ArrayList<>(entityListSize);
    for (int i = 0; i < entityListSize; i++) {
      TestdataValue value = valueList.get(i % valueListSize);
      TestdataEntity entity = new TestdataEntity("Generated Entity " + i, value);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  private List<TestdataValue> valueList;
  private List<TestdataEntity> entityList;

  BendableLongScore score;

  public TestdataBendableLongScoreSolution() {}

  public TestdataBendableLongScoreSolution(String code) {
    super(code);
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

  @PlanningScore(bendableHardLevelsSize = 1, bendableSoftLevelsSize = 2)
  public BendableLongScore getScore() {
    return score;
  }

  public void setScore(BendableLongScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
