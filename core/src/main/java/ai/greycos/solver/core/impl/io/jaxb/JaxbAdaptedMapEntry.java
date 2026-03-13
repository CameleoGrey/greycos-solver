package ai.greycos.solver.core.impl.io.jaxb;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;

import ai.greycos.solver.core.config.solver.SolverConfig;

// Required to generate the XSD type in the same namespace.
@XmlType(namespace = SolverConfig.XML_NAMESPACE)
public class JaxbAdaptedMapEntry {

  @XmlAttribute private String name;

  @XmlAttribute private String value;

  public JaxbAdaptedMapEntry() {}

  public JaxbAdaptedMapEntry(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public String getName() {
    return name;
  }

  public String getValue() {
    return value;
  }
}
