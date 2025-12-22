package ai.greycos.solver.core.testdomain.list;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.IndexShadowVariable;
import ai.greycos.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.index.IndexShadowVariableDescriptor;
import ai.greycos.solver.core.impl.domain.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;

@PlanningEntity
public class TestdataListValue extends TestdataObject {

  public static EntityDescriptor<TestdataListSolution> buildEntityDescriptor() {
    return TestdataListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListValue.class);
  }

  public static InverseRelationShadowVariableDescriptor<TestdataListSolution>
      buildVariableDescriptorForEntity() {
    return (InverseRelationShadowVariableDescriptor<TestdataListSolution>)
        buildEntityDescriptor().getShadowVariableDescriptor("entity");
  }

  public static IndexShadowVariableDescriptor<TestdataListSolution>
      buildVariableDescriptorForIndex() {
    return (IndexShadowVariableDescriptor<TestdataListSolution>)
        buildEntityDescriptor().getShadowVariableDescriptor("index");
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataListEntity entity;

  @IndexShadowVariable(sourceVariableName = "valueList")
  private Integer index;

  public TestdataListValue() {}

  public TestdataListValue(String code) {
    super(code);
  }

  public TestdataListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataListEntity entity) {
    this.entity = entity;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }
}
