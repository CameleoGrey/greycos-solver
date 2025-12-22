package ai.greycos.solver.core.testdomain.invalid.badfactcollection;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
