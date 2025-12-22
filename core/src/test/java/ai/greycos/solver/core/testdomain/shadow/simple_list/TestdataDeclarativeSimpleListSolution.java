package ai.greycos.solver.core.testdomain.shadow.simple_list;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;

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
