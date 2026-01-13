package ai.greycos.solver.core.testcotwin.equals.list;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;

@PlanningEntity
public class TestdataEqualsByCodeListValue extends TestdataEqualsByCodeListObject {

  public static EntityDescriptor<TestdataEqualsByCodeListSolution> buildEntityDescriptor() {
    return TestdataEqualsByCodeListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataEqualsByCodeListValue.class);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataEqualsByCodeListEntity entity;

  @IndexShadowVariable(sourceVariableName = "valueList")
  private Integer index;

  public TestdataEqualsByCodeListValue(String code) {
    super(code);
  }

  public TestdataEqualsByCodeListEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataEqualsByCodeListEntity entity) {
    this.entity = entity;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }
}
