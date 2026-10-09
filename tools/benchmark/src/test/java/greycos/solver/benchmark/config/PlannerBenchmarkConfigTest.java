package greycos.solver.benchmark.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import greycos.solver.benchmark.impl.io.jaxb.PlannerBenchmarkConfigIO;
import greycos.solver.benchmark.util.RigidTestdataSolutionFileIO;
import greycos.solver.core.api.cotwin.solution.SolutionFileIO;
import greycos.solver.core.api.solver.alns.AlnsGrouping;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmStepLoggingMode;
import greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchStepLoggingMode;
import greycos.solver.core.impl.io.jaxb.GreyCOSXmlSerializationException;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.jackson.impl.cotwin.solution.JacksonSolutionFileIO;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXParseException;

class PlannerBenchmarkConfigTest {

  private static final String TEST_PLANNER_BENCHMARK_CONFIG_WITH_NAMESPACE =
      "testBenchmarkConfigWithNamespace.xml";
  private static final String TEST_PLANNER_BENCHMARK_CONFIG_WITHOUT_NAMESPACE =
      "testBenchmarkConfigWithoutNamespace.xml";

  @ParameterizedTest
  @ValueSource(
      strings = {
        TEST_PLANNER_BENCHMARK_CONFIG_WITHOUT_NAMESPACE,
        TEST_PLANNER_BENCHMARK_CONFIG_WITH_NAMESPACE
      })
  void xmlConfigFileRemainsSameAfterReadWrite(String xmlBenchmarkConfigResource)
      throws IOException {
    PlannerBenchmarkConfigIO xmlIO = new PlannerBenchmarkConfigIO();
    PlannerBenchmarkConfig jaxbBenchmarkConfig;

    try (Reader reader =
        new InputStreamReader(
            PlannerBenchmarkConfigTest.class.getResourceAsStream(xmlBenchmarkConfigResource))) {
      jaxbBenchmarkConfig = xmlIO.read(reader);
    }

    assertThat(jaxbBenchmarkConfig).isNotNull();

    Writer stringWriter = new StringWriter();
    xmlIO.write(jaxbBenchmarkConfig, stringWriter);
    String jaxbString = stringWriter.toString();

    String originalXml =
        IOUtils.toString(
            PlannerBenchmarkConfigTest.class.getResourceAsStream(xmlBenchmarkConfigResource),
            StandardCharsets.UTF_8);

    // During writing the benchmark config, the benchmark element's namespace is removed.
    String benchmarkElementWithNamespace =
        PlannerBenchmarkConfig.XML_ELEMENT_NAME
            + " xmlns=\""
            + PlannerBenchmarkConfig.XML_NAMESPACE
            + "\"";
    if (originalXml.contains(benchmarkElementWithNamespace)) {
      originalXml =
          originalXml.replace(
              benchmarkElementWithNamespace, PlannerBenchmarkConfig.XML_ELEMENT_NAME);
    }
    assertThat(jaxbString).isXmlEqualTo(originalXml);
  }

  @Test
  void namespacedAlnsOperatorsValidateAndRoundTrip() {
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <solverBenchmark>
            <name>ALNS extensions</name>
            <solver>
              <alns>
                <destroyOperator>
                  <type>GROUP_REMOVAL</type><groupingClass>%s</groupingClass>
                  <customProperties><property name="setting" value="example"/></customProperties>
                </destroyOperator>
                <repairOperator><type>CHEAPEST_INSERTION</type></repairOperator>
                <repairOperator><type>REGRET_K</type><regretK>5</regretK></repairOperator>
              </alns>
            </solver>
          </solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(PlannerBenchmarkConfig.XML_NAMESPACE, AlnsGrouping.class.getName());
    var io = new PlannerBenchmarkConfigIO();
    var config = io.read(new StringReader(xml));
    var phase =
        (AlnsPhaseConfig)
            config
                .getSolverBenchmarkConfigList()
                .getFirst()
                .getSolverConfig()
                .getPhaseConfigList()
                .getFirst();
    var destroy = phase.getDestroyOperatorConfigList().getFirst();
    assertThat(destroy.getType()).isEqualTo(AlnsDestroyOperatorType.GROUP_REMOVAL);
    assertThat(destroy.getGroupingClass()).isEqualTo(AlnsGrouping.class);
    assertThat(destroy.getCustomProperties()).containsEntry("setting", "example");
    assertThat(phase.getRepairOperatorConfigList())
        .extracting(AlnsRepairOperatorConfig::getType)
        .containsExactly(
            AlnsRepairOperatorType.CHEAPEST_INSERTION, AlnsRepairOperatorType.REGRET_K);
    assertThat(phase.getRepairOperatorConfigList().getLast().getRegretK()).isEqualTo(5);
    var writer = new StringWriter();
    io.write(config, writer);
    assertThat(io.read(new StringReader(writer.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void nearbyFlagsValidateRoundTripAndInherit(Boolean enabled) {
    var nearbyElement =
        enabled == null
            ? ""
            : "<nearbySelectionAutoConfigurationEnabled>"
                + enabled
                + "</nearbySelectionAutoConfigurationEnabled>";
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <inheritedSolverBenchmark>
            <solver>
              <constructionHeuristic>%s</constructionHeuristic>
              <localSearch>
                <ruinRecreateMoveSelector>%s</ruinRecreateMoveSelector>
              </localSearch>
              <localSearch>
                <listRuinRecreateMoveSelector>%s</listRuinRecreateMoveSelector>
              </localSearch>
            </solver>
          </inheritedSolverBenchmark>
          <solverBenchmark><name>Inherited nearby settings</name></solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(
                PlannerBenchmarkConfig.XML_NAMESPACE, nearbyElement, nearbyElement, nearbyElement);
    var io = new PlannerBenchmarkConfigIO();
    var config = io.read(new StringReader(xml));
    var inherited = config.getInheritedSolverBenchmarkConfig();
    var effective =
        config.getSolverBenchmarkConfigList().getFirst().copyConfig().inherit(inherited);
    for (var benchmark : List.of(inherited, effective)) {
      var phases = benchmark.getSolverConfig().getPhaseConfigList();
      assertThat(
              ((ConstructionHeuristicPhaseConfig) phases.getFirst())
                  .getNearbySelectionAutoConfigurationEnabled())
          .isEqualTo(enabled);
      assertThat(
              ((RuinRecreateMoveSelectorConfig)
                      ((LocalSearchPhaseConfig) phases.get(1)).getMoveSelectorConfig())
                  .getNearbySelectionAutoConfigurationEnabled())
          .isEqualTo(enabled);
      assertThat(
              ((ListRuinRecreateMoveSelectorConfig)
                      ((LocalSearchPhaseConfig) phases.get(2)).getMoveSelectorConfig())
                  .getNearbySelectionAutoConfigurationEnabled())
          .isEqualTo(enabled);
    }
    var writer = new StringWriter();
    io.write(config, writer);
    if (enabled == null) {
      assertThat(writer.toString()).doesNotContain("nearbySelectionAutoConfigurationEnabled");
    }
    // Restore the namespace omitted by write() so the round trip validates the benchmark schema.
    var roundTripXml =
        writer
            .toString()
            .replace(
                "<plannerBenchmark>",
                "<plannerBenchmark xmlns=\"" + PlannerBenchmarkConfig.XML_NAMESPACE + "\">");
    assertThat(io.read(new StringReader(roundTripXml)))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(LocalSearchStepLoggingMode.class)
  void stepLoggingModeValidatesRoundTripsAndInherits(LocalSearchStepLoggingMode mode) {
    var modeElement = mode == null ? "" : "<stepLoggingMode>" + mode + "</stepLoggingMode>";
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <inheritedSolverBenchmark>
            <solver><localSearch>%s</localSearch></solver>
          </inheritedSolverBenchmark>
          <solverBenchmark><name>Inherited step logging</name></solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(PlannerBenchmarkConfig.XML_NAMESPACE, modeElement);
    var io = new PlannerBenchmarkConfigIO();
    var config = io.read(new StringReader(xml));
    var inherited = config.getInheritedSolverBenchmarkConfig();
    var effective =
        config.getSolverBenchmarkConfigList().getFirst().copyConfig().inherit(inherited);
    var phase =
        (LocalSearchPhaseConfig) effective.getSolverConfig().getPhaseConfigList().getFirst();
    assertThat(phase.getStepLoggingMode()).isEqualTo(mode);
    var writer = new StringWriter();
    io.write(config, writer);
    if (mode == null) {
      assertThat(writer.toString()).doesNotContain("stepLoggingMode");
    } else {
      assertThat(writer.toString()).contains(modeElement);
    }
    // Restore the namespace so the round trip validates the generated benchmark schema too.
    var roundTripXml =
        writer
            .toString()
            .replace(
                "<plannerBenchmark>",
                "<plannerBenchmark xmlns=\"" + PlannerBenchmarkConfig.XML_NAMESPACE + "\">");
    assertThat(io.read(new StringReader(roundTripXml)))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void geneticAlgorithmStepLoggingModeRejectsUnknownValue() {
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <solverBenchmark>
            <solver><geneticAlgorithm><stepLoggingMode>IMPROVED</stepLoggingMode></geneticAlgorithm></solver>
          </solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(PlannerBenchmarkConfig.XML_NAMESPACE);

    assertThatExceptionOfType(GreyCOSXmlSerializationException.class)
        .isThrownBy(() -> new PlannerBenchmarkConfigIO().read(new StringReader(xml)))
        .withRootCauseExactlyInstanceOf(SAXParseException.class);
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(GeneticAlgorithmStepLoggingMode.class)
  void geneticAlgorithmStepLoggingModeValidatesRoundTripsAndInherits(
      GeneticAlgorithmStepLoggingMode mode) {
    var modeElement = mode == null ? "" : "<stepLoggingMode>" + mode + "</stepLoggingMode>";
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <inheritedSolverBenchmark>
            <solver><geneticAlgorithm>%s</geneticAlgorithm></solver>
          </inheritedSolverBenchmark>
          <solverBenchmark><name>Inherited GA step logging</name></solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(PlannerBenchmarkConfig.XML_NAMESPACE, modeElement);
    var io = new PlannerBenchmarkConfigIO();
    var config = io.read(new StringReader(xml));
    var inherited = config.getInheritedSolverBenchmarkConfig();
    var effective =
        config.getSolverBenchmarkConfigList().getFirst().copyConfig().inherit(inherited);
    var phase =
        (GeneticAlgorithmPhaseConfig) effective.getSolverConfig().getPhaseConfigList().getFirst();
    assertThat(phase.getStepLoggingMode()).isEqualTo(mode);
    assertThat(phase.resolve().getStepLoggingMode())
        .isEqualTo(mode == null ? GeneticAlgorithmStepLoggingMode.ALL : mode);
    var override =
        new GeneticAlgorithmPhaseConfig()
            .withStepLoggingMode(GeneticAlgorithmStepLoggingMode.ALL)
            .inherit(phase);
    assertThat(override.copyConfig().getStepLoggingMode())
        .isEqualTo(GeneticAlgorithmStepLoggingMode.ALL);
    assertThat(phase.getStepLoggingMode()).isEqualTo(mode);
    var writer = new StringWriter();
    io.write(config, writer);
    if (mode == null) {
      assertThat(writer.toString()).doesNotContain("stepLoggingMode");
    } else {
      assertThat(writer.toString()).contains(modeElement);
    }
    // Restore the namespace so the round trip validates the generated benchmark schema too.
    var roundTripXml =
        writer
            .toString()
            .replace(
                "<plannerBenchmark>",
                "<plannerBenchmark xmlns=\"" + PlannerBenchmarkConfig.XML_NAMESPACE + "\">");
    assertThat(io.read(new StringReader(roundTripXml)))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void readAndValidateInvalidBenchmarkConfig_failsIndicatingTheIssue() {
    PlannerBenchmarkConfigIO xmlIO = new PlannerBenchmarkConfigIO();
    String benchmarkConfigXml =
        "<plannerBenchmark xmlns=\"https://github.com/CameleoGrey/greycos-solver/xsd/benchmark\">\n"
            + "  <benchmarkDirectory>data</benchmarkDirectory>\n"
            + "  <parallelBenchmarkCount>AUTO</parallelBenchmarkCount>\n"
            + "  <solverBenchmark>\n"
            + "    <name>Entity Tabu Search</name>\n"
            + "    <solver>\n"
            // Intentionally wrong to simulate a typo.
            + "      <solutionKlazz>greycos.solver.core.testcotwin.TestdataSolution</solutionKlazz>\n"
            + "      <entityClass>greycos.solver.core.testcotwin.TestdataEntity</entityClass>\n"
            + "    </solver>\n"
            + "    <problemBenchmarks>\n"
            + "      <solutionFileIOClass>"
            + TestdataSolutionFileIO.class.getCanonicalName()
            + "</solutionFileIOClass>\n"
            + "      <inputSolutionFile>nonExistingDataset1.xml</inputSolutionFile>\n"
            + "    </problemBenchmarks>\n"
            + "  </solverBenchmark>\n"
            + "</plannerBenchmark>\n";

    StringReader stringReader = new StringReader(benchmarkConfigXml);
    assertThatExceptionOfType(GreyCOSXmlSerializationException.class)
        .isThrownBy(() -> xmlIO.read(stringReader))
        .withRootCauseExactlyInstanceOf(SAXParseException.class)
        .satisfies(
            exception -> assertThat(exception.getCause()).hasMessageContaining("solutionKlazz"));
  }

  @Test
  void nonExistentConfiguredClassesFailFastWhenResolved() {
    var benchmarkConfig =
        PlannerBenchmarkConfig.createFromXmlReader(
            new StringReader(
                """
                <plannerBenchmark>
                  <threadFactoryClass>missing.ThreadFactory</threadFactoryClass>
                  <benchmarkReport>
                    <solverRankingComparatorClass>missing.Comparator</solverRankingComparatorClass>
                    <solverRankingWeightFactoryClass>missing.WeightFactory</solverRankingWeightFactoryClass>
                  </benchmarkReport>
                  <solverBenchmark>
                    <problemBenchmarks>
                      <solutionFileIOClass>missing.SolutionFileIO</solutionFileIOClass>
                    </problemBenchmarks>
                  </solverBenchmark>
                </plannerBenchmark>
                """));

    assertThatIllegalArgumentException()
        .isThrownBy(benchmarkConfig::getThreadFactoryClass)
        .withMessageContaining("threadFactoryClass")
        .withMessageContaining("missing.ThreadFactory");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> benchmarkConfig.getBenchmarkReportConfig().getSolverRankingComparatorClass())
        .withMessageContaining("solverRankingComparatorClass")
        .withMessageContaining("missing.Comparator");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> benchmarkConfig.getBenchmarkReportConfig().getSolverRankingWeightFactoryClass())
        .withMessageContaining("solverRankingWeightFactoryClass")
        .withMessageContaining("missing.WeightFactory");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                benchmarkConfig
                    .getSolverBenchmarkConfigList()
                    .getFirst()
                    .getProblemBenchmarksConfig()
                    .getSolutionFileIOClass())
        .withMessageContaining("solutionFileIOClass")
        .withMessageContaining("missing.SolutionFileIO");
  }

  @Test
  public void assignCustomSolutionIO() {
    ProblemBenchmarksConfig pbc = new ProblemBenchmarksConfig();
    pbc.setSolutionFileIOClass(RigidTestdataSolutionFileIO.class);

    Class<? extends SolutionFileIO<?>> configured = pbc.getSolutionFileIOClass();
    assertThat(configured).isNotNull();
  }

  private static class TestdataSolutionFileIO extends JacksonSolutionFileIO<TestdataSolution> {
    private TestdataSolutionFileIO() {
      super(TestdataSolution.class);
    }
  }
}
