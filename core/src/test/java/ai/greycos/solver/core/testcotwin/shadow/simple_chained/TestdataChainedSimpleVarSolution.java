package ai.greycos.solver.core.testcotwin.shadow.simple_chained;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataChainedSimpleVarSolution {

  public static SolutionDescriptor<TestdataChainedSimpleVarSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataChainedSimpleVarSolution.class,
        TestdataChainedSimpleVarEntity.class,
        TestdataChainedSimpleVarValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataChainedSimpleVarEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataChainedSimpleVarValue> values;

  @PlanningScore SimpleScore score;

  public TestdataChainedSimpleVarSolution() {}

  public TestdataChainedSimpleVarSolution(
      List<TestdataChainedSimpleVarEntity> entities, List<TestdataChainedSimpleVarValue> values) {
    this.values = values;
    this.entities = entities;
  }

  public List<TestdataChainedSimpleVarValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataChainedSimpleVarValue> values) {
    this.values = values;
  }

  public List<TestdataChainedSimpleVarEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataChainedSimpleVarEntity> entities) {
    this.entities = entities;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  @Override
  public String toString() {
    return "TestdataChainedSimpleVarSolution{"
        + "entities="
        + entities
        + ", values="
        + values
        + ", score="
        + score
        + '}';
  }
}
