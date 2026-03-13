package ai.greycos.solver.core.testcotwin.list.pinned;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.IndexShadowVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataPinnedListValue extends TestdataObject {

  public static EntityDescriptor<TestdataPinnedListSolution> buildEntityDescriptor() {
    return TestdataPinnedListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataPinnedListValue.class);
  }

  public static InverseRelationShadowVariableDescriptor<TestdataPinnedListSolution>
      buildVariableDescriptorForEntity() {
    return (InverseRelationShadowVariableDescriptor<TestdataPinnedListSolution>)
        buildEntityDescriptor().getShadowVariableDescriptor("entity");
  }

  public static IndexShadowVariableDescriptor<TestdataPinnedListSolution>
      buildVariableDescriptorForIndex() {
    return (IndexShadowVariableDescriptor<TestdataPinnedListSolution>)
        buildEntityDescriptor().getShadowVariableDescriptor("index");
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataPinnedListEntity entity;

  @IndexShadowVariable(sourceVariableName = "valueList")
  private Integer index;

  public TestdataPinnedListValue() {}

  public TestdataPinnedListValue(String code) {
    super(code);
  }

  public TestdataPinnedListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataPinnedListEntity entity) {
    this.entity = entity;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }
}
