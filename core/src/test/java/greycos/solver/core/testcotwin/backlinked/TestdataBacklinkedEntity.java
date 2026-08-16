package greycos.solver.core.testcotwin.backlinked;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataBacklinkedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataBacklinkedSolution> buildEntityDescriptor() {
    return TestdataBacklinkedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataBacklinkedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataBacklinkedSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataBacklinkedSolution solution;
  private TestdataValue value;

  public TestdataBacklinkedEntity() {}

  public TestdataBacklinkedEntity(TestdataBacklinkedSolution solution, String code) {
    this(solution, code, null);
  }

  public TestdataBacklinkedEntity(
      TestdataBacklinkedSolution solution, String code, TestdataValue value) {
    super(code);
    this.solution = solution;
    this.value = value;
  }

  public TestdataBacklinkedSolution getSolution() {
    return solution;
  }

  public void setSolution(TestdataBacklinkedSolution solution) {
    this.solution = solution;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
