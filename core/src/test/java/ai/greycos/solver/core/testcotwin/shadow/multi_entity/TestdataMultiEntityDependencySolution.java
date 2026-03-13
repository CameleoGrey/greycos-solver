package ai.greycos.solver.core.testcotwin.shadow.multi_entity;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataMultiEntityDependencySolution {
  public static SolutionDescriptor<TestdataMultiEntityDependencySolution> buildDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMultiEntityDependencySolution.class,
        TestdataMultiEntityDependencyEntity.class,
        TestdataMultiEntityDependencyValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataMultiEntityDependencyEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataMultiEntityDependencyValue> values;

  @ValueRangeProvider List<TestdataMultiEntityDependencyDelay> delays;

  @PlanningScore HardSoftScore score;

  public TestdataMultiEntityDependencySolution() {}

  public TestdataMultiEntityDependencySolution(
      List<TestdataMultiEntityDependencyEntity> entities,
      List<TestdataMultiEntityDependencyValue> values) {
    this.values = values;
    this.entities = entities;
  }

  public static SolutionDescriptor<TestdataMultiEntityDependencySolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMultiEntityDependencySolution.class,
        TestdataMultiEntityDependencyEntity.class,
        TestdataMultiEntityDependencyValue.class);
  }

  public List<TestdataMultiEntityDependencyValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataMultiEntityDependencyValue> values) {
    this.values = values;
  }

  public List<TestdataMultiEntityDependencyEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataMultiEntityDependencyEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataMultiEntityDependencyDelay> getDelays() {
    return delays;
  }

  public void setDelays(List<TestdataMultiEntityDependencyDelay> delays) {
    this.delays = delays;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  @Override
  public String toString() {
    return "TestdataPredecessorSolution{"
        + "entities="
        + entities
        + ", values="
        + values
        + ", score="
        + score
        + '}';
  }
}
