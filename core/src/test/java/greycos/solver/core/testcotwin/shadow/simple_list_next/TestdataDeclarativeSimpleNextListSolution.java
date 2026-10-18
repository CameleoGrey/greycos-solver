package greycos.solver.core.testcotwin.shadow.simple_list_next;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataDeclarativeSimpleNextListSolution {

  public static SolutionDescriptor<TestdataDeclarativeSimpleNextListSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDeclarativeSimpleNextListSolution.class,
        TestdataDeclarativeSimpleNextListEntity.class,
        TestdataDeclarativeSimpleNextListValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataDeclarativeSimpleNextListEntity> entityList;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataDeclarativeSimpleNextListValue> valueList;

  @PlanningScore SimpleScore score;

  public TestdataDeclarativeSimpleNextListSolution() {}

  public TestdataDeclarativeSimpleNextListSolution(
      List<TestdataDeclarativeSimpleNextListEntity> entityList,
      List<TestdataDeclarativeSimpleNextListValue> valueList) {
    this.entityList = entityList;
    this.valueList = valueList;
  }

  public List<TestdataDeclarativeSimpleNextListEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataDeclarativeSimpleNextListEntity> entityList) {
    this.entityList = entityList;
  }

  public List<TestdataDeclarativeSimpleNextListValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataDeclarativeSimpleNextListValue> valueList) {
    this.valueList = valueList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
