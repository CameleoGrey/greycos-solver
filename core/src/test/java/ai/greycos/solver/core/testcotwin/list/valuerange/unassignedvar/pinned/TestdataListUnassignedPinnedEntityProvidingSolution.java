package ai.greycos.solver.core.testcotwin.list.valuerange.unassignedvar.pinned;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataListUnassignedPinnedEntityProvidingSolution {

  public static SolutionDescriptor<TestdataListUnassignedPinnedEntityProvidingSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataListUnassignedPinnedEntityProvidingSolution.class,
        TestdataListUnassignedPinnedEntityProvidingEntity.class);
  }

  private List<TestdataListUnassignedPinnedEntityProvidingEntity> entityList;

  private SimpleScore score;

  @PlanningEntityCollectionProperty
  public List<TestdataListUnassignedPinnedEntityProvidingEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataListUnassignedPinnedEntityProvidingEntity> entityList) {
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
