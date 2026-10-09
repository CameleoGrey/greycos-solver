package greycos.solver.core.config.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySorterManner;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSorterManner;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhase;
import greycos.solver.core.impl.constructionheuristic.placer.RandomAssignmentEntityPlacer;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.score.DummySimpleScoreEasyScoreCalculator;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RandomAssignmentPhaseConfigTest {

  @Test
  void xmlAndInheritanceKeepRandomConstructionExplicit() {
    var io = new SolverConfigIO();
    var config =
        io.read(
            new StringReader(
                """
        <solver xmlns="https://github.com/CameleoGrey/greycos-solver/xsd/solver">
          <constructionHeuristic>
            <constructionHeuristicType>RANDOM_ASSIGNMENT</constructionHeuristicType>
          </constructionHeuristic>
        </solver>
        """));
    var phase = (ConstructionHeuristicPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phase.getConstructionHeuristicType())
        .isEqualTo(ConstructionHeuristicType.RANDOM_ASSIGNMENT);
    assertThat(phase.copyConfig().getConstructionHeuristicType())
        .isEqualTo(ConstructionHeuristicType.RANDOM_ASSIGNMENT);
    assertThat(new ConstructionHeuristicPhaseConfig().inherit(phase).getConstructionHeuristicType())
        .isEqualTo(ConstructionHeuristicType.RANDOM_ASSIGNMENT);
    var output = new StringWriter();
    io.write(config, output);
    assertThat(io.read(new StringReader(output.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
    assertThat(new ConstructionHeuristicPhaseConfig().getConstructionHeuristicType()).isNull();
    assertThat(ConstructionHeuristicType.getBluePrintTypes())
        .doesNotContain(ConstructionHeuristicType.RANDOM_ASSIGNMENT);
  }

  @Test
  void usesStandardPhaseWithDedicatedPlacer() {
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config(random())).buildSolver();
    assertThat(solver.getPhaseList())
        .singleElement()
        .satisfies(
            phase -> {
              assertThat(phase).isExactlyInstanceOf(DefaultConstructionHeuristicPhase.class);
              assertThat(((DefaultConstructionHeuristicPhase<?>) phase).getEntityPlacer())
                  .isInstanceOf(RandomAssignmentEntityPlacer.class);
            });
  }

  @Test
  void moveWorkersRequireAnExplicitNoneOverride() {
    var inherited = config(random()).withMoveThreadCount("2");
    assertThatThrownBy(() -> SolverFactory.create(inherited).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RANDOM_ASSIGNMENT")
        .hasMessageContaining("moveThreadCount")
        .hasMessageContaining("NONE");
    assertThatCode(
            () ->
                SolverFactory.create(
                        config(random().withMoveThreadCount("NONE")).withMoveThreadCount("2"))
                    .buildSolver())
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> SolverFactory.create(config(random().withMoveThreadCount("2"))).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RANDOM_ASSIGNMENT");
  }

  @Test
  void explicitDisabledSettingsAreCompatible() {
    assertThatCode(
            () ->
                SolverFactory.create(
                        config(
                            random()
                                .withEntitySorterManner(EntitySorterManner.NONE)
                                .withValueSorterManner(ValueSorterManner.NONE)
                                .withNearbySelectionAutoConfigurationEnabled(false)))
                    .buildSolver())
        .doesNotThrowAnyException();
  }

  static Stream<Arguments> conflictingSettings() {
    return Stream.of(
        conflict(
            phase -> phase.withEntityPlacerConfig(new QueuedEntityPlacerConfig()),
            "entityPlacerConfig=QueuedEntityPlacerConfig("),
        conflict(
            phase -> phase.withMoveSelectorConfigList(List.of(new ChangeMoveSelectorConfig())),
            "moveSelectorConfigList=[ChangeMoveSelectorConfig("),
        conflict(
            phase -> phase.withForagerConfig(new ConstructionHeuristicForagerConfig()),
            "foragerConfig=ConstructionHeuristicForagerConfig()"),
        conflict(
            phase -> phase.withEntitySorterManner(EntitySorterManner.DESCENDING),
            "entitySorterManner=DESCENDING"),
        conflict(
            phase -> phase.withValueSorterManner(ValueSorterManner.ASCENDING),
            "valueSorterManner=ASCENDING"),
        conflict(
            phase -> phase.withNearbySelectionAutoConfigurationEnabled(true),
            "nearbySelectionAutoConfigurationEnabled=true"),
        conflict(phase -> phase.withNearbySelectionSize(5), "nearbySelectionSize=5"));
  }

  private static Arguments conflict(
      Consumer<ConstructionHeuristicPhaseConfig> configure, String diagnostic) {
    return Arguments.of(configure, diagnostic);
  }

  @ParameterizedTest
  @MethodSource("conflictingSettings")
  void incompatibleSettingsFailAtBuildTime(
      Consumer<ConstructionHeuristicPhaseConfig> configure, String diagnostic) {
    var phase = random();
    configure.accept(phase);
    assertThatThrownBy(() -> SolverFactory.create(config(phase)).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RANDOM_ASSIGNMENT")
        .hasMessageContaining(diagnostic)
        .hasMessageContaining("Remove");
  }

  @Test
  void configurationErrorIdentifiesEveryConflictingSetting() {
    var phase =
        random()
            .withEntitySorterManner(EntitySorterManner.DESCENDING)
            .withNearbySelectionAutoConfigurationEnabled(true)
            .withNearbySelectionSize(12);
    assertThatThrownBy(() -> SolverFactory.create(config(phase)).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("entitySorterManner=DESCENDING")
        .hasMessageContaining("nearbySelectionAutoConfigurationEnabled=true")
        .hasMessageContaining("nearbySelectionSize=12")
        .hasMessageNotContaining("valueSorterManner");
  }

  private static ConstructionHeuristicPhaseConfig random() {
    return new ConstructionHeuristicPhaseConfig()
        .withConstructionHeuristicType(ConstructionHeuristicType.RANDOM_ASSIGNMENT);
  }

  private static SolverConfig config(ConstructionHeuristicPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class)
        .withPhases(phase);
  }
}
