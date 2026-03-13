package ai.greycos.solver.core.impl.io.jaxb;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.util.ValidationEventCollector;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

public final class GenericJaxbIO<T> {

  private static final int DEFAULT_INDENTATION = 2;

  private static final String ERR_MSG_WRITE =
      "Failed to marshall a root element class (%s) to XML.";
  private static final String ERR_MSG_READ =
      "Failed to unmarshall a root element class (%s) from XML.";
  private static final String ERR_MSG_READ_OVERRIDE_NAMESPACE =
      "Failed to unmarshall a root element class (%s) from XML with overriding elements' namespaces: (%s).";

  private final JAXBContext jaxbContext;
  private final Marshaller marshaller;
  private final Class<T> rootClass;
  private final int indentation;

  public GenericJaxbIO(Class<T> rootClass) {
    this(rootClass, DEFAULT_INDENTATION);
  }

  public GenericJaxbIO(Class<T> rootClass, int indentation) {
    Objects.requireNonNull(rootClass);
    this.rootClass = rootClass;
    this.indentation = indentation;
    try {
      jaxbContext = JAXBContext.newInstance(rootClass);
      marshaller = jaxbContext.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      marshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.toString());
    } catch (JAXBException jaxbException) {
      String errorMessage =
          String.format(
              "Failed to create JAXB Marshaller for a root element class (%s).",
              rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, jaxbException);
    }
  }

  public static DocumentBuilderFactory createDocumentBuilderFactory() {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setXIncludeAware(false);
      factory.setNamespaceAware(true);
      return factory;
    } catch (ParserConfigurationException e) {
      throw new IllegalArgumentException(
          "Failed to create a secure %s instance."
              .formatted(DocumentBuilderFactory.class.getSimpleName()),
          e);
    }
  }

  public static TransformerFactory createTransformerFactory() {
    var factory = TransformerFactory.newInstance();
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    return factory;
  }

  public static SchemaFactory createSchemaFactory(Class<?> rootClass, String schemaResource) {
    var schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    try {
      schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return schemaFactory;
    } catch (SAXNotSupportedException | SAXNotRecognizedException saxException) {
      throw new GreyCOSXmlSerializationException(
          "Failed to configure the %s to validate an XML for a root class (%s) using the (%s) XML Schema."
              .formatted(SchemaFactory.class.getSimpleName(), rootClass.getName(), schemaResource),
          saxException);
    }
  }

  public static Validator createValidator(Schema schema, Class<?> rootClass) {
    try {
      var validator = schema.newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return validator;
    } catch (SAXNotSupportedException | SAXNotRecognizedException saxException) {
      throw new GreyCOSXmlSerializationException(
          "Failed to configure the %s to validate an XML for a root class (%s)."
              .formatted(Validator.class.getSimpleName(), rootClass.getName()),
          saxException);
    }
  }

  public T read(Reader reader) {
    Objects.requireNonNull(reader);
    try {
      return (T) createUnmarshaller().unmarshal(reader);
    } catch (JAXBException jaxbException) {
      String errorMessage = String.format(ERR_MSG_READ, rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, jaxbException);
    }
  }

  public T readAndValidate(Reader reader, String schemaResource) {
    Objects.requireNonNull(reader);
    Schema schema = readSchemaResource(schemaResource);
    return readAndValidate(reader, schema);
  }

  public T readAndValidate(Document document, String schemaResource) {
    return readAndValidate(document, readSchemaResource(schemaResource));
  }

  private Schema readSchemaResource(String schemaResource) {
    Objects.requireNonNull(schemaResource);
    var schemaResourceUrl = GenericJaxbIO.class.getResource(schemaResource);
    if (schemaResourceUrl == null) {
      throw new IllegalArgumentException(
          "The XML schema ("
              + schemaResource
              + ") does not exist.\n"
              + "Maybe build the sources with Maven first?");
    }

    try {
      SchemaFactory schemaFactory = createSchemaFactory(rootClass, schemaResource);
      return schemaFactory.newSchema(schemaResourceUrl);
    } catch (SAXException saxException) {
      String errorMessage =
          String.format(
              "Failed to read an XML Schema resource (%s) to validate an XML for a root class (%s).",
              schemaResource, rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, saxException);
    }
  }

  public T readAndValidate(Reader reader, Schema schema) {
    Document document = parseXml(Objects.requireNonNull(reader));
    return readAndValidate(document, Objects.requireNonNull(schema));
  }

  public T readAndValidate(Document document, Schema schema) {
    Document nonNullDocument = Objects.requireNonNull(document);
    Schema nonNullSchema = Objects.requireNonNull(schema);
    Unmarshaller unmarshaller = createUnmarshaller();
    unmarshaller.setSchema(nonNullSchema);

    ValidationEventCollector validationEventCollector = new ValidationEventCollector();
    try {
      unmarshaller.setEventHandler(validationEventCollector);
    } catch (JAXBException jaxbException) {
      String errorMessage =
          String.format(
              "Failed to set a validation event handler to the %s for "
                  + "a root element class (%s).",
              Unmarshaller.class.getSimpleName(), rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, jaxbException);
    }

    try {
      return (T) unmarshaller.unmarshal(nonNullDocument);
    } catch (JAXBException jaxbException) {
      if (validationEventCollector.hasEvents()) {
        String errorMessage =
            String.format(
                "XML validation failed for a root element class (%s).", rootClass.getName());
        String validationErrors =
            Stream.of(validationEventCollector.getEvents())
                .map(
                    validationEvent ->
                        validationEvent.getMessage()
                            + "\nNode: "
                            + validationEvent.getLocator().getNode().getNodeName())
                .collect(Collectors.joining("\n"));
        String errorMessageWithValidationEvents = errorMessage + "\n" + validationErrors;
        throw new GreyCOSXmlSerializationException(errorMessageWithValidationEvents, jaxbException);
      } else {
        String errorMessage = String.format(ERR_MSG_READ, rootClass.getName());
        throw new GreyCOSXmlSerializationException(errorMessage, jaxbException);
      }
    }
  }

  /**
   * Reads the input XML using the {@link Reader} overriding elements namespaces. If an element
   * already has a namespace and a {@link ElementNamespaceOverride} is defined for this element, its
   * namespace is overridden. In case the element has no namespace, new namespace defined in the
   * {@link ElementNamespaceOverride} is added.
   *
   * @param reader input XML {@link Reader}; never null
   * @param elementNamespaceOverrides never null
   * @return deserialized object representation of the XML.
   */
  public T readOverridingNamespace(
      Reader reader, ElementNamespaceOverride... elementNamespaceOverrides) {
    Objects.requireNonNull(reader);
    Objects.requireNonNull(elementNamespaceOverrides);
    return readOverridingNamespace(parseXml(reader), elementNamespaceOverrides);
  }

  /**
   * Reads the input XML {@link Document} overriding namespaces. If an element already has a
   * namespace and a {@link ElementNamespaceOverride} is defined for this element, its namespace is
   * overridden. In case the element has no namespace a new namespace defined in the {@link
   * ElementNamespaceOverride} is added.
   *
   * @param document input XML {@link Document}; never null
   * @param elementNamespaceOverrides never null
   * @return deserialized object representation of the XML.
   */
  public T readOverridingNamespace(
      Document document, ElementNamespaceOverride... elementNamespaceOverrides) {
    Document translatedDocument =
        overrideNamespaces(
            Objects.requireNonNull(document), Objects.requireNonNull(elementNamespaceOverrides));
    try {
      return (T) createUnmarshaller().unmarshal(translatedDocument);
    } catch (JAXBException e) {
      final String errorMessage =
          String.format(
              ERR_MSG_READ_OVERRIDE_NAMESPACE,
              rootClass.getName(),
              Arrays.toString(elementNamespaceOverrides));
      throw new GreyCOSXmlSerializationException(errorMessage, e);
    }
  }

  public Document parseXml(Reader reader) {
    try (Reader nonNullReader = Objects.requireNonNull(reader)) {
      DocumentBuilder builder = createDocumentBuilderFactory().newDocumentBuilder();
      return builder.parse(new InputSource(nonNullReader));
    } catch (ParserConfigurationException e) {
      String errorMessage =
          String.format(
              "Failed to create a %s instance to parse an XML for a root class (%s).",
              DocumentBuilder.class.getSimpleName(), rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, e);
    } catch (SAXException saxException) {
      String errorMessage =
          String.format("Failed to parse an XML for a root class (%s).", rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, saxException);
    } catch (IOException ioException) {
      String errorMessage =
          String.format("Failed to read an XML for a root class (%s).", rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, ioException);
    }
  }

  private Unmarshaller createUnmarshaller() {
    try {
      return jaxbContext.createUnmarshaller();
    } catch (JAXBException e) {
      String errorMessage =
          String.format(
              "Failed to create a JAXB %s for a root element class (%s).",
              Unmarshaller.class.getSimpleName(), rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, e);
    }
  }

  public void validate(Document document, String schemaResource) {
    Schema schema = readSchemaResource(Objects.requireNonNull(schemaResource));
    validate(Objects.requireNonNull(document), schema);
  }

  public void validate(Document document, Schema schema) {
    Validator validator = createValidator(Objects.requireNonNull(schema), rootClass);
    try {
      validator.validate(new DOMSource(Objects.requireNonNull(document)));
    } catch (SAXException saxException) {
      String errorMessage =
          String.format("XML validation failed for a root element class (%s).", rootClass.getName())
              + "\n"
              + saxException.getMessage();
      throw new GreyCOSXmlSerializationException(errorMessage, saxException);
    } catch (IOException ioException) {
      String errorMessage =
          String.format(
              "Failed to read an XML for a root element class (%s) during validation.",
              rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, ioException);
    }
  }

  public void write(T root, Writer writer) {
    write(root, writer, null);
  }

  private void write(T root, Writer writer, StreamSource xslt) {
    DOMResult domResult = marshall(Objects.requireNonNull(root));
    Writer nonNullWriter = Objects.requireNonNull(writer);
    formatXml(domResult, xslt, nonNullWriter);
  }

  public void writeWithoutNamespaces(T root, Writer writer) {
    try (InputStream xsltInputStream = getClass().getResourceAsStream("removeNamespaces.xslt")) {
      if (xsltInputStream == null) {
        throw new IllegalStateException(
            "Impossible state: Failed to load XSLT stylesheet to remove namespaces.");
      }
      write(root, writer, new StreamSource(xsltInputStream));
    } catch (Exception e) {
      throw new GreyCOSXmlSerializationException(
          String.format(ERR_MSG_WRITE, rootClass.getName()), e);
    }
  }

  private DOMResult marshall(T root) {
    Objects.requireNonNull(root);
    DOMResult domResult = new DOMResult();
    try {
      marshaller.marshal(root, domResult);
    } catch (JAXBException jaxbException) {
      throw new GreyCOSXmlSerializationException(
          String.format(ERR_MSG_WRITE, rootClass.getName()), jaxbException);
    }
    return domResult;
  }

  private void formatXml(DOMResult domResult, Source transformationTemplate, Writer writer) {
    try {
      TransformerFactory transformerFactory = createTransformerFactory();
      Transformer transformer =
          transformationTemplate == null
              ? transformerFactory.newTransformer()
              : transformerFactory.newTransformer(transformationTemplate);
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(
          "{http://xml.apache.org/xslt}indent-amount", String.valueOf(indentation));
      transformer.transform(new DOMSource(domResult.getNode()), new StreamResult(writer));
    } catch (TransformerException transformerException) {
      String errorMessage =
          String.format("Failed to format XML for a root element class (%s).", rootClass.getName());
      throw new GreyCOSXmlSerializationException(errorMessage, transformerException);
    }
  }

  private Document overrideNamespaces(
      Document document, ElementNamespaceOverride... elementNamespaceOverrides) {
    Document nonNullDocument = Objects.requireNonNull(document);
    var elementNamespaceOverridesMap = new HashMap<String, String>();
    for (ElementNamespaceOverride namespaceOverride :
        Objects.requireNonNull(elementNamespaceOverrides)) {
      elementNamespaceOverridesMap.put(
          namespaceOverride.elementLocalName(), namespaceOverride.namespaceOverride());
    }

    var preOrderNodes = new LinkedList<NamespaceOverride>();
    preOrderNodes.push(new NamespaceOverride(nonNullDocument.getDocumentElement(), null));
    while (!preOrderNodes.isEmpty()) {
      NamespaceOverride currentNodeOverride = preOrderNodes.pop();
      Node currentNode = currentNodeOverride.node();
      final String elementLocalName =
          currentNode.getLocalName() == null
              ? currentNode.getNodeName()
              : currentNode.getLocalName();

      String detectedNamespaceOverride = elementNamespaceOverridesMap.get(elementLocalName);
      String effectiveNamespaceOverride =
          detectedNamespaceOverride != null
              ? detectedNamespaceOverride
              : currentNodeOverride.namespace();

      if (effectiveNamespaceOverride != null) {
        nonNullDocument.renameNode(currentNode, effectiveNamespaceOverride, elementLocalName);
      }

      processChildNodes(
          currentNode,
          (childNode -> {
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
              preOrderNodes.push(new NamespaceOverride(childNode, effectiveNamespaceOverride));
            }
          }));
    }

    return nonNullDocument;
  }

  private void processChildNodes(Node node, Consumer<Node> nodeConsumer) {
    NodeList childNodes = node.getChildNodes();
    if (childNodes != null) {
      for (int i = 0; i < childNodes.getLength(); i++) {
        Node childNode = childNodes.item(i);
        if (childNode != null) {
          nodeConsumer.accept(childNode);
        }
      }
    }
  }

  private record NamespaceOverride(Node node, String namespace) {}
}
