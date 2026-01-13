package ai.greycos.solver.quarkus.testcotwin.chained;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;

@PlanningSolution
public class TestdataChainedQuarkusSolution {

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "chainedAnchorRange")
  private List<TestdataChainedQuarkusAnchor> chainedAnchorList;

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "chainedEntityRange")
  private List<TestdataChainedQuarkusEntity> chainedEntityList;

  @PlanningScore private SimpleScore score;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public List<TestdataChainedQuarkusAnchor> getChainedAnchorList() {
    return chainedAnchorList;
  }

  public void setChainedAnchorList(List<TestdataChainedQuarkusAnchor> chainedAnchorList) {
    this.chainedAnchorList = chainedAnchorList;
  }

  public List<TestdataChainedQuarkusEntity> getChainedEntityList() {
    return chainedEntityList;
  }

  public void setChainedEntityList(List<TestdataChainedQuarkusEntity> chainedEntityList) {
    this.chainedEntityList = chainedEntityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
