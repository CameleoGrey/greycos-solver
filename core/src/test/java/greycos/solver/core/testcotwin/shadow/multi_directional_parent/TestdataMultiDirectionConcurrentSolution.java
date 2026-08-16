package greycos.solver.core.testcotwin.shadow.multi_directional_parent;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataMultiDirectionConcurrentSolution {
  public static SolutionDescriptor<TestdataMultiDirectionConcurrentSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMultiDirectionConcurrentSolution.class,
        TestdataMultiDirectionConcurrentEntity.class,
        TestdataMultiDirectionConcurrentValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataMultiDirectionConcurrentEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider
  List<TestdataMultiDirectionConcurrentValue> values;

  @PlanningScore HardSoftScore score;

  public List<TestdataMultiDirectionConcurrentEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataMultiDirectionConcurrentEntity> entities) {
    this.entities = entities;
  }

  public List<TestdataMultiDirectionConcurrentValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataMultiDirectionConcurrentValue> values) {
    this.values = values;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }
}
