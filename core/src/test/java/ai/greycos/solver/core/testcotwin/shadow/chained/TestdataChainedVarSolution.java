package ai.greycos.solver.core.testcotwin.shadow.chained;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataChainedVarSolution {

  public static SolutionDescriptor<TestdataChainedVarSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataChainedVarSolution.class,
        TestdataChainedVarEntity.class,
        TestdataChainedVarValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataChainedVarEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataChainedVarValue> values;

  @PlanningScore HardSoftScore score;

  public TestdataChainedVarSolution() {}

  public TestdataChainedVarSolution(
      List<TestdataChainedVarEntity> entities, List<TestdataChainedVarValue> values) {
    this.values = values;
    this.entities = entities;
  }

  public List<TestdataChainedVarValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataChainedVarValue> values) {
    this.values = values;
  }

  public List<TestdataChainedVarEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataChainedVarEntity> entities) {
    this.entities = entities;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  @Override
  public String toString() {
    return "TestdataBasicVarSolution{"
        + "entities="
        + entities
        + ", values="
        + values
        + ", score="
        + score
        + '}';
  }
}
