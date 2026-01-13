package ai.greycos.solver.jaxb.testcotwin;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class JaxbTestdataValue extends JaxbTestdataObject {

  public JaxbTestdataValue() {}

  public JaxbTestdataValue(String code) {
    super(code);
  }
}
