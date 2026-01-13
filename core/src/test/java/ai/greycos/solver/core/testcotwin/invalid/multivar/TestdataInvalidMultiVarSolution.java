package ai.greycos.solver.core.testcotwin.invalid.multivar;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.chained.TestdataChainedAnchor;
import ai.greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

@PlanningSolution
public class TestdataInvalidMultiVarSolution {

  public static SolutionDescriptor<TestdataInvalidMultiVarSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataInvalidMultiVarSolution.class, TestdataInvalidMultiVarEntity.class);
  }

  @ValueRangeProvider(id = "valueRange")
  @PlanningEntityCollectionProperty
  private List<TestdataMixedValue> valueList;

  @ValueRangeProvider(id = "chainedAnchorRange")
  @PlanningEntityCollectionProperty
  private List<TestdataChainedAnchor> chainedAnchorList;

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "chainedEntityRange")
  private List<TestdataInvalidMultiVarEntity> entityList;

  @PlanningScore private SimpleScore score;

  public List<TestdataMixedValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataMixedValue> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataChainedAnchor> getChainedAnchorList() {
    return chainedAnchorList;
  }

  public void setChainedAnchorList(List<TestdataChainedAnchor> chainedAnchorList) {
    this.chainedAnchorList = chainedAnchorList;
  }

  public List<TestdataInvalidMultiVarEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataInvalidMultiVarEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
