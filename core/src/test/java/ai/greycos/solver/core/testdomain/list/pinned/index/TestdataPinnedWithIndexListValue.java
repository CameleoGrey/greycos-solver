package ai.greycos.solver.core.testdomain.list.pinned.index;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.index.IndexShadowVariableDescriptor;
import ai.greycos.solver.core.impl.domain.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;

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
