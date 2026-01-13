package ai.greycos.solver.core.testcotwin.shadow.cyclic;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataSevenNonCyclicShadowedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataSevenNonCyclicShadowedSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataSevenNonCyclicShadowedSolution.class, TestdataSevenNonCyclicShadowedEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<TestdataSevenNonCyclicShadowedEntity> entityList;

  private SimpleScore score;

  public TestdataSevenNonCyclicShadowedSolution() {}

  public TestdataSevenNonCyclicShadowedSolution(String code) {
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
  public List<TestdataSevenNonCyclicShadowedEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataSevenNonCyclicShadowedEntity> entityList) {
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
