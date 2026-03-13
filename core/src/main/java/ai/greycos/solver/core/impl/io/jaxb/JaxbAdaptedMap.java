package ai.greycos.solver.core.impl.io.jaxb;

import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import ai.greycos.solver.core.config.solver.SolverConfig;

// Required to generate the XSD type in the same namespace.
@XmlType(namespace = SolverConfig.XML_NAMESPACE)
public class JaxbAdaptedMap {

  @XmlElement(name = "property", namespace = SolverConfig.XML_NAMESPACE)
  private List<JaxbAdaptedMapEntry> entries;

  private JaxbAdaptedMap() {
    // Required by JAXB
  }

  public JaxbAdaptedMap(List<JaxbAdaptedMapEntry> entries) {
    this.entries = entries;
  }

  public List<JaxbAdaptedMapEntry> getEntries() {
    return entries;
  }
}
