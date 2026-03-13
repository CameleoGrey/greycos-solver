package ai.greycos.solver.core.testcotwin.invalid.badfactcollection;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataBadFactCollectionSolution {

  public static SolutionDescriptor<TestdataBadFactCollectionSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(TestdataBadFactCollectionSolution.class);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  TestdataValue valueList;

  @PlanningEntityCollectionProperty private List<TestdataEntity> entityList;

  @PlanningScore private SimpleScore score;

  public TestdataValue getValueList() {
    return valueList;
  }

  public void setValueList(TestdataValue valueList) {
    this.valueList = valueList;
  }

  public List<TestdataEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
