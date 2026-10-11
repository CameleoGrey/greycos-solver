package greycos.solver.core.config.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;

class ResumableSearchConfigTest {

  @Test
  void defaultsRemainUnspecified() {
    var config = new ResumableSearchConfig();
    assertThat(config.getWorkerCount()).isNull();
    assertThat(config.getStepQuantum()).isNull();
    assertThat(config.getCooperationPolicy()).isNull();
    assertThat(config.getStagnationRounds()).isNull();
    assertThat(new IslandModelPhaseConfig().getResumableSearchConfig()).isNull();
  }

  @Test
  void inheritancePreservesOverridesAndCopiesNestedConfiguration() {
    var parent =
        new IslandModelPhaseConfig()
            .withResumableSearchConfig(
                new ResumableSearchConfig()
                    .withWorkerCount(4)
                    .withStepQuantum(7)
                    .withCooperationPolicy(ResumableSearchCooperationPolicy.RESTART_WEAKEST)
                    .withStagnationRounds(3));
    var child =
        new IslandModelPhaseConfig()
            .withResumableSearchConfig(new ResumableSearchConfig().withWorkerCount(2))
            .inherit(parent);
    var inherited = new IslandModelPhaseConfig().inherit(parent);

    assertThat(child.getResumableSearchConfig().getWorkerCount()).isEqualTo(2);
    assertThat(child.getResumableSearchConfig().getStepQuantum()).isEqualTo(7);
    assertThat(child.getResumableSearchConfig().getCooperationPolicy())
        .isEqualTo(ResumableSearchCooperationPolicy.RESTART_WEAKEST);
    assertThat(child.getResumableSearchConfig().getStagnationRounds()).isEqualTo(3);
    assertThat(inherited.getResumableSearchConfig()).isNotSameAs(parent.getResumableSearchConfig());

    parent.getResumableSearchConfig().setStepQuantum(64);
    assertThat(child.getResumableSearchConfig().getStepQuantum()).isEqualTo(7);
    assertThat(inherited.getResumableSearchConfig().getStepQuantum()).isEqualTo(7);
  }

  @Test
  void copiesNestedIslandsAndPartitionsIndependently() {
    var inner =
        new IslandModelPhaseConfig()
            .withResumableSearchConfig(
                new ResumableSearchConfig().withWorkerCount(4).withStepQuantum(8));
    var partition = new PartitionedSearchPhaseConfig().withPhaseConfigs(inner);
    var outer = new IslandModelPhaseConfig().withPhaseConfigList(List.of(partition));

    var copy = outer.copyConfig();
    var partitionCopy = (PartitionedSearchPhaseConfig) copy.getPhaseConfigList().getFirst();
    var innerCopy = (IslandModelPhaseConfig) partitionCopy.getPhaseConfigList().getFirst();
    assertThat(partitionCopy).isNotSameAs(partition);
    assertThat(innerCopy).isNotSameAs(inner);
    assertThat(innerCopy.getResumableSearchConfig()).isNotSameAs(inner.getResumableSearchConfig());

    inner.getResumableSearchConfig().setWorkerCount(8);
    assertThat(innerCopy.getResumableSearchConfig().getWorkerCount()).isEqualTo(4);
  }

  @Test
  void xmlRoundTripSupportsBothNestingDirectionsAndRecursiveIslands() {
    var xml =
        """
        <solver xmlns="%s">
          <islandModel>
            <islandCount>4</islandCount>
            <resumableSearch>
              <workerCount>2</workerCount>
              <stepQuantum>7</stepQuantum>
              <cooperationPolicy>RESTART_WEAKEST</cooperationPolicy>
              <stagnationRounds>3</stagnationRounds>
            </resumableSearch>
            <islandModel>
              <resumableSearch/>
              <partitionedSearch>
                <islandModel>
                  <localSearch/>
                </islandModel>
              </partitionedSearch>
            </islandModel>
          </islandModel>
        </solver>
        """
            .formatted(SolverConfig.XML_NAMESPACE);
    var io = new SolverConfigIO();
    var config = io.read(new StringReader(xml));
    var outer = (IslandModelPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(outer.getResumableSearchConfig().getWorkerCount()).isEqualTo(2);
    assertThat(outer.getResumableSearchConfig().getStepQuantum()).isEqualTo(7);
    assertThat(outer.getResumableSearchConfig().getCooperationPolicy())
        .isEqualTo(ResumableSearchCooperationPolicy.RESTART_WEAKEST);
    assertThat(outer.getResumableSearchConfig().getStagnationRounds()).isEqualTo(3);
    var inner = (IslandModelPhaseConfig) outer.getPhaseConfigList().getFirst();
    assertThat(inner.getResumableSearchConfig().getWorkerCount()).isNull();
    assertThat(inner.getPhaseConfigList())
        .singleElement()
        .isInstanceOf(PartitionedSearchPhaseConfig.class);

    var output = new StringWriter();
    io.write(config, output);
    assertThat(io.read(new StringReader(output.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void referencedClassesTraverseNestedHierarchy() {
    var localSearch =
        new LocalSearchPhaseConfig()
            .withGuidedLocalSearchConfig(
                new GuidedLocalSearchConfig().withFeatureProviderClass(TestFeatureProvider.class));
    var outer =
        new IslandModelPhaseConfig()
            .withResumableSearchConfig(new ResumableSearchConfig())
            .withPhaseConfigList(
                List.of(new PartitionedSearchPhaseConfig().withPhaseConfigs(localSearch)));
    var classes = new ArrayList<Class<?>>();

    outer.visitReferencedClasses(classes::add);

    assertThat(classes).contains(TestFeatureProvider.class);
  }

  // Only class discovery is exercised; the provider is not instantiated.
  public abstract static class TestFeatureProvider
      implements GuidedLocalSearchFeatureProvider<Object, Object> {}
}
