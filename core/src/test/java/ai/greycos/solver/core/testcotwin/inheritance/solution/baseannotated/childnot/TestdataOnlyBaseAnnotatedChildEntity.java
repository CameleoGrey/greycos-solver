package ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot;

import ai.greycos.solver.core.testcotwin.TestdataValue;

public class TestdataOnlyBaseAnnotatedChildEntity extends TestdataOnlyBaseAnnotatedBaseEntity {

  private Object extraObject;

  public TestdataOnlyBaseAnnotatedChildEntity() {}

  public TestdataOnlyBaseAnnotatedChildEntity(String code) {
    super(code);
  }

  public TestdataOnlyBaseAnnotatedChildEntity(String code, TestdataValue value) {
    super(code, value);
  }

  public TestdataOnlyBaseAnnotatedChildEntity(
      String code, TestdataValue value, Object extraObject) {
    super(code, value);
    this.extraObject = extraObject;
  }

  public Object getExtraObject() {
    return extraObject;
  }

  public void setExtraObject(Object extraObject) {
    this.extraObject = extraObject;
  }
}
