package ai.greycos.solver.core.testdomain.pinned.unassignedvar;

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
public class TestdataPinnedAllowsUnassignedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataPinnedAllowsUnassignedSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataPinnedAllowsUnassignedSolution.class, TestdataPinnedAllowsUnassignedEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<TestdataPinnedAllowsUnassignedEntity> entityList;

  private SimpleScore score;

  public TestdataPinnedAllowsUnassignedSolution() {}

  public TestdataPinnedAllowsUnassignedSolution(String code) {
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
  public List<TestdataPinnedAllowsUnassignedEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataPinnedAllowsUnassignedEntity> entityList) {
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
