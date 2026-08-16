package greycos.solver.core.testcotwin.list.pinned.index;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.IndexShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataPinnedWithIndexListValue extends TestdataObject {

  public static EntityDescriptor<TestdataPinnedWithIndexListSolution> buildEntityDescriptor() {
    return TestdataPinnedWithIndexListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataPinnedWithIndexListValue.class);
  }

  public static InverseRelationShadowVariableDescriptor<TestdataPinnedWithIndexListSolution>
      buildVariableDescriptorForEntity() {
    return (InverseRelationShadowVariableDescriptor<TestdataPinnedWithIndexListSolution>)
        buildEntityDescriptor().getShadowVariableDescriptor("entity");
  }

  public static IndexShadowVariableDescriptor<TestdataPinnedWithIndexListSolution>
      buildVariableDescriptorForIndex() {
    return (IndexShadowVariableDescriptor<TestdataPinnedWithIndexListSolution>)
        buildEntityDescriptor().getShadowVariableDescriptor("index");
  }

  // Index shadow var intentionally missing, to test that the supply can deal with that.
  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataPinnedWithIndexListEntity entity;

  public TestdataPinnedWithIndexListValue() {}

  public TestdataPinnedWithIndexListValue(String code) {
    super(code);
  }

  public TestdataPinnedWithIndexListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataPinnedWithIndexListEntity entity) {
    this.entity = entity;
  }
}
