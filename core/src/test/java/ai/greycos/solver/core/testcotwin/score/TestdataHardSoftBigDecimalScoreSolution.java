package ai.greycos.solver.core.testcotwin.score;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftBigDecimalScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataHardSoftBigDecimalScoreSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataHardSoftBigDecimalScoreSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataHardSoftBigDecimalScoreSolution.class, TestdataEntity.class);
  }

  public static TestdataHardSoftBigDecimalScoreSolution generateSolution() {
    return generateSolution(5, 7);
  }

  public static TestdataHardSoftBigDecimalScoreSolution generateSolution(
      int valueListSize, int entityListSize) {
    TestdataHardSoftBigDecimalScoreSolution solution =
        new TestdataHardSoftBigDecimalScoreSolution("Generated Solution 0");
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

  HardSoftBigDecimalScore score;

  public TestdataHardSoftBigDecimalScoreSolution() {}

  public TestdataHardSoftBigDecimalScoreSolution(String code) {
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

  @PlanningScore
  public HardSoftBigDecimalScore getScore() {
    return score;
  }

  public void setScore(HardSoftBigDecimalScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
