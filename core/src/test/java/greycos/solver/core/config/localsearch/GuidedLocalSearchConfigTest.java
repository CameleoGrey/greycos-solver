package greycos.solver.core.config.localsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchConfigTest {

  @Test
  void defaultsStayUnspecifiedForInheritanceAndSerialization() {
    var config = new GuidedLocalSearchConfig();
    assertThat(config.getFeatureProviderClass()).isNull();
    assertThat(config.getPenaltyFactor()).isNull();
    assertThat(config.getTargetScoreLevelIndex()).isNull();
    assertThat(config.getSearchMode()).isNull();
    assertThat(config.getSampleSize()).isNull();
    assertThat(config.getMaxUnproductiveRounds()).isNull();
    assertThat(config.getResetPenaltiesOnNewBest()).isNull();
    var visited = new ArrayList<Class<?>>();
    config.visitReferencedClasses(visited::add);
    assertThat(visited).isEmpty();
  }

  @Test
  void inheritanceFillsMissingPropertiesAndPreservesOverrides() {
    var parent = configured();
    var child =
        new GuidedLocalSearchConfig()
            .withPenaltyFactor(new BigDecimal("0.125"))
            .withSampleSize(17)
            .withResetPenaltiesOnNewBest(false);
    child.inherit(parent);

    assertThat(child.getFeatureProviderClass()).isEqualTo(FeatureProvider.class);
    assertThat(child.getPenaltyFactor()).isEqualByComparingTo("0.125");
    assertThat(child.getTargetScoreLevelIndex()).isZero();
    assertThat(child.getSearchMode()).isEqualTo(GuidedLocalSearchSearchMode.SAMPLED);
    assertThat(child.getSampleSize()).isEqualTo(17);
    assertThat(child.getMaxUnproductiveRounds()).isEqualTo(5);
    assertThat(child.getResetPenaltiesOnNewBest()).isFalse();
    assertThat(parent.getSampleSize()).isEqualTo(23);
    assertThat(parent.getResetPenaltiesOnNewBest()).isTrue();
  }

  @Test
  void localSearchAndIslandCopiesDoNotShareGuidanceConfiguration() {
    var original = configured();
    var localSearch = new LocalSearchPhaseConfig().withGuidedLocalSearchConfig(original);
    var island =
        new IslandModelPhaseConfig()
            .withGuidedLocalSearchConfig(original)
            .withPhaseConfigList(List.of(localSearch));
    var localCopy = localSearch.copyConfig();
    var islandCopy = island.copyConfig();
    var innerCopy = (LocalSearchPhaseConfig) islandCopy.getPhaseConfigList().getFirst();

    assertThat(localCopy.getGuidedLocalSearchConfig()).isNotSameAs(original);
    assertThat(islandCopy.getGuidedLocalSearchConfig()).isNotSameAs(original);
    assertThat(innerCopy.getGuidedLocalSearchConfig()).isNotSameAs(original);
    original.setSampleSize(99);
    assertThat(localCopy.getGuidedLocalSearchConfig().getSampleSize()).isEqualTo(23);
    assertThat(islandCopy.getGuidedLocalSearchConfig().getSampleSize()).isEqualTo(23);
    assertThat(innerCopy.getGuidedLocalSearchConfig().getSampleSize()).isEqualTo(23);
  }

  @Test
  void referencedProviderIsDiscoveredThroughEveryConfigurationPath() {
    var localSearch = new LocalSearchPhaseConfig().withGuidedLocalSearchConfig(configured());
    var configurations =
        List.of(
            new SolverConfig().withPhases(localSearch),
            new SolverConfig()
                .withPhases(new IslandModelPhaseConfig().withGuidedLocalSearchConfig(configured())),
            new SolverConfig()
                .withPhases(
                    new IslandModelPhaseConfig().withPhaseConfigList(List.of(localSearch))));
    for (var config : configurations) {
      var visited = new ArrayList<Class<?>>();
      config.visitReferencedClasses(visited::add);
      assertThat(visited).contains(FeatureProvider.class);
    }
  }

  @Test
  void namespacedXmlAndRoundTripPreserveAllProperties() {
    var xml =
        """
        <solver xmlns="https://github.com/CameleoGrey/greycos-solver/xsd/solver">
          <localSearch>
            <localSearchType>GUIDED_LOCAL_SEARCH</localSearchType>
            <guidedLocalSearch>
              <featureProviderClass>%s</featureProviderClass>
              <penaltyFactor>0.125</penaltyFactor>
              <targetScoreLevelIndex>0</targetScoreLevelIndex>
              <searchMode>SAMPLED</searchMode>
              <sampleSize>23</sampleSize>
              <maxUnproductiveRounds>5</maxUnproductiveRounds>
              <resetPenaltiesOnNewBest>true</resetPenaltiesOnNewBest>
            </guidedLocalSearch>
          </localSearch>
          <islandModel>
            <localSearchType>GUIDED_LOCAL_SEARCH</localSearchType>
            <guidedLocalSearch>
              <featureProviderClass>%s</featureProviderClass>
              <searchMode>EXHAUSTIVE</searchMode>
            </guidedLocalSearch>
          </islandModel>
        </solver>
        """
            .formatted(FeatureProvider.class.getName(), FeatureProvider.class.getName());
    var io = new SolverConfigIO();
    var parsed = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(parsed, writer);
    var restored = io.read(new StringReader(writer.toString()));
    var phase = (LocalSearchPhaseConfig) restored.getPhaseConfigList().getFirst();
    assertThat(phase.getLocalSearchType()).isEqualTo(LocalSearchType.GUIDED_LOCAL_SEARCH);
    var config = phase.getGuidedLocalSearchConfig();
    assertThat(config.getFeatureProviderClass()).isEqualTo(FeatureProvider.class);
    assertThat(config.getPenaltyFactor()).isEqualByComparingTo("0.125");
    assertThat(config.getTargetScoreLevelIndex()).isZero();
    assertThat(config.getSearchMode()).isEqualTo(GuidedLocalSearchSearchMode.SAMPLED);
    assertThat(config.getSampleSize()).isEqualTo(23);
    assertThat(config.getMaxUnproductiveRounds()).isEqualTo(5);
    assertThat(config.getResetPenaltiesOnNewBest()).isTrue();
    var island = (IslandModelPhaseConfig) restored.getPhaseConfigList().getLast();
    assertThat(island.getGuidedLocalSearchConfig().getFeatureProviderClass())
        .isEqualTo(FeatureProvider.class);
    assertThat(island.getGuidedLocalSearchConfig().getSearchMode())
        .isEqualTo(GuidedLocalSearchSearchMode.EXHAUSTIVE);
    assertThat(island.getGuidedLocalSearchConfig().getSampleSize()).isNull();
  }

  private static GuidedLocalSearchConfig configured() {
    return new GuidedLocalSearchConfig()
        .withFeatureProviderClass(FeatureProvider.class)
        .withPenaltyFactor(new BigDecimal("0.2"))
        .withTargetScoreLevelIndex(0)
        .withSearchMode(GuidedLocalSearchSearchMode.SAMPLED)
        .withSampleSize(23)
        .withMaxUnproductiveRounds(5)
        .withResetPenaltiesOnNewBest(true);
  }

  // These tests exercise class discovery and serialization, not provider instantiation.
  public abstract static class FeatureProvider
      implements GuidedLocalSearchFeatureProvider<Object, Object> {}
}
