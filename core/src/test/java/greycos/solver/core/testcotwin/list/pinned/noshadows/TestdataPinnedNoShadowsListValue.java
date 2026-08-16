package greycos.solver.core.testcotwin.list.pinned.noshadows;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.IndexShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataPinnedNoShadowsListValue extends TestdataObject {

  public static EntityDescriptor<TestdataPinnedNoShadowsListSolution> buildEntityDescriptor() {
    return TestdataPinnedNoShadowsListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataPinnedNoShadowsListValue.class);
  }

  public static InverseRelationShadowVariableDescriptor<TestdataPinnedNoShadowsListSolution>
      buildVariableDescriptorForEntity() {
    return (InverseRelationShadowVariableDescriptor<TestdataPinnedNoShadowsListSolution>)
        buildEntityDescriptor().getShadowVariableDescriptor("entity");
  }

  public static IndexShadowVariableDescriptor<TestdataPinnedNoShadowsListSolution>
      buildVariableDescriptorForIndex() {
    return (IndexShadowVariableDescriptor<TestdataPinnedNoShadowsListSolution>)
        buildEntityDescriptor().getShadowVariableDescriptor("index");
  }

  // Intentionally missing the inverse relation variable.
  @IndexShadowVariable(sourceVariableName = "valueList")
  private Integer index;

  public TestdataPinnedNoShadowsListValue() {}

  public TestdataPinnedNoShadowsListValue(String code) {
    super(code);
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }
}
