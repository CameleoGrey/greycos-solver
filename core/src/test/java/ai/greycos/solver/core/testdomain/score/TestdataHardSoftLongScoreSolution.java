package ai.greycos.solver.core.testdomain.score;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningSolution
public class TestdataHardSoftLongScoreSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataHardSoftLongScoreSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataHardSoftLongScoreSolution.class, TestdataEntity.class);
  }

  public static TestdataHardSoftLongScoreSolution generateSolution() {
    return generateSolution(5, 7);
  }

  public static TestdataHardSoftLongScoreSolution generateSolution(
      int valueListSize, int entityListSize) {
    TestdataHardSoftLongScoreSolution solution =
        new TestdataHardSoftLongScoreSolution("Generated Solution 0");
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

  HardSoftLongScore score;

  public TestdataHardSoftLongScoreSolution() {}

  public TestdataHardSoftLongScoreSolution(String code) {
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
  public HardSoftLongScore getScore() {
    return score;
  }

  public void setScore(HardSoftLongScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
