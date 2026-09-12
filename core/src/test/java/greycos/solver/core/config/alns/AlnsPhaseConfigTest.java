package greycos.solver.core.config.alns;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsRanking;
import greycos.solver.core.api.solver.alns.AlnsRelatedness;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.testcotwin.TestdataEntity;

import org.junit.jupiter.api.Test;

class AlnsPhaseConfigTest {
  @Test
  void xmlRoundTripIncludesTopLevelAndNestedPhases() {
    var phase =
        new AlnsPhaseConfig()
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(12))
            .withSelectionPolicyType(AlnsSelectionPolicyType.UCB)
            .withSelectionPolicyCustomProperties(Map.of("exploration", "1.5"))
            .withAcceptanceType(AlnsAcceptanceType.SIMULATED_ANNEALING)
            .withStartingTemperature("10hard/20soft")
            .withAcceptancePolicyCustomProperties(Map.of("threshold", "2"))
            .withCoolingRate(0.99)
            .withRepairSpentLimit(Duration.ofMillis(50))
            .withRepairScoreCalculationLimit(100L)
            .withDestroyOperators(
                new AlnsDestroyOperatorConfig()
                    .withId("random")
                    .withType(AlnsDestroyOperatorType.RANDOM)
                    .withEntityClass(TestdataEntity.class)
                    .withVariableName("value")
                    .withMinimumDestroyedCount(1)
                    .withMaximumDestroyedCount(4))
            .withRepairOperators(
                new AlnsRepairOperatorConfig()
                    .withId("regret")
                    .withType(AlnsRepairOperatorType.REGRET_2)
                    .withInitialWeight(2.0));
    var config =
        new SolverConfig()
            .withPhases(
                phase,
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withPhaseConfigList(List.of(phase.copyConfig())),
                new PartitionedSearchPhaseConfig().withPhaseConfigs(phase.copyConfig()));
    var io = new SolverConfigIO();
    var writer = new StringWriter();
    io.write(config, writer);
    assertThat(writer.toString()).contains("<alns>", "<destroyOperator>", "<repairOperator>");
    assertThat(io.read(new StringReader(writer.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void namespacedXmlValidatesAgainstGeneratedSchema() {
    var xml =
        """
        <solver xmlns="%s">
          <alns>
            <termination><stepCountLimit>2</stepCountLimit></termination>
            <destroyOperator><id>random</id><type>RANDOM</type></destroyOperator>
            <repairOperator><id>greedy</id><type>GREEDY</type></repairOperator>
            <selectionPolicyType>SEGMENTED_ROULETTE</selectionPolicyType>
            <selectionPolicyCustomProperties><property name="selection" value="example"/></selectionPolicyCustomProperties>
            <acceptanceType>LATE_ACCEPTANCE</acceptanceType>
            <acceptancePolicyCustomProperties><property name="acceptance" value="example"/></acceptancePolicyCustomProperties>
            <lateAcceptanceSize>128</lateAcceptanceSize>
            <repairSpentLimit>PT0.05S</repairSpentLimit>
          </alns>
        </solver>
        """
            .formatted(SolverConfig.XML_NAMESPACE);
    var config = new SolverConfigIO().read(new StringReader(xml));
    assertThat(config.getPhaseConfigList()).singleElement().isInstanceOf(AlnsPhaseConfig.class);
    assertThat(((AlnsPhaseConfig) config.getPhaseConfigList().getFirst()).getRepairSpentLimit())
        .isEqualTo(Duration.ofMillis(50));
    assertThat(
            ((AlnsPhaseConfig) config.getPhaseConfigList().getFirst())
                .getSelectionPolicyCustomProperties())
        .containsEntry("selection", "example");
    assertThat(
            ((AlnsPhaseConfig) config.getPhaseConfigList().getFirst())
                .getAcceptancePolicyCustomProperties())
        .containsEntry("acceptance", "example");
  }

  @Test
  void copyAndInheritanceDoNotShareMutableOperatorConfiguration() {
    var original =
        new AlnsPhaseConfig()
            .withSegmentLength(100)
            .withSelectionPolicyCustomProperties(
                new LinkedHashMap<>(Map.of("selection", "original")))
            .withAcceptancePolicyCustomProperties(
                new LinkedHashMap<>(Map.of("acceptance", "original")))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(4))
            .withDestroyOperators(
                new AlnsDestroyOperatorConfig()
                    .withId("destroy")
                    .withCustomProperties(new LinkedHashMap<>(Map.of("setting", "original"))))
            .withRepairOperators(
                new AlnsRepairOperatorConfig()
                    .withId("repair")
                    .withCustomProperties(new LinkedHashMap<>(Map.of("setting", "original"))));
    var copy = original.copyConfig();
    copy.getDestroyOperatorConfigList().getFirst().getCustomProperties().put("setting", "changed");
    copy.getRepairOperatorConfigList().getFirst().setId("changed");
    copy.getTerminationConfig().setStepCountLimit(9);
    copy.getSelectionPolicyCustomProperties().put("selection", "changed");
    copy.getAcceptancePolicyCustomProperties().put("acceptance", "changed");
    assertThat(original.getDestroyOperatorConfigList().getFirst().getCustomProperties())
        .containsEntry("setting", "original");
    assertThat(original.getRepairOperatorConfigList().getFirst().getId()).isEqualTo("repair");
    assertThat(original.getTerminationConfig().getStepCountLimit()).isEqualTo(4);
    assertThat(original.getSelectionPolicyCustomProperties())
        .containsEntry("selection", "original");
    assertThat(original.getAcceptancePolicyCustomProperties())
        .containsEntry("acceptance", "original");
    assertThat(new AlnsPhaseConfig().withSegmentLength(7).inherit(original).getSegmentLength())
        .isEqualTo(7);
    assertThat(new AlnsPhaseConfig().getLateAcceptanceSize()).isNull();
  }

  @Test
  void nativeDiscoveryVisitsEveryCustomExtensionAndBindingClass() {
    var config =
        new AlnsPhaseConfig()
            .withSelectionPolicyClass(AlnsSelectionPolicy.class)
            .withAcceptancePolicyClass(AlnsAcceptancePolicy.class)
            .withDestroyOperators(
                new AlnsDestroyOperatorConfig()
                    .withEntityClass(TestdataEntity.class)
                    .withCustomClass(AlnsDestroyOperator.class)
                    .withRankingClass(AlnsRanking.class)
                    .withRelatednessClass(AlnsRelatedness.class))
            .withRepairOperators(
                new AlnsRepairOperatorConfig().withCustomClass(AlnsRepairOperator.class));
    var classes = new ArrayList<Class<?>>();
    config.visitReferencedClasses(classes::add);
    assertThat(classes)
        .containsExactlyInAnyOrder(
            AlnsSelectionPolicy.class,
            AlnsAcceptancePolicy.class,
            TestdataEntity.class,
            AlnsDestroyOperator.class,
            AlnsRanking.class,
            AlnsRelatedness.class,
            AlnsRepairOperator.class);
  }
}
