package greycos.solver.benchmark.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;

import greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import greycos.solver.benchmark.impl.io.jaxb.PlannerBenchmarkConfigIO;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IteratedLocalSearchBenchmarkConfigTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void explicitPhaseValidatesRoundTripsAndBuilds(boolean island, @TempDir Path directory) {
    var phaseXml =
        """
        <iteratedLocalSearch>
          <moveThreadCount>NONE</moveThreadCount>
          <localSearch>
            <termination><stepCountLimit>2</stepCountLimit></termination>
            <localSearchType>LATE_ACCEPTANCE</localSearchType>
          </localSearch>
          <perturbation><unionMoveSelector><changeMoveSelector/><swapMoveSelector/></unionMoveSelector></perturbation>
          <perturbationStrengths>1 2</perturbationStrengths>
          <perturbationAttemptLimit>8</perturbationAttemptLimit>
          <episodeCandidateAttemptLimit>10</episodeCandidateAttemptLimit>
          <iterationCountLimit>2</iterationCountLimit>
        </iteratedLocalSearch>
        """;
    var xml =
        """
        <plannerBenchmark xmlns="https://github.com/CameleoGrey/greycos-solver/xsd/benchmark">
          <solverBenchmark>
            <name>Iterated local search</name>
            <solver>
              <solutionClass>%s</solutionClass>
              <entityClass>%s</entityClass>
              <scoreDirectorFactory><constraintProviderClass>%s</constraintProviderClass></scoreDirectorFactory>
              <termination><stepCountLimit>20</stepCountLimit></termination>
              <constructionHeuristic/>
              %s
            </solver>
          </solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(
                TestdataSolution.class.getName(),
                TestdataEntity.class.getName(),
                TestdataConstraintProvider.class.getName(),
                island ? "<islandModel>" + phaseXml + "</islandModel>" : phaseXml);
    var io = new PlannerBenchmarkConfigIO();
    var config = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(config, writer);
    assertThat(io.read(new StringReader(writer.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
    config.setBenchmarkDirectory(directory.toFile());
    var phase =
        config
            .getSolverBenchmarkConfigList()
            .getFirst()
            .getSolverConfig()
            .getPhaseConfigList()
            .getLast();
    var iterated =
        (IteratedLocalSearchPhaseConfig)
            (island ? ((IslandModelPhaseConfig) phase).getPhaseConfigList().getFirst() : phase);
    assertThat(iterated.getPerturbationStrengths()).containsExactly(1, 2);
    assertThat(iterated.getEpisodeCandidateAttemptLimit()).isEqualTo(10L);
    assertThat(
            PlannerBenchmarkFactory.create(config)
                .buildPlannerBenchmark(TestdataSolution.generateUninitializedSolution(3, 6)))
        .isNotNull();
  }
}
