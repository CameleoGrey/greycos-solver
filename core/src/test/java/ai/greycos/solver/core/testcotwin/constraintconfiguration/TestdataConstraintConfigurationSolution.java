package ai.greycos.solver.core.testcotwin.constraintconfiguration;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintConfigurationProvider;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@Deprecated(forRemoval = true, since = "1.13.0")
@PlanningSolution
public class TestdataConstraintConfigurationSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataConstraintConfigurationSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataConstraintConfigurationSolution.class, TestdataEntity.class);
  }

  public static TestdataConstraintConfigurationSolution generateSolution(
      int valueListSize, int entityListSize) {
    TestdataConstraintConfigurationSolution solution =
        new TestdataConstraintConfigurationSolution("Generated Solution 0");
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
    solution.setConstraintConfiguration(new TestdataConstraintConfiguration(solution.getCode()));
    return solution;
  }

  private TestdataConstraintConfiguration constraintConfiguration;
  private List<TestdataValue> valueList;
  private List<TestdataEntity> entityList;

  private SimpleScore score;

  public TestdataConstraintConfigurationSolution() {}

  public TestdataConstraintConfigurationSolution(String code) {
    super(code);
  }

  @ConstraintConfigurationProvider
  public TestdataConstraintConfiguration getConstraintConfiguration() {
    return constraintConfiguration;
  }

  public void setConstraintConfiguration(TestdataConstraintConfiguration constraintConfiguration) {
    this.constraintConfiguration = constraintConfiguration;
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
