package greycos.solver.core.testcotwin.shadow.simple_list;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataDeclarativeSimpleListSolution {
  public static SolutionDescriptor<TestdataDeclarativeSimpleListSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDeclarativeSimpleListSolution.class,
        TestdataDeclarativeSimpleListEntity.class,
        TestdataDeclarativeSimpleListValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataDeclarativeSimpleListEntity> entityList;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataDeclarativeSimpleListValue> valueList;

  @PlanningScore SimpleScore score;

  public TestdataDeclarativeSimpleListSolution() {}

  public TestdataDeclarativeSimpleListSolution(
      List<TestdataDeclarativeSimpleListEntity> entityList,
      List<TestdataDeclarativeSimpleListValue> valueList) {
    this.entityList = entityList;
    this.valueList = valueList;
  }

  public List<TestdataDeclarativeSimpleListEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataDeclarativeSimpleListEntity> entityList) {
    this.entityList = entityList;
  }

  public List<TestdataDeclarativeSimpleListValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataDeclarativeSimpleListValue> valueList) {
    this.valueList = valueList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
