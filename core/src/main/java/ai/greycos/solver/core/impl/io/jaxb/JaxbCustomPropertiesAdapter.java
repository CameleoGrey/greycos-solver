package ai.greycos.solver.core.impl.io.jaxb;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class JaxbCustomPropertiesAdapter extends XmlAdapter<JaxbAdaptedMap, Map<String, String>> {

  @Override
  public Map<String, String> unmarshal(JaxbAdaptedMap jaxbAdaptedMap) {
    if (jaxbAdaptedMap == null) {
      return null;
    }
    List<JaxbAdaptedMapEntry> entries = jaxbAdaptedMap.getEntries();
    if (entries == null || entries.isEmpty()) {
      return Collections.emptyMap();
    }
    return entries.stream()
        .collect(Collectors.toMap(JaxbAdaptedMapEntry::getName, JaxbAdaptedMapEntry::getValue));
  }

  @Override
  public JaxbAdaptedMap marshal(Map<String, String> originalMap) {
    if (originalMap == null) {
      return null;
    }
    List<JaxbAdaptedMapEntry> entries =
        originalMap.entrySet().stream()
            .map(entry -> new JaxbAdaptedMapEntry(entry.getKey(), entry.getValue()))
            .toList();
    return new JaxbAdaptedMap(entries);
  }
}
