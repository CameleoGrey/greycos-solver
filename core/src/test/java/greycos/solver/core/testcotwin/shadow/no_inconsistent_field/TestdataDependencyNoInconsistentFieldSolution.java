package greycos.solver.core.testcotwin.shadow.no_inconsistent_field;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataDependencyNoInconsistentFieldSolution {
  public static SolutionDescriptor<TestdataDependencyNoInconsistentFieldSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDependencyNoInconsistentFieldSolution.class,
        TestdataDependencyNoInconsistentFieldEntity.class,
        TestdataDependencyNoInconsistentFieldValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataDependencyNoInconsistentFieldEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataDependencyNoInconsistentFieldValue> values;

  @PlanningScore HardSoftScore score;

  public TestdataDependencyNoInconsistentFieldSolution() {}

  public TestdataDependencyNoInconsistentFieldSolution(
      List<TestdataDependencyNoInconsistentFieldEntity> entities,
      List<TestdataDependencyNoInconsistentFieldValue> values) {
    this.values = values;
    this.entities = entities;
  }

  public List<TestdataDependencyNoInconsistentFieldValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataDependencyNoInconsistentFieldValue> values) {
    this.values = values;
  }

  public List<TestdataDependencyNoInconsistentFieldEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataDependencyNoInconsistentFieldEntity> entities) {
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
    return "TestdataPredecessorSolution{entities=%s, values=%s, score=%s}"
        .formatted(entities, values, score);
  }
}
