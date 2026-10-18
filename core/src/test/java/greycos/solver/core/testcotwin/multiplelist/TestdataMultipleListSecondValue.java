package greycos.solver.core.testcotwin.multiplelist;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataMultipleListSecondValue extends TestdataObject {

  @InverseRelationShadowVariable(sourceVariableName = "secondValueList")
  private TestdataMultipleListEntity entity;

  @IndexShadowVariable(sourceVariableName = "secondValueList")
  private Integer index;

  @PreviousElementShadowVariable(sourceVariableName = "secondValueList")
  private TestdataMultipleListSecondValue previous;

  @NextElementShadowVariable(sourceVariableName = "secondValueList")
  private TestdataMultipleListSecondValue next;

  @PlanningVariable(valueRangeProviderRefs = "basicValueRange")
  private Integer quantity;

  public TestdataMultipleListSecondValue() {}

  public TestdataMultipleListSecondValue(String code) {
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

  public TestdataMultipleListSecondValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataMultipleListSecondValue previous) {
    this.previous = previous;
  }

  public TestdataMultipleListSecondValue getNext() {
    return next;
  }

  public void setNext(TestdataMultipleListSecondValue next) {
    this.next = next;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }
}
