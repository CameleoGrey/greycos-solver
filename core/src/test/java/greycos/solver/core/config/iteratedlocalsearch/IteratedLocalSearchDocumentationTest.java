package greycos.solver.core.config.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import greycos.solver.core.config.solver.SolverConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Keeps the actual published examples synchronized with configuration and generated XML schema. */
class IteratedLocalSearchDocumentationTest {

  @Test
  void documentationXmlExamplesValidateAgainstGeneratedSchema() throws Exception {
    var schema =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(getClass().getResource("/solver.xsd"));
    var matcher =
        Pattern.compile("(?s)\\[source,xml]\\R----\\R(.*?)\\R----")
            .matcher(Files.readString(documentation()));
    int fragments = 0;
    while (matcher.find()) {
      var fragment = matcher.group(1).strip();
      if (fragment.startsWith("<unionMoveSelector>")) {
        fragment = "<perturbation>" + fragment + "</perturbation>";
      }
      if (fragment.startsWith("<perturbation>")) {
        fragment = "<iteratedLocalSearch>" + fragment + "</iteratedLocalSearch>";
      } else if (fragment.startsWith("<perturbationStrengths>")) {
        fragment = "<iteratedLocalSearch>" + fragment + "</iteratedLocalSearch>";
      }
      var xml = "<solver xmlns=\"" + SolverConfig.XML_NAMESPACE + "\">" + fragment + "</solver>";
      schema.newValidator().validate(new StreamSource(new StringReader(xml)));
      fragments++;
    }
    assertThat(fragments)
        .as("Configuration, fixed strength, two portfolios and islands")
        .isEqualTo(5);
  }

  @Test
  void documentationJavaConfigurationCompiles(@TempDir Path output) throws Exception {
    var matcher =
        Pattern.compile("(?s)\\[source,java]\\R----\\R(.*?)\\R----")
            .matcher(Files.readString(documentation()));
    assertThat(matcher.find()).isTrue();
    var snippet = matcher.group(1);
    var imports =
        snippet
            .lines()
            .filter(line -> line.startsWith("import "))
            .collect(Collectors.joining("\n"));
    var statements =
        snippet
            .lines()
            .filter(line -> !line.startsWith("import "))
            .collect(Collectors.joining("\n"));
    var source = output.resolve("DocumentationConfiguration.java");
    Files.writeString(
        source,
        imports
            + "\nimport greycos.solver.core.config.solver.SolverConfig;\n"
            + "public class DocumentationConfiguration { public static SolverConfig configure(SolverConfig solverConfig) {\n"
            + statements
            + "\nreturn solverConfig; } }\n");
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).isNotNull();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var compiled =
          compiler
              .getTask(
                  null,
                  manager,
                  diagnostics,
                  List.of(
                      "-proc:none",
                      "--release",
                      "21",
                      "-classpath",
                      System.getProperty("java.class.path"),
                      "-d",
                      output.toString()),
                  null,
                  manager.getJavaFileObjects(source))
              .call();
      assertThat(compiled)
          .withFailMessage(
              "Documentation configuration did not compile: %s", diagnostics.getDiagnostics())
          .isTrue();
    }
  }

  private static Path documentation() {
    var relative =
        Path.of("docs/src/modules/ROOT/pages/optimization-algorithms/iterated-local-search.adoc");
    for (var directory = Path.of("").toAbsolutePath();
        directory != null;
        directory = directory.getParent()) {
      var candidate = directory.resolve(relative);
      if (Files.isRegularFile(candidate)) return candidate;
    }
    throw new IllegalStateException(
        "Cannot locate the ILS documentation from the test working directory.");
  }
}
