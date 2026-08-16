package greycos.solver.core.testcotwin.immutable.enumeration;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataEnumSolution {

  public static SolutionDescriptor<TestdataEnumSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataEnumSolution.class, TestdataEnumEntity.class);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  List<TestdataEnumValue> valueList;

  @PlanningEntityCollectionProperty List<TestdataEnumEntity> entityList;
  @PlanningScore SimpleScore score;

  public List<TestdataEnumValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataEnumValue> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataEnumEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataEnumEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
