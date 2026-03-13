package ai.greycos.solver.core.testcotwin.solutionproperties.invalid;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataDuplicatePlanningScorePropertySolution extends TestdataObject {

  public static SolutionDescriptor<TestdataDuplicatePlanningScorePropertySolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDuplicatePlanningScorePropertySolution.class, TestdataEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<TestdataEntity> entityList;

  @PlanningScore // Duplicate
  private SimpleScore score;

  public TestdataDuplicatePlanningScorePropertySolution() {}

  public TestdataDuplicatePlanningScorePropertySolution(String code) {
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

  @PlanningScore // Duplicate
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
