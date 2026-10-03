package greycos.solver.benchmark.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.benchmark.impl.io.jaxb.PlannerBenchmarkConfigIO;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.CrossVariableCustomStage;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageVariableConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageVariableKind;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrossVariableMultistageBenchmarkConfigTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void crossVariableSelectorValidatesAndRoundTripsAgainstBenchmarkSchema(boolean inUnion) {
    var selectorXml =
        """
        <crossVariableMultistageMoveSelector>
          <stageProviderClass>%s</stageProviderClass>
          <variable>
            <kind>BASIC</kind>
            <entityClass>%s</entityClass>
            <variableName>basicValue</variableName>
            <valueClass>%s</valueClass>
          </variable>
          <variable>
            <kind>BASIC</kind>
            <entityClass>%s</entityClass>
            <variableName>secondBasicValue</variableName>
            <valueClass>%s</valueClass>
          </variable>
          <variable>
            <kind>LIST</kind>
            <entityClass>%s</entityClass>
            <variableName>valueList</variableName>
            <valueClass>%s</valueClass>
          </variable>
          <candidateCountLimit>11</candidateCountLimit>
          <probeCountLimit>29</probeCountLimit>
        </crossVariableMultistageMoveSelector>
        """
            .formatted(
                CrossProvider.class.getName(),
                TestdataMixedEntity.class.getName(),
                TestdataMixedOtherValue.class.getName(),
                TestdataMixedEntity.class.getName(),
                TestdataMixedOtherValue.class.getName(),
                TestdataMixedEntity.class.getName(),
                TestdataMixedValue.class.getName());
    if (inUnion) selectorXml = "<unionMoveSelector>" + selectorXml + "</unionMoveSelector>";
    var xml =
        """
        <plannerBenchmark xmlns="%s">
          <solverBenchmark>
            <name>Cross variable multistage</name>
            <solver>
              <solutionClass>%s</solutionClass>
              <entityClass>%s</entityClass>
              <entityClass>%s</entityClass>
              <entityClass>%s</entityClass>
              <localSearch>%s</localSearch>
            </solver>
          </solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(
                PlannerBenchmarkConfig.XML_NAMESPACE,
                TestdataMixedSolution.class.getName(),
                TestdataMixedEntity.class.getName(),
                TestdataMixedOtherValue.class.getName(),
                TestdataMixedValue.class.getName(),
                selectorXml);
    var io = new PlannerBenchmarkConfigIO();
    var parsed = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(parsed, writer);
    // write() omits namespaces; restore the benchmark namespace to validate both reads.
    var roundTripXml =
        writer
            .toString()
            .replace(
                "<plannerBenchmark>",
                "<plannerBenchmark xmlns=\"" + PlannerBenchmarkConfig.XML_NAMESPACE + "\">");
    assertThat(roundTripXml)
        .contains("<plannerBenchmark xmlns=\"" + PlannerBenchmarkConfig.XML_NAMESPACE + "\">");
    var restored = io.read(new StringReader(roundTripXml));

    assertThat(restored).usingRecursiveComparison().isEqualTo(parsed);
    var phase =
        (LocalSearchPhaseConfig)
            restored
                .getSolverBenchmarkConfigList()
                .getFirst()
                .getSolverConfig()
                .getPhaseConfigList()
                .getFirst();
    var selector = phase.getMoveSelectorConfig();
    if (inUnion) selector = ((UnionMoveSelectorConfig) selector).getMoveSelectorList().getFirst();
    assertThat(selector).isInstanceOf(CrossVariableMultistageMoveSelectorConfig.class);
    var cross = (CrossVariableMultistageMoveSelectorConfig) selector;
    assertThat(cross.getStageProviderClass()).isEqualTo(CrossProvider.class);
    assertThat(cross.getCandidateCountLimit()).isEqualTo(11);
    assertThat(cross.getProbeCountLimit()).isEqualTo(29);
    assertThat(cross.getVariableList())
        .extracting(MultistageVariableConfig::getKind)
        .containsExactly(
            MultistageVariableKind.BASIC,
            MultistageVariableKind.BASIC,
            MultistageVariableKind.LIST);
    assertThat(cross.getVariableList())
        .extracting(MultistageVariableConfig::getVariableName)
        .containsExactly("basicValue", "secondBasicValue", "valueList");
    assertThat(cross.getVariableList())
        .extracting(MultistageVariableConfig::getEntityClass)
        .containsOnly(TestdataMixedEntity.class);
    assertThat(cross.getVariableList())
        .extracting(MultistageVariableConfig::getValueClass)
        .containsExactly(
            TestdataMixedOtherValue.class, TestdataMixedOtherValue.class, TestdataMixedValue.class);
  }

  public static final class CrossProvider
      implements CrossVariableStageProvider<TestdataMixedSolution, SimpleScore> {

    @Override
    public long getCandidateCount() {
      return 0;
    }

    @Override
    public List<CrossVariableCustomStage<TestdataMixedSolution, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of();
    }
  }
}
