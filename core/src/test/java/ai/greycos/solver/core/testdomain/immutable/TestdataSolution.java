package ai.greycos.solver.core.testdomain.immutable;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.immutable.record.TestdataRecordEntity;
import ai.greycos.solver.core.testdomain.immutable.record.TestdataRecordValue;

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
