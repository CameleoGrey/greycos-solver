package greycos.solver.benchmark.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;

import greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import greycos.solver.benchmark.impl.io.jaxb.PlannerBenchmarkConfigIO;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GuidedLocalSearchBenchmarkConfigTest {

  @ParameterizedTest
  @EnumSource(GuidedLocalSearchFeatureComposition.class)
  void explicitGuidedLocalSearchBenchmarkValidatesAndBuilds(
      GuidedLocalSearchFeatureComposition composition, @TempDir Path directory) {
    var automaticFeatures = composition != GuidedLocalSearchFeatureComposition.CUSTOM;
    var providerElement =
        composition == GuidedLocalSearchFeatureComposition.AUTOMATIC
            ? ""
            : "<featureProviderClass>"
                + FeatureProvider.class.getName()
                + "</featureProviderClass>";
    var xml =
        """
        <plannerBenchmark xmlns="https://github.com/CameleoGrey/greycos-solver/xsd/benchmark">
          <solverBenchmark>
            <name>GLS</name>
            <solver>
              <solutionClass>%s</solutionClass>
              <entityClass>%s</entityClass>
              <scoreDirectorFactory>
                <constraintProviderClass>%s</constraintProviderClass>
              </scoreDirectorFactory>
              <termination><stepCountLimit>2</stepCountLimit></termination>
              <constructionHeuristic/>
              <localSearch>
                <localSearchType>GUIDED_LOCAL_SEARCH</localSearchType>
                <guidedLocalSearch>
                  %s
                  <featureComposition>%s</featureComposition>
                  <automaticListOwnershipEnabled>%s</automaticListOwnershipEnabled>
                  <directedOriginSelection>%s</directedOriginSelection>
                  <penaltyFactor>0.125</penaltyFactor>
                  <targetScoreLevelIndex>0</targetScoreLevelIndex>
                  <maxPenaltyUpdatesPerStep>11</maxPenaltyUpdatesPerStep>
                  <excursionStepLimit>5</excursionStepLimit>
                  <excursionRepairStepLimit>29</excursionRepairStepLimit>
                  <searchMode>SAMPLED</searchMode>
                  <sampleSize>17</sampleSize>
                </guidedLocalSearch>
              </localSearch>
            </solver>
          </solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(
                TestdataSolution.class.getName(),
                TestdataEntity.class.getName(),
                TestdataConstraintProvider.class.getName(),
                providerElement,
                composition,
                automaticFeatures,
                automaticFeatures);
    var io = new PlannerBenchmarkConfigIO();
    var parsed = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(parsed, writer);
    var config = io.read(new StringReader(writer.toString()));
    config.setBenchmarkDirectory(directory.toFile());
    var solverConfig = config.getSolverBenchmarkConfigList().getFirst().getSolverConfig();
    var phase = (LocalSearchPhaseConfig) solverConfig.getPhaseConfigList().getLast();
    assertThat(phase.getLocalSearchType()).isEqualTo(LocalSearchType.GUIDED_LOCAL_SEARCH);
    var guidance = phase.getGuidedLocalSearchConfig();
    assertThat(guidance.getFeatureProviderClass())
        .isEqualTo(
            composition == GuidedLocalSearchFeatureComposition.AUTOMATIC
                ? null
                : FeatureProvider.class);
    assertThat(guidance.getFeatureComposition()).isEqualTo(composition);
    assertThat(guidance.getAutomaticListOwnershipEnabled()).isEqualTo(automaticFeatures);
    assertThat(guidance.getDirectedOriginSelection()).isEqualTo(automaticFeatures);
    assertThat(guidance.getPenaltyFactor()).isEqualByComparingTo("0.125");
    assertThat(guidance.getTargetScoreLevelIndex()).isZero();
    assertThat(guidance.getMaxPenaltyUpdatesPerStep()).isEqualTo(11);
    assertThat(guidance.getExcursionStepLimit()).isEqualTo(5);
    assertThat(guidance.getExcursionRepairStepLimit()).isEqualTo(29);
    assertThat(guidance.getSampleSize()).isEqualTo(17);
    assertThat(
            PlannerBenchmarkFactory.create(config)
                .buildPlannerBenchmark(TestdataSolution.generateUninitializedSolution(3, 6)))
        .isNotNull();
  }

  public static class FeatureProvider
      implements GuidedLocalSearchFeatureProvider<TestdataSolution, String> {

    @Override
    public void extractFeatures(
        TestdataSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {}

    @Override
    public GuidedLocalSearchFeatureSession<TestdataSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        @Override
        public void resetWorkingSolution(TestdataSolution solution) {}

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {}
      };
    }
  }
}
