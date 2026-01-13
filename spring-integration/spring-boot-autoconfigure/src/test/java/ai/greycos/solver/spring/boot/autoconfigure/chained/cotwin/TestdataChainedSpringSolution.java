package ai.greycos.solver.spring.boot.autoconfigure.chained.cotwin;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;

@PlanningSolution
public class TestdataChainedSpringSolution {

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "chainedAnchorRange")
  private List<TestdataChainedSpringAnchor> chainedAnchorList;

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "chainedEntityRange")
  private List<TestdataChainedSpringEntity> chainedEntityList;

  @PlanningScore private SimpleScore score;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public List<TestdataChainedSpringAnchor> getChainedAnchorList() {
    return chainedAnchorList;
  }

  public void setChainedAnchorList(List<TestdataChainedSpringAnchor> chainedAnchorList) {
    this.chainedAnchorList = chainedAnchorList;
  }

  public List<TestdataChainedSpringEntity> getChainedEntityList() {
    return chainedEntityList;
  }

  public void setChainedEntityList(List<TestdataChainedSpringEntity> chainedEntityList) {
    this.chainedEntityList = chainedEntityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
