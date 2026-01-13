package ai.greycos.solver.core.testcotwin.shadow.dependency;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataDependencySolution {
  public static SolutionDescriptor<TestdataDependencySolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDependencySolution.class,
        TestdataDependencyEntity.class,
        TestdataDependencyValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataDependencyEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataDependencyValue> values;

  @PlanningScore HardSoftScore score;

  public TestdataDependencySolution() {}

  public TestdataDependencySolution(
      List<TestdataDependencyEntity> entities, List<TestdataDependencyValue> values) {
    this.values = values;
    this.entities = entities;
  }

  public List<TestdataDependencyValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataDependencyValue> values) {
    this.values = values;
  }

  public List<TestdataDependencyEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataDependencyEntity> entities) {
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
