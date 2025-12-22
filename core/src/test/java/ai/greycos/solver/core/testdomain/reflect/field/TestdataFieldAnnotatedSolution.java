package ai.greycos.solver.core.testdomain.reflect.field;

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
public class TestdataFieldAnnotatedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataFieldAnnotatedSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataFieldAnnotatedSolution.class, TestdataFieldAnnotatedEntity.class);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  private List<TestdataValue> valueList;

  @PlanningEntityCollectionProperty private List<TestdataFieldAnnotatedEntity> entityList;

  @PlanningScore private SimpleScore score;

  public TestdataFieldAnnotatedSolution() {}

  public TestdataFieldAnnotatedSolution(String code) {
    super(code);
  }

  public TestdataFieldAnnotatedSolution(
      String code, List<TestdataValue> valueList, List<TestdataFieldAnnotatedEntity> entityList) {
    super(code);
    this.valueList = valueList;
    this.entityList = entityList;
  }

  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public List<TestdataFieldAnnotatedEntity> getEntityList() {
    return entityList;
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
