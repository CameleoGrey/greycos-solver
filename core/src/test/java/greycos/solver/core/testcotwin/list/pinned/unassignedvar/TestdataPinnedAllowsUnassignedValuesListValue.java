package greycos.solver.core.testcotwin.list.pinned.unassignedvar;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataPinnedAllowsUnassignedValuesListValue extends TestdataObject {

  private TestdataPinnedAllowsUnassignedValuesListEntity entity;
  private Integer index;
  private TestdataPinnedAllowsUnassignedValuesListValue previous;
  private TestdataPinnedAllowsUnassignedValuesListValue next;

  public TestdataPinnedAllowsUnassignedValuesListValue() {}

  public TestdataPinnedAllowsUnassignedValuesListValue(String code) {
    super(code);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  public TestdataPinnedAllowsUnassignedValuesListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataPinnedAllowsUnassignedValuesListEntity entity) {
    this.entity = entity;
  }

  @IndexShadowVariable(sourceVariableName = "valueList")
  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  public TestdataPinnedAllowsUnassignedValuesListValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataPinnedAllowsUnassignedValuesListValue previous) {
    this.previous = previous;
  }

  @NextElementShadowVariable(sourceVariableName = "valueList")
  public TestdataPinnedAllowsUnassignedValuesListValue getNext() {
    return next;
  }

  public void setNext(TestdataPinnedAllowsUnassignedValuesListValue next) {
    this.next = next;
  }
}
