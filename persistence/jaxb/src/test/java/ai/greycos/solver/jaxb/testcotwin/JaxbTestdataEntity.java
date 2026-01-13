package ai.greycos.solver.jaxb.testcotwin;

import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlRootElement;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

@PlanningEntity
@XmlRootElement
public class JaxbTestdataEntity extends JaxbTestdataObject {

  public static EntityDescriptor buildEntityDescriptor() {
    SolutionDescriptor solutionDescriptor = JaxbTestdataSolution.buildSolutionDescriptor();
    return solutionDescriptor.findEntityDescriptorOrFail(JaxbTestdataEntity.class);
  }

  public static GenuineVariableDescriptor buildVariableDescriptorForValue() {
    SolutionDescriptor solutionDescriptor = JaxbTestdataSolution.buildSolutionDescriptor();
    EntityDescriptor entityDescriptor =
        solutionDescriptor.findEntityDescriptorOrFail(JaxbTestdataEntity.class);
    return entityDescriptor.getGenuineVariableDescriptor("value");
  }

  private JaxbTestdataValue value;

  public JaxbTestdataEntity() {}

  public JaxbTestdataEntity(String code) {
    super(code);
  }

  public JaxbTestdataEntity(String code, JaxbTestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  @XmlIDREF
  public JaxbTestdataValue getValue() {
    return value;
  }

  public void setValue(JaxbTestdataValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
