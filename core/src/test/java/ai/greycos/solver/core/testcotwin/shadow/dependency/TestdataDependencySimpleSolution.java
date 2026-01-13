package ai.greycos.solver.core.testcotwin.shadow.dependency;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

/**
 * Exists solely to be used in Constraint Steam tests, which require the score type to be
 * SimpleScore
 */
@PlanningSolution
public class TestdataDependencySimpleSolution {
  public static SolutionDescriptor<TestdataDependencySimpleSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDependencySimpleSolution.class,
        TestdataDependencyEntity.class,
        TestdataDependencyValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataDependencyEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataDependencyValue> values;

  @PlanningScore SimpleScore score;

  public TestdataDependencySimpleSolution() {}

  public TestdataDependencySimpleSolution(
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

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
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
