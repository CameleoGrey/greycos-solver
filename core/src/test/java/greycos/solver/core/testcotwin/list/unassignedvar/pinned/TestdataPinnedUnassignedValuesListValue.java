package greycos.solver.core.testcotwin.list.unassignedvar.pinned;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataPinnedUnassignedValuesListValue extends TestdataObject {

  public static EntityDescriptor<TestdataPinnedUnassignedValuesListSolution>
      buildEntityDescriptor() {
    return TestdataPinnedUnassignedValuesListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataPinnedUnassignedValuesListValue.class);
  }

  private TestdataPinnedUnassignedValuesListEntity entity;
  private Integer index;
  private TestdataPinnedUnassignedValuesListValue previous;
  private TestdataPinnedUnassignedValuesListValue next;

  public TestdataPinnedUnassignedValuesListValue() {}

  public TestdataPinnedUnassignedValuesListValue(String code) {
    super(code);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  public TestdataPinnedUnassignedValuesListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataPinnedUnassignedValuesListEntity entity) {
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
  public TestdataPinnedUnassignedValuesListValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataPinnedUnassignedValuesListValue previous) {
    this.previous = previous;
  }

  @NextElementShadowVariable(sourceVariableName = "valueList")
  public TestdataPinnedUnassignedValuesListValue getNext() {
    return next;
  }

  public void setNext(TestdataPinnedUnassignedValuesListValue next) {
    this.next = next;
  }
}
