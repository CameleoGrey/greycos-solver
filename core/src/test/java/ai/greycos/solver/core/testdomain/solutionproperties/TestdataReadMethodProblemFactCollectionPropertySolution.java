package ai.greycos.solver.core.testdomain.solutionproperties;

import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningSolution
public class TestdataReadMethodProblemFactCollectionPropertySolution extends TestdataObject {

  public static SolutionDescriptor<TestdataReadMethodProblemFactCollectionPropertySolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataReadMethodProblemFactCollectionPropertySolution.class, TestdataEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<TestdataEntity> entityList;

  private SimpleScore score;

  public TestdataReadMethodProblemFactCollectionPropertySolution() {}

  public TestdataReadMethodProblemFactCollectionPropertySolution(String code) {
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
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  @ProblemFactCollectionProperty
  public List<String> createProblemFacts() {
    return Arrays.asList("a", "b", "c");
  }
}
