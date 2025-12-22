package ai.greycos.solver.core.testdomain.score;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningSolution
public class TestdataHardMediumSoftBigDecimalScoreSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataHardMediumSoftBigDecimalScoreSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataHardMediumSoftBigDecimalScoreSolution.class, TestdataEntity.class);
  }

  public static TestdataHardMediumSoftBigDecimalScoreSolution generateSolution() {
    return generateSolution(5, 7);
  }

  public static TestdataHardMediumSoftBigDecimalScoreSolution generateSolution(
      int valueListSize, int entityListSize) {
    TestdataHardMediumSoftBigDecimalScoreSolution solution =
        new TestdataHardMediumSoftBigDecimalScoreSolution("Generated Solution 0");
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

  HardMediumSoftBigDecimalScore score;

  public TestdataHardMediumSoftBigDecimalScoreSolution() {}

  public TestdataHardMediumSoftBigDecimalScoreSolution(String code) {
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
  public HardMediumSoftBigDecimalScore getScore() {
    return score;
  }

  public void setScore(HardMediumSoftBigDecimalScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
