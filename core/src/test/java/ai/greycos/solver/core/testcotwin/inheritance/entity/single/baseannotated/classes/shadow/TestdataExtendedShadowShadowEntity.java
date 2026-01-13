package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public abstract class TestdataExtendedShadowShadowEntity {
  @ShadowVariable(
      variableListenerClass = TestdataExtendedShadowVariableListener.class,
      sourceVariableName = "myPlanningVariable",
      sourceEntityClass = TestdataExtendedShadowEntity.class)
  public List<TestdataExtendedShadowEntity> planningEntityList = new ArrayList<>();

  protected TestdataExtendedShadowShadowEntity() {}

  protected TestdataExtendedShadowShadowEntity(
      List<TestdataExtendedShadowEntity> planningEntityList) {
    this.planningEntityList = planningEntityList;
  }
}
