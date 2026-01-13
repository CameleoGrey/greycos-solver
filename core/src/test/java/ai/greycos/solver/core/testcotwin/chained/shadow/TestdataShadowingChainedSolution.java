package ai.greycos.solver.core.testcotwin.chained.shadow;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataShadowingChainedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataShadowingChainedSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataShadowingChainedSolution.class,
        TestdataShadowingChainedObject.class,
        TestdataShadowingChainedEntity.class);
  }

  private List<TestdataShadowingChainedAnchor> chainedAnchorList;
  private List<TestdataShadowingChainedEntity> chainedEntityList;

  private SimpleScore score;

  public TestdataShadowingChainedSolution() {}

  public TestdataShadowingChainedSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "chainedAnchorRange")
  @ProblemFactCollectionProperty
  public List<TestdataShadowingChainedAnchor> getChainedAnchorList() {
    return chainedAnchorList;
  }

  public void setChainedAnchorList(List<TestdataShadowingChainedAnchor> chainedAnchorList) {
    this.chainedAnchorList = chainedAnchorList;
  }

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "chainedEntityRange")
  public List<TestdataShadowingChainedEntity> getChainedEntityList() {
    return chainedEntityList;
  }

  public void setChainedEntityList(List<TestdataShadowingChainedEntity> chainedEntityList) {
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
