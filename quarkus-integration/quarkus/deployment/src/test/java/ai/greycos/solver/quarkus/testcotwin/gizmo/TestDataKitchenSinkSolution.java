package ai.greycos.solver.quarkus.testcotwin.gizmo;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactProperty;
import ai.greycos.solver.core.api.score.HardSoftScore;

@PlanningSolution
public class TestDataKitchenSinkSolution {

  @PlanningEntityProperty private TestDataKitchenSinkEntity planningEntityProperty;

  @PlanningEntityCollectionProperty
  private List<TestDataKitchenSinkEntity> planningEntityListProperty;

  @ProblemFactProperty private String problemFactProperty;

  @ProblemFactCollectionProperty private List<String> problemFactListProperty;

  @PlanningScore private HardSoftScore score;

  public TestDataKitchenSinkSolution() {}

  public TestDataKitchenSinkSolution(
      TestDataKitchenSinkEntity planningEntityProperty,
      List<TestDataKitchenSinkEntity> planningEntityListProperty,
      String problemFactProperty,
      List<String> problemFactListProperty,
      HardSoftScore score) {
    this.planningEntityProperty = planningEntityProperty;
    this.planningEntityListProperty = planningEntityListProperty;
    this.problemFactProperty = problemFactProperty;
    this.problemFactListProperty = problemFactListProperty;
    this.score = score;
  }

  public TestDataKitchenSinkEntity getPlanningEntityProperty() {
    return planningEntityProperty;
  }
}
