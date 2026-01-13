package ai.greycos.solver.jackson.testcotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

@PlanningEntity
public class JacksonTestdataEntity extends JacksonTestdataObject {

  public static EntityDescriptor buildEntityDescriptor() {
    SolutionDescriptor solutionDescriptor = JacksonTestdataSolution.buildSolutionDescriptor();
    return solutionDescriptor.findEntityDescriptorOrFail(JacksonTestdataEntity.class);
  }

  public static GenuineVariableDescriptor buildVariableDescriptorForValue() {
    SolutionDescriptor solutionDescriptor = JacksonTestdataSolution.buildSolutionDescriptor();
    EntityDescriptor entityDescriptor =
        solutionDescriptor.findEntityDescriptorOrFail(JacksonTestdataEntity.class);
    return entityDescriptor.getGenuineVariableDescriptor("value");
  }

  private JacksonTestdataValue value;

  public JacksonTestdataEntity() {}

  public JacksonTestdataEntity(String code) {
    super(code);
  }

  public JacksonTestdataEntity(String code, JacksonTestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public JacksonTestdataValue getValue() {
    return value;
  }

  public void setValue(JacksonTestdataValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
