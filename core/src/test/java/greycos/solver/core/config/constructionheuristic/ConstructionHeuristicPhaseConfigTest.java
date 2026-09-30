package greycos.solver.core.config.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.score.DummySimpleScoreEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConstructionHeuristicPhaseConfigTest {

  @Test
  void nearbyInheritanceAndExplicitOverrides() {
    var parent =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withNearbySelectionSize(12);
    var inherited = new ConstructionHeuristicPhaseConfig().inherit(parent);
    assertThat(inherited.getNearbySelectionAutoConfigurationEnabled()).isTrue();
    assertThat(inherited.getNearbySelectionSize()).isEqualTo(12);
    var overridden =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(false)
            .withNearbySelectionSize(7)
            .inherit(parent);
    assertThat(overridden.getNearbySelectionAutoConfigurationEnabled()).isFalse();
    assertThat(overridden.getNearbySelectionSize()).isEqualTo(7);
  }

  @Test
  void copyDoesNotAliasPlacerOrSourceSelector() {
    var queue =
        new QueuedValuePlacerConfig()
            .withValueSelectorConfig(new ValueSelectorConfig("visits").withId("queue"))
            .withMoveSelectorConfig(new ListChangeMoveSelectorConfig());
    var original =
        new ConstructionHeuristicPhaseConfig()
            .withEntityPlacerConfig(queue)
            .withNearbySelectionSize(3);
    var copy = original.copyConfig();
    var copiedQueue = (QueuedValuePlacerConfig) copy.getEntityPlacerConfig();
    assertThat(copiedQueue).isNotSameAs(queue);
    assertThat(copiedQueue.getValueSelectorConfig()).isNotSameAs(queue.getValueSelectorConfig());
    copiedQueue.getValueSelectorConfig().setId("different");
    assertThat(queue.getValueSelectorConfig().getId()).isEqualTo("queue");
    assertThat(copy.getNearbySelectionSize()).isEqualTo(3);
  }

  @Test
  void xmlSupportsSettingsAndDirectListChangeInBothLocations() {
    var xml =
        """
        <solver xmlns="https://github.com/CameleoGrey/greycos-solver/xsd/solver">
          <constructionHeuristic>
            <nearbySelectionAutoConfigurationEnabled>false</nearbySelectionAutoConfigurationEnabled>
            <nearbySelectionSize>12</nearbySelectionSize>
            <listChangeMoveSelector/>
          </constructionHeuristic>
          <constructionHeuristic>
            <queuedValuePlacer>
              <valueSelector id="queue" variableName="visits"/>
              <listChangeMoveSelector>
                <valueSelector mimicSelectorRef="queue"/>
              </listChangeMoveSelector>
            </queuedValuePlacer>
          </constructionHeuristic>
        </solver>
        """;
    var io = new SolverConfigIO();
    var config = io.read(new StringReader(xml));
    var phase = (ConstructionHeuristicPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phase.getNearbySelectionAutoConfigurationEnabled()).isFalse();
    assertThat(phase.getNearbySelectionSize()).isEqualTo(12);
    assertThat(phase.getMoveSelectorConfigList().getFirst())
        .isInstanceOf(ListChangeMoveSelectorConfig.class);
    var queue =
        (QueuedValuePlacerConfig)
            ((ConstructionHeuristicPhaseConfig) config.getPhaseConfigList().get(1))
                .getEntityPlacerConfig();
    assertThat(queue.getMoveSelectorConfig()).isInstanceOf(ListChangeMoveSelectorConfig.class);
    var output = new StringWriter();
    io.write(config, output);
    assertThat(io.read(new StringReader(output.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void invalidNearbySizeFailsWhenSolverIsBuilt(int size) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class)
            .withPhases(new ConstructionHeuristicPhaseConfig().withNearbySelectionSize(size));
    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nearbySelectionSize")
        .hasMessageContaining("at least 1");
  }
}
