package ai.greycos.solver.quarkus.testcotwin.gizmo;

import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class PrivateNoArgsConstructorSolution {
  @PlanningEntityCollectionProperty List<PrivateNoArgsConstructorEntity> planningEntityList;

  @PlanningScore public SimpleScore score;

  public final int someField;

  private PrivateNoArgsConstructorSolution() {
    this.someField = 1;
  }

  public PrivateNoArgsConstructorSolution(List<PrivateNoArgsConstructorEntity> planningEntityList) {
    this.planningEntityList = planningEntityList;
    this.someField = 2;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<String> valueRange() {
    return Arrays.asList("1", "2", "3");
  }
}
