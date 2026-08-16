package greycos.solver.core.testcotwin.list.unassignedvar;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataAllowsUnassignedValuesListValue extends TestdataObject {

  public static EntityDescriptor<TestdataAllowsUnassignedValuesListSolution>
      buildEntityDescriptor() {
    return TestdataAllowsUnassignedValuesListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataAllowsUnassignedValuesListValue.class);
  }

  private TestdataAllowsUnassignedValuesListEntity entity;
  private Integer index;
  private TestdataAllowsUnassignedValuesListValue previous;
  private TestdataAllowsUnassignedValuesListValue next;

  public TestdataAllowsUnassignedValuesListValue() {}

  public TestdataAllowsUnassignedValuesListValue(String code) {
    super(code);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  public TestdataAllowsUnassignedValuesListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataAllowsUnassignedValuesListEntity entity) {
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
  public TestdataAllowsUnassignedValuesListValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataAllowsUnassignedValuesListValue previous) {
    this.previous = previous;
  }

  @NextElementShadowVariable(sourceVariableName = "valueList")
  public TestdataAllowsUnassignedValuesListValue getNext() {
    return next;
  }

  public void setNext(TestdataAllowsUnassignedValuesListValue next) {
    this.next = next;
  }
}
