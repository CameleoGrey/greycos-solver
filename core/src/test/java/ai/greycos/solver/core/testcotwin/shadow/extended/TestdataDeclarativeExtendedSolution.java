package ai.greycos.solver.core.testcotwin.shadow.extended;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataDeclarativeExtendedSolution extends TestdataObject {
  public static SolutionDescriptor<TestdataDeclarativeExtendedSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDeclarativeExtendedSolution.class,
        TestdataDeclarativeExtendedEntity.class,
        TestdataDeclarativeExtendedBaseValue.class,
        TestdataDeclarativeExtendedSubclassValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataDeclarativeExtendedEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataDeclarativeExtendedBaseValue> values;

  @PlanningScore HardSoftScore score;

  public List<TestdataDeclarativeExtendedEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataDeclarativeExtendedEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataDeclarativeExtendedBaseValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataDeclarativeExtendedBaseValue> values) {
    this.values = values;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }
}
