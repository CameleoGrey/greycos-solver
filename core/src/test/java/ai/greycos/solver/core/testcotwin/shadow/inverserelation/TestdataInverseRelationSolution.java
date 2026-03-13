package ai.greycos.solver.core.testcotwin.shadow.inverserelation;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataInverseRelationSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataInverseRelationSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataInverseRelationSolution.class,
        TestdataInverseRelationEntity.class,
        TestdataInverseRelationValue.class);
  }

  private List<TestdataInverseRelationValue> valueList;
  private List<TestdataInverseRelationEntity> entityList;

  private SimpleScore score;

  public TestdataInverseRelationSolution() {}

  public TestdataInverseRelationSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  public List<TestdataInverseRelationValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataInverseRelationValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataInverseRelationEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataInverseRelationEntity> entityList) {
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
