package greycos.solver.core.testcotwin.list.valuerange;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.list.TestdataListSolution;

@PlanningEntity
public class TestdataListEntityProvidingValue extends TestdataObject {

  public static EntityDescriptor<TestdataListSolution> buildEntityDescriptor() {
    return TestdataListSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListEntityProvidingValue.class);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataListEntityProvidingEntity entity;

  @IndexShadowVariable(sourceVariableName = "valueList")
  private Integer index;

  public TestdataListEntityProvidingValue() {}

  public TestdataListEntityProvidingValue(String code) {
    super(code);
  }

  public TestdataListEntityProvidingEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataListEntityProvidingEntity entity) {
    this.entity = entity;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }
}
