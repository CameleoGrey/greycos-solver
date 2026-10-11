package greycos.solver.greybay.impl.config;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.RuntimeReconstructionProvider;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Owns configuration bytes instead of relying on inheritance's intentionally shared properties. */
public final class FrozenSolverConfig {
  private final String xml;
  private final String identity;
  private final Set<String> requiredRuntimeFields;
  private final RuntimeReconstructionProvider reconstructionProvider;

  private FrozenSolverConfig(
      String xml,
      Set<String> requiredRuntimeFields,
      RuntimeReconstructionProvider reconstructionProvider) {
    this.xml = xml;
    this.requiredRuntimeFields = Set.copyOf(requiredRuntimeFields);
    this.reconstructionProvider = reconstructionProvider;
    this.identity =
        canonicalXmlIdentity(
            xml, reconstructionProvider == null ? null : reconstructionProvider.identity());
  }

  public static FrozenSolverConfig capture(
      SolverConfig config, RuntimeReconstructionProvider reconstructionProvider) {
    Objects.requireNonNull(config, "solverConfig");
    var runtimeFields = runtimeFields(config);
    if (!runtimeFields.isEmpty() && reconstructionProvider == null) {
      throw new IllegalArgumentException(
          "The solverConfig contains runtime-only fields ("
              + runtimeFields
              + "). Supply an identified RuntimeReconstructionProvider.");
    }
    var frozen = new FrozenSolverConfig(write(config), runtimeFields, reconstructionProvider);
    frozen.freshConfig(); // Fail before any study or proposal can commit unusable configuration.
    return frozen;
  }

  public SolverConfig freshConfig() {
    var result = SolverConfig.createFromXmlReader(new StringReader(xml));
    if (reconstructionProvider != null) {
      Long originalSeed = result.getRandomSeed();
      reconstructionProvider.reconstruct(result);
      String actualIdentity = canonicalIdentity(result, reconstructionProvider.identity());
      if (!identity.equals(actualIdentity)
          || !Objects.equals(originalSeed, result.getRandomSeed())) {
        throw new IllegalArgumentException(
            "The runtime reconstruction provider ("
                + reconstructionProvider.identity()
                + ") changed XML-visible solver configuration.");
      }
    }
    var actualRuntimeFields = runtimeFields(result);
    if (!actualRuntimeFields.containsAll(requiredRuntimeFields)) {
      throw new IllegalArgumentException(
          "The reconstructed runtime fields ("
              + actualRuntimeFields
              + ") do not include the required fields ("
              + requiredRuntimeFields
              + ").");
    }
    return result;
  }

  public String xml() {
    return xml;
  }

  public String identity() {
    return identity;
  }

  /** The seed is evaluation identity; lists remain ordered, custom-property maps do not. */
  public static String canonicalIdentity(SolverConfig config, ComponentIdentity providerIdentity) {
    return canonicalXmlIdentity(write(config), providerIdentity);
  }

  public static String write(SolverConfig config) {
    var writer = new StringWriter();
    config.writeXml(writer);
    return writer.toString();
  }

  private static String canonicalXmlIdentity(String xml, ComponentIdentity providerIdentity) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setExpandEntityReferences(false);
      var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      var canonical = new StringBuilder("greybay-config-v1:");
      appendElement(document.getDocumentElement(), canonical, true);
      if (providerIdentity != null) {
        appendValue(canonical, providerIdentity.id());
        appendValue(canonical, providerIdentity.version());
        appendValue(canonical, providerIdentity.artifactIdentity());
      }
      return digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot canonicalize the solver configuration XML.", e);
    }
  }

  public static String digest(byte[] value) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform.", e);
    }
  }

  private static void appendElement(Element element, StringBuilder target, boolean root) {
    target.append('E');
    appendValue(target, element.getTagName());
    var attributes = new ArrayList<String>();
    for (int i = 0; i < element.getAttributes().getLength(); i++) {
      var attribute = element.getAttributes().item(i);
      if (!attribute.getNodeName().startsWith("xmlns")) attributes.add(attribute.getNodeName());
    }
    attributes.sort(String::compareTo);
    for (String name : attributes) {
      target.append('A');
      appendValue(target, name);
      appendValue(target, element.getAttribute(name));
    }
    var children = new ArrayList<Element>();
    var content = new StringBuilder();
    boolean hadElementChildren = false;
    for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element child) {
        hadElementChildren = true;
        if (!(root && child.getTagName().equals("randomSeed"))) children.add(child);
      } else if (node.getNodeType() == Node.TEXT_NODE
          || node.getNodeType() == Node.CDATA_SECTION_NODE) {
        content.append(node.getNodeValue());
      }
    }
    if (children.isEmpty()) {
      target.append('T');
      appendValue(target, hadElementChildren ? "" : content.toString());
    } else {
      // JAXB's map entries are unordered; phase/operator/selector lists retain their order.
      if (children.stream()
          .allMatch(child -> child.hasAttribute("name") && child.hasAttribute("value"))) {
        children.sort(Comparator.comparing(child -> child.getAttribute("name")));
      }
      var previews =
          children.stream()
              .filter(child -> child.getTagName().equals("enablePreviewFeature"))
              .sorted(Comparator.comparing(Element::getTextContent))
              .toList();
      int previewIndex = 0;
      for (var child : children) {
        appendElement(
            child.getTagName().equals("enablePreviewFeature")
                ? previews.get(previewIndex++)
                : child,
            target,
            false);
      }
    }
    target.append(';');
  }

  private static void appendValue(StringBuilder target, String value) {
    target.append(value.length()).append(':').append(value);
  }

  @SuppressWarnings("removal")
  private static Set<String> runtimeFields(SolverConfig config) {
    var fields = new TreeSet<String>();
    if (config.getClock() != null) fields.add("clock");
    if (config.getClassLoader() != null) fields.add("classLoader");
    if (config.getGizmoMemberAccessorMap() != null && !config.getGizmoMemberAccessorMap().isEmpty())
      fields.add("gizmoMemberAccessorMap");
    if (config.getGizmoSolutionClonerMap() != null && !config.getGizmoSolutionClonerMap().isEmpty())
      fields.add("gizmoSolutionClonerMap");
    collectRuntimePhases(config.getPhaseConfigList(), "phase", fields);
    return fields;
  }

  private static void collectRuntimePhases(
      List<? extends PhaseConfig> phases, String prefix, Set<String> fields) {
    if (phases == null) return;
    for (int i = 0; i < phases.size(); i++) {
      var phase = phases.get(i);
      String path = prefix + '[' + i + ']';
      if (phase instanceof CustomPhaseConfig custom
          && custom.getCustomPhaseCommandList() != null
          && !custom.getCustomPhaseCommandList().isEmpty())
        fields.add(path + ".customPhaseCommands");
      if (phase instanceof IslandModelPhaseConfig island)
        collectRuntimePhases(island.getPhaseConfigList(), path, fields);
      if (phase instanceof PartitionedSearchPhaseConfig partition)
        collectRuntimePhases(partition.getPhaseConfigList(), path, fields);
    }
  }
}
