package ai.greycos.solver.core.testdomain.list.valuerange.pinned;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataListPinnedEntityProvidingSolution {

  public static SolutionDescriptor<TestdataListPinnedEntityProvidingSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataListPinnedEntityProvidingSolution.class,
        TestdataListPinnedEntityProvidingEntity.class);
  }

  private List<TestdataListPinnedEntityProvidingEntity> entityList;

  private SimpleScore score;

  @PlanningEntityCollectionProperty
  public List<TestdataListPinnedEntityProvidingEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataListPinnedEntityProvidingEntity> entityList) {
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
