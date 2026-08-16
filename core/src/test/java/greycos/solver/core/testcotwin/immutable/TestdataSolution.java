package greycos.solver.core.testcotwin.immutable;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.immutable.record.TestdataRecordEntity;
import greycos.solver.core.testcotwin.immutable.record.TestdataRecordValue;

@PlanningSolution
public class TestdataSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataSolution.class, TestdataRecordEntity.class);
  }

  private List<TestdataRecordValue> valueList;
  private List<TestdataRecordEntity> entityList;

  private SimpleScore score;

  public TestdataSolution() {}

  public TestdataSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataRecordValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataRecordValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataRecordEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataRecordEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
