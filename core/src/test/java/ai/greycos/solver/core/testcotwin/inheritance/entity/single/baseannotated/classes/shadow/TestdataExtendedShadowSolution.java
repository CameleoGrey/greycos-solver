package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow;

import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataExtendedShadowSolution {
  @PlanningEntityCollectionProperty
  public List<TestdataExtendedShadowShadowEntity> shadowEntityList;

  @ValueRangeProvider @ProblemFactCollectionProperty
  public List<TestdataExtendedShadowVariable> planningVariableList;

  // Exists so Quarkus does not return the original solution because there are no planning variables
  @PlanningEntityProperty public TestdataEntity testdataEntity;

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> testdataValueList;

  @PlanningScore public SimpleScore score;

  public TestdataExtendedShadowSolution() {
    // Required for cloning
  }

  public TestdataExtendedShadowSolution(TestdataExtendedShadowShadowEntity shadowShadowEntity) {
    this.testdataEntity = new TestdataEntity("Entity 1");
    this.testdataValueList = List.of(new TestdataValue("Value 1"));
    this.shadowEntityList = Collections.singletonList(shadowShadowEntity);
    this.planningVariableList = List.of(new TestdataExtendedShadowVariable(1));
  }
}
