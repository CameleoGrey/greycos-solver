package greycos.solver.core.config.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.DefaultGeneticAlgorithmPhaseFactory;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListWorkload;
import greycos.solver.core.impl.io.jaxb.GreyCOSXmlSerializationException;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.phase.PhaseFactory;
import greycos.solver.core.impl.phase.PhaseType;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmPhaseConfigTest {

  @Test
  void defaultsResolveAfterInheritanceWithoutMutatingOriginal() {
    var original = new GeneticAlgorithmPhaseConfig();
    var resolved = original.resolve();
    assertThat(original.getPopulationSize()).isNull();
    assertThat(original.getCrossoverProbability()).isNull();
    assertThat(original.getPBestRate()).isNull();
    assertThat(original.getMutationRateMultiplier()).isNull();
    assertThat(original.getTabuEntityRate()).isNull();
    assertThat(original.getNoProgressAttemptLimit()).isNull();
    assertThat(original.getMoveThreadCount()).isNull();
    assertThat(original.getMutationOperatorConfigList()).isNull();
    assertThat(resolved.getPopulationSize()).isEqualTo(128);
    assertThat(resolved.getCrossoverProbability()).isEqualTo(0.5);
    assertThat(resolved.getPBestRate()).isEqualTo(0.05);
    assertThat(resolved.getMutationRateMultiplier()).isEqualTo(0.0);
    assertThat(resolved.getTabuEntityRate()).isEqualTo(0.0);
    assertThat(resolved.getNoProgressAttemptLimit()).isEqualTo(1280L);
    assertThat(resolved.getMutationOperatorConfigList())
        .extracting(GeneticAlgorithmMutationOperatorConfig::getType)
        .containsExactly(GeneticAlgorithmMutationType.values());
    assertThat(resolved.getMutationOperatorConfigList())
        .allSatisfy(operator -> assertThat(operator.getProbability()).isEqualTo(1.0 / 6.0));
    assertThat(
            new GeneticAlgorithmPhaseConfig()
                .withPopulationSize(1)
                .resolve()
                .getNoProgressAttemptLimit())
        .isEqualTo(128L);
    assertThat(
            new GeneticAlgorithmPhaseConfig()
                .withPopulationSize(Integer.MAX_VALUE)
                .resolve()
                .getNoProgressAttemptLimit())
        .isEqualTo(10L * Integer.MAX_VALUE);
  }

  @Test
  void copiesAndInheritanceAreIndependentAndExplicitListsOverride() {
    var original =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(12)
            .withCrossoverProbability(0.2)
            .withPBestRate(0.1)
            .withMutationRateMultiplier(2.5)
            .withTabuEntityRate(0.3)
            .withNoProgressAttemptLimit(500L)
            .withMoveThreadCount("NONE")
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(7))
            .withMutationOperators(operator(GeneticAlgorithmMutationType.CHANGE, 1.0));
    var copy = original.copyConfig();
    assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    copy.getMutationOperatorConfigList().getFirst().setProbability(0.5);
    copy.getMutationOperatorConfigList().add(operator(GeneticAlgorithmMutationType.SWAP, 0.5));
    copy.getTerminationConfig().setStepCountLimit(2);
    assertThat(original.getMutationOperatorConfigList()).hasSize(1);
    assertThat(original.getMutationOperatorConfigList().getFirst().getProbability()).isEqualTo(1.0);
    assertThat(original.getTerminationConfig().getStepCountLimit()).isEqualTo(7);

    var inherited =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(3)
            .withMutationOperators(operator(GeneticAlgorithmMutationType.INVERSE, 1.0))
            .inherit(original)
            .resolve();
    assertThat(inherited.getPopulationSize()).isEqualTo(3);
    assertThat(inherited.getCrossoverProbability()).isEqualTo(0.2);
    assertThat(inherited.getMutationOperatorConfigList())
        .extracting(GeneticAlgorithmMutationOperatorConfig::getType)
        .containsExactly(GeneticAlgorithmMutationType.INVERSE);
    assertThat(
            new GeneticAlgorithmMutationOperatorConfig()
                .inherit(original.getMutationOperatorConfigList().getFirst())
                .getProbability())
        .isEqualTo(1.0);
  }

  @ParameterizedTest
  @ValueSource(
      doubles = {-1.0, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
  void unitIntervalRatesRejectInvalidValues(double value) {
    assertThatThrownBy(
            () -> new GeneticAlgorithmPhaseConfig().withCrossoverProbability(value).resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("crossoverProbability");
    assertThatThrownBy(() -> new GeneticAlgorithmPhaseConfig().withTabuEntityRate(value).resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tabuEntityRate");
  }

  @ParameterizedTest
  @ValueSource(
      doubles = {
        0.0,
        0.000001,
        1.1,
        Double.NaN,
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY
      })
  void pBestRateRejectsInvalidValues(double value) {
    assertThatThrownBy(() -> new GeneticAlgorithmPhaseConfig().withPBestRate(value).resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pBestRate");
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
  void mutationMultiplierRejectsInvalidValues(double value) {
    assertThatThrownBy(
            () -> new GeneticAlgorithmPhaseConfig().withMutationRateMultiplier(value).resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mutationRateMultiplier");
  }

  @Test
  void validBoundaryRatesAndPositiveLimits() {
    assertThat(
            new GeneticAlgorithmPhaseConfig()
                .withCrossoverProbability(0.0)
                .withTabuEntityRate(1.0)
                .withPBestRate(Math.nextUp(0.000001))
                .resolve())
        .isNotNull();
    assertThat(
            new GeneticAlgorithmPhaseConfig()
                .withCrossoverProbability(1.0)
                .withTabuEntityRate(0.0)
                .withPBestRate(1.0)
                .resolve())
        .isNotNull();
    assertThatThrownBy(() -> new GeneticAlgorithmPhaseConfig().withPopulationSize(0).resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("populationSize (0)");
    assertThatThrownBy(
            () -> new GeneticAlgorithmPhaseConfig().withNoProgressAttemptLimit(0L).resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("noProgressAttemptLimit (0)");
  }

  @Test
  void namedMutationProbabilitiesMustBeCompleteAndValid() {
    assertThatThrownBy(() -> new GeneticAlgorithmPhaseConfig().withMutationOperators().resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sum to 1.0");
    assertThatThrownBy(
            () ->
                new GeneticAlgorithmPhaseConfig()
                    .withMutationOperators(
                        operator(GeneticAlgorithmMutationType.CHANGE, 0.5),
                        operator(GeneticAlgorithmMutationType.CHANGE, 0.5))
                    .resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CHANGE")
        .hasMessageContaining("unique");
    assertThatThrownBy(
            () ->
                new GeneticAlgorithmPhaseConfig()
                    .withMutationOperators(
                        new GeneticAlgorithmMutationOperatorConfig().withProbability(1.0))
                    .resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("specify a type");
    assertThatThrownBy(
            () ->
                new GeneticAlgorithmPhaseConfig()
                    .withMutationOperators(
                        new GeneticAlgorithmMutationOperatorConfig()
                            .withType(GeneticAlgorithmMutationType.CHANGE))
                    .resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("probability (null)");
    assertThatThrownBy(
            () ->
                new GeneticAlgorithmPhaseConfig()
                    .withMutationOperatorConfigList(
                        Arrays.asList((GeneticAlgorithmMutationOperatorConfig) null))
                    .resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("specify a type");
    for (double probability :
        new double[] {-0.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThatThrownBy(
              () ->
                  new GeneticAlgorithmPhaseConfig()
                      .withMutationOperators(
                          operator(GeneticAlgorithmMutationType.CHANGE, probability))
                      .resolve())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("probability");
    }
    assertThatThrownBy(
            () ->
                new GeneticAlgorithmPhaseConfig()
                    .withMutationOperators(operator(GeneticAlgorithmMutationType.CHANGE, 0.9))
                    .resolve())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sum to 1.0");
    assertThat(
            new GeneticAlgorithmPhaseConfig()
                .withMutationOperators(
                    operator(GeneticAlgorithmMutationType.CHANGE, 1.0),
                    operator(GeneticAlgorithmMutationType.SWAP, 0.0))
                .resolve()
                .getMutationOperatorConfigList())
        .hasSize(2);
  }

  @Test
  void xmlRoundTripAndGeneratedSchemaSupportAllConfiguration() {
    var xml =
        """
        <solver xmlns="%s">
          <geneticAlgorithm>
            <environmentMode>FULL_ASSERT</environmentMode>
            <termination><stepCountLimit>23</stepCountLimit></termination>
            <populationSize>5</populationSize>
            <crossoverProbability>0.75</crossoverProbability>
            <pBestRate>0.25</pBestRate>
            <mutationRateMultiplier>2.5</mutationRateMultiplier>
            <tabuEntityRate>0.1</tabuEntityRate>
            <noProgressAttemptLimit>99</noProgressAttemptLimit>
            <moveThreadCount>NONE</moveThreadCount>
            <mutationOperator><type>CHANGE</type><probability>0.3</probability></mutationOperator>
            <mutationOperator><type>INVERSE</type><probability>0.7</probability></mutationOperator>
          </geneticAlgorithm>
        </solver>
        """
            .formatted(SolverConfig.XML_NAMESPACE);
    var io = new SolverConfigIO();
    var config = io.read(new StringReader(xml));
    var phase = (GeneticAlgorithmPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phase.resolve().getPopulationSize()).isEqualTo(5);
    assertThat(phase.getMutationOperatorConfigList())
        .extracting(GeneticAlgorithmMutationOperatorConfig::getType)
        .containsExactly(GeneticAlgorithmMutationType.CHANGE, GeneticAlgorithmMutationType.INVERSE);
    var writer = new StringWriter();
    io.write(config, writer);
    assertThat(io.read(new StringReader(writer.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
    assertThatThrownBy(() -> io.read(new StringReader(xml.replace("INVERSE", "UNKNOWN"))))
        .isInstanceOf(GreyCOSXmlSerializationException.class);
  }

  @Test
  void factoryRegistrationAndEventIdentityAreDistinct() {
    assertThat(PhaseFactory.create(new GeneticAlgorithmPhaseConfig()))
        .isInstanceOf(DefaultGeneticAlgorithmPhaseFactory.class);
    assertThat(PhaseFactory.requiresInitializedSolution(new GeneticAlgorithmPhaseConfig()))
        .isTrue();
    assertThat(EventProducerId.geneticAlgorithm(4).phaseIndex()).hasValue(4);
    assertThat(EventProducerId.geneticAlgorithm(4).simpleProducerName())
        .isEqualTo("Genetic Algorithm");
    assertThat(EventProducerId.geneticAlgorithm(4).producerId()).isEqualTo("Genetic Algorithm (4)");
    assertThat(PhaseType.GENETIC_ALGORITHM.getPhaseName()).isEqualTo("Genetic Algorithm");
  }

  @Test
  void phaseNoneOverridesSolverWorkersAndEnabledWorkersAreRejected() {
    var phase = new GeneticAlgorithmPhaseConfig();
    var config = basicConfig(phase).withMoveThreadCount("2");
    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("moveThreadCount (2)");
    phase.setMoveThreadCount("NONE");
    assertThat(SolverFactory.create(config).buildSolver()).isNotNull();
    phase.setMoveThreadCount("1");
    config.setMoveThreadCount("NONE");
    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("moveThreadCount (1)");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void listAndMixedModelsSolveThroughJavaAndXmlConfiguration(boolean xml) {
    var workload = new ListWorkload();
    var listConfig =
        workload
            .solverConfig(
                "NONE",
                0L,
                1,
                new TerminationConfig().withStepCountLimit(12),
                EnvironmentMode.NO_ASSERT)
            .withPhases(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(4)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(12)));
    // The workload disables all metrics for benchmarks; exercise the standard XML defaults here.
    listConfig.setMonitoringConfig(null);
    var listResult =
        SolverFactory.<ListSolution>create(configure(listConfig, xml))
            .buildSolver()
            .solve(workload.createProblem(8));
    assertThat(workload.score(listResult)).isEqualTo(workload.recompute(listResult));
    var mixedConfig =
        PlannerTestUtils.buildSolverConfig(
                TestdataMixedSolution.class,
                TestdataMixedEntity.class,
                TestdataMixedValue.class,
                TestdataMixedOtherValue.class)
            .withEasyScoreCalculatorClass(TestdataMixedEasyScoreCalculator.class)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(12))
            .withPhases(new GeneticAlgorithmPhaseConfig().withPopulationSize(4));
    var mixedInput = TestdataMixedSolution.generateUninitializedSolution(2, 8, 2);
    for (var entity : mixedInput.getEntityList()) {
      entity.setBasicValue(mixedInput.getOtherValueList().getFirst());
      entity.setSecondBasicValue(mixedInput.getOtherValueList().getLast());
    }
    for (var i = 0; i < mixedInput.getValueList().size(); i++) {
      mixedInput.getEntityList().get(i % 2).getValueList().add(mixedInput.getValueList().get(i));
    }
    var mixedResult =
        SolverFactory.<TestdataMixedSolution>create(configure(mixedConfig, xml))
            .buildSolver()
            .solve(mixedInput);
    assertThat(mixedResult.getScore())
        .isEqualTo(new TestdataMixedEasyScoreCalculator().calculateScore(mixedResult));
    assertThat(
            mixedResult.getEntityList().stream()
                .flatMap(entity -> entity.getValueList().stream())
                .toList())
        .containsExactlyInAnyOrderElementsOf(mixedResult.getValueList());
  }

  private static SolverConfig configure(SolverConfig config, boolean xml) {
    if (!xml) return config;
    var io = new SolverConfigIO();
    var writer = new StringWriter();
    io.write(config, writer);
    return io.read(new StringReader(writer.toString()));
  }

  @Test
  void nestedPhasesFailBeforeFactoryCreatesWorkers() {
    var geneticAlgorithm = new GeneticAlgorithmPhaseConfig();
    for (PhaseConfig<?> enclosing :
        List.of(
            new IslandModelPhaseConfig().withPhaseConfigList(List.of(geneticAlgorithm)),
            new PartitionedSearchPhaseConfig().withPhaseConfigs(geneticAlgorithm),
            new IslandModelPhaseConfig()
                .withPhaseConfigList(
                    List.of(new PartitionedSearchPhaseConfig().withPhaseConfigs(geneticAlgorithm))),
            new PartitionedSearchPhaseConfig()
                .withPhaseConfigs(
                    new IslandModelPhaseConfig().withPhaseConfigList(List.of(geneticAlgorithm))))) {
      assertThatThrownBy(() -> SolverFactory.create(basicConfig(enclosing)))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("cannot be nested under islandModel or partitionedSearch");
    }
  }

  private static GeneticAlgorithmMutationOperatorConfig operator(
      GeneticAlgorithmMutationType type, double probability) {
    return new GeneticAlgorithmMutationOperatorConfig().withType(type).withProbability(probability);
  }

  private static SolverConfig basicConfig(PhaseConfig<?> phase) {
    return new BasicWorkload()
        .solverConfig(
            "NONE", 0L, 1, new TerminationConfig().withStepCountLimit(1), EnvironmentMode.NO_ASSERT)
        .withPhases(phase);
  }
}
