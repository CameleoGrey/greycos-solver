package ai.greycos.solver.core.testcotwin.shadow.concurrent;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataConcurrentSolution {
  public static SolutionDescriptor<TestdataConcurrentSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataConcurrentSolution.class,
        TestdataConcurrentEntity.class,
        TestdataConcurrentValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataConcurrentEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataConcurrentValue> values;

  @PlanningScore HardSoftScore score;

  public List<TestdataConcurrentEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataConcurrentEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataConcurrentValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataConcurrentValue> values) {
    this.values = values;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }
}
