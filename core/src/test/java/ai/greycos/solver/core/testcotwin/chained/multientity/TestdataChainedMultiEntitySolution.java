package ai.greycos.solver.core.testcotwin.chained.multientity;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;

@PlanningSolution
public class TestdataChainedMultiEntitySolution {

  private List<TestdataChainedBrownEntity> brownEntities;
  private List<TestdataChainedGreenEntity> greenEntities;
  private List<TestdataChainedMultiEntityAnchor> anchors;
  private SimpleScore score;

  public TestdataChainedMultiEntitySolution() {}

  public TestdataChainedMultiEntitySolution(
      List<TestdataChainedBrownEntity> brownEntities,
      List<TestdataChainedGreenEntity> greenEntities,
      List<TestdataChainedMultiEntityAnchor> anchors) {
    this.brownEntities = brownEntities;
    this.greenEntities = greenEntities;
    this.anchors = anchors;
  }

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "brownRange")
  public List<TestdataChainedBrownEntity> getBrownEntities() {
    return brownEntities;
  }

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "greenRange")
  public List<TestdataChainedGreenEntity> getGreenEntities() {
    return greenEntities;
  }

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "anchorRange")
  public List<TestdataChainedMultiEntityAnchor> getAnchors() {
    return anchors;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
