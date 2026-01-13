package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow;

public class TestdataExtendedShadowExtendedShadowEntity extends TestdataExtendedShadowShadowEntity {

  public TestdataExtendedShadowExtendedShadowEntity() {
    super();
  }

  public TestdataExtendedShadowExtendedShadowEntity(TestdataExtendedShadowEntity myPlanningEntity) {
    super();
    this.planningEntityList.add(myPlanningEntity);
  }
}
