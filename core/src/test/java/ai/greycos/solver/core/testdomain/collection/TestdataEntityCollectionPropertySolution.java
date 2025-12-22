package ai.greycos.solver.core.testdomain.collection;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningSolution
public class TestdataEntityCollectionPropertySolution extends TestdataObject {

  public static SolutionDescriptor<TestdataEntityCollectionPropertySolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataEntityCollectionPropertySolution.class,
        TestdataEntityCollectionPropertyEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<TestdataEntityCollectionPropertyEntity> entityList;

  @PlanningScore private SimpleScore score;

  public TestdataEntityCollectionPropertySolution() {}

  public TestdataEntityCollectionPropertySolution(String code) {
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
  public List<TestdataEntityCollectionPropertyEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataEntityCollectionPropertyEntity> entityList) {
    this.entityList = entityList;
  }

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
