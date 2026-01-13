package ai.greycos.solver.core.testcotwin.pinned.chained;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.chained.TestdataChainedAnchor;

@PlanningSolution
public class TestdataPinnedChainedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataPinnedChainedSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataPinnedChainedSolution.class, TestdataPinnedChainedEntity.class);
  }

  private List<TestdataChainedAnchor> chainedAnchorList;
  private List<TestdataPinnedChainedEntity> chainedEntityList;

  private SimpleScore score;

  public TestdataPinnedChainedSolution() {}

  public TestdataPinnedChainedSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "chainedAnchorRange")
  @ProblemFactCollectionProperty
  public List<TestdataChainedAnchor> getChainedAnchorList() {
    return chainedAnchorList;
  }

  public void setChainedAnchorList(List<TestdataChainedAnchor> chainedAnchorList) {
    this.chainedAnchorList = chainedAnchorList;
  }

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "chainedEntityRange")
  public List<TestdataPinnedChainedEntity> getChainedEntityList() {
    return chainedEntityList;
  }

  public void setChainedEntityList(List<TestdataPinnedChainedEntity> chainedEntityList) {
    this.chainedEntityList = chainedEntityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
