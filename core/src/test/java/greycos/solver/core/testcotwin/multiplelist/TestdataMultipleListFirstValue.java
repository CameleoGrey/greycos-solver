package greycos.solver.core.testcotwin.multiplelist;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataMultipleListFirstValue extends TestdataObject {

  @InverseRelationShadowVariable(sourceVariableName = "firstValueList")
  private TestdataMultipleListEntity entity;

  @IndexShadowVariable(sourceVariableName = "firstValueList")
  private Integer index;

  @PreviousElementShadowVariable(sourceVariableName = "firstValueList")
  private TestdataMultipleListFirstValue previous;

  @NextElementShadowVariable(sourceVariableName = "firstValueList")
  private TestdataMultipleListFirstValue next;

  public TestdataMultipleListFirstValue() {}

  public TestdataMultipleListFirstValue(String code) {
    super(code);
  }

  public TestdataMultipleListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataMultipleListEntity entity) {
    this.entity = entity;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }

  public TestdataMultipleListFirstValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataMultipleListFirstValue previous) {
    this.previous = previous;
  }

  public TestdataMultipleListFirstValue getNext() {
    return next;
  }

  public void setNext(TestdataMultipleListFirstValue next) {
    this.next = next;
  }
}
