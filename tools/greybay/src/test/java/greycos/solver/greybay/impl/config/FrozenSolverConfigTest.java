package greycos.solver.greybay.impl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.api.solver.phase.PhaseCommandContext;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.RuntimeReconstructionProvider;

import org.junit.jupiter.api.Test;

class FrozenSolverConfigTest {
  @Test
  void freezesCallerPropertiesAndSelectorGraphAndCreatesIndependentCopies() {
    var base = configuration();
    var properties = new LinkedHashMap<>(Map.of("weight", "10"));
    base.getScoreDirectorFactoryConfig().setEasyScoreCalculatorCustomProperties(properties);
    var selector =
        new ChangeMoveSelectorConfig().withValueSelectorConfig(new ValueSelectorConfig("value"));
    var phase = new LocalSearchPhaseConfig();
    phase.setMoveSelectorConfig(selector);
    base.setPhaseConfigList(new ArrayList<>(List.of(phase)));
    var frozen = FrozenSolverConfig.capture(base, null);
    var identity = frozen.identity();

    properties.put("weight", "999");
    selector.getValueSelectorConfig().setVariableName("changed-by-caller");
    base.getPhaseConfigList().clear();

    var first = frozen.freshConfig();
    assertThat(first.getScoreDirectorFactoryConfig().getEasyScoreCalculatorCustomProperties())
        .containsEntry("weight", "10");
    assertThat(valueSelector(first).getVariableName()).isEqualTo("value");
    first
        .getScoreDirectorFactoryConfig()
        .getEasyScoreCalculatorCustomProperties()
        .put("weight", "700");
    valueSelector(first).setVariableName("changed-in-candidate");

    var second = frozen.freshConfig();
    assertThat(second.getScoreDirectorFactoryConfig().getEasyScoreCalculatorCustomProperties())
        .containsEntry("weight", "10");
    assertThat(valueSelector(second).getVariableName()).isEqualTo("value");
    assertThat(second.getPhaseConfigList().getFirst())
        .isNotSameAs(first.getPhaseConfigList().getFirst());
    assertThat(frozen.identity()).isEqualTo(identity);
  }

  @Test
  void canonicalIdentityIgnoresMapInsertionOrderAndEvaluationSeed() {
    var first = configuration();
    var properties = new LinkedHashMap<String, String>();
    properties.put("beta", "2");
    properties.put("alpha", "1");
    first.getScoreDirectorFactoryConfig().setEasyScoreCalculatorCustomProperties(properties);
    first.setRandomSeed(11L);
    var second = configuration();
    properties = new LinkedHashMap<>();
    properties.put("alpha", "1");
    properties.put("beta", "2");
    second.getScoreDirectorFactoryConfig().setEasyScoreCalculatorCustomProperties(properties);
    second.setRandomSeed(99L);
    assertThat(FrozenSolverConfig.canonicalIdentity(first, null))
        .isEqualTo(FrozenSolverConfig.canonicalIdentity(second, null));
    second.setRandomSeed(null);
    assertThat(FrozenSolverConfig.canonicalIdentity(first, null))
        .isEqualTo(FrozenSolverConfig.canonicalIdentity(second, null));
  }

  @Test
  void canonicalIdentityIgnoresSeedWhenItIsOnlyXmlElement() {
    assertThat(FrozenSolverConfig.canonicalIdentity(new SolverConfig().withRandomSeed(7L), null))
        .isEqualTo(FrozenSolverConfig.canonicalIdentity(new SolverConfig(), null));
  }

  @Test
  void canonicalIdentityRetainsSelectorAndPhaseOrder() {
    var first = configuration();
    var change = new ChangeMoveSelectorConfig();
    var swap = new SwapMoveSelectorConfig();
    var phase = new LocalSearchPhaseConfig();
    phase.setMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectors(change, swap));
    first.setPhaseConfigList(List.of(phase));
    var second = configuration();
    var reversed = new LocalSearchPhaseConfig();
    reversed.setMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectors(swap, change));
    second.setPhaseConfigList(List.of(reversed));
    assertThat(FrozenSolverConfig.canonicalIdentity(first, null))
        .isNotEqualTo(FrozenSolverConfig.canonicalIdentity(second, null));

    first.setPhaseConfigList(List.of(phase, reversed));
    second.setPhaseConfigList(List.of(reversed, phase));
    assertThat(FrozenSolverConfig.canonicalIdentity(first, null))
        .isNotEqualTo(FrozenSolverConfig.canonicalIdentity(second, null));
  }

  @Test
  void runtimeFieldsRequireIdentifiedReconstruction() {
    var config = configuration();
    config.setClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    assertThatThrownBy(() -> FrozenSolverConfig.capture(config, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clock")
        .hasMessageContaining("RuntimeReconstructionProvider");
    assertThatThrownBy(() -> FrozenSolverConfig.capture(config, provider("empty", ignored -> {})))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required fields");
  }

  @Test
  void providerRestoresRequiredRuntimeStateWithoutChangingXml() {
    var config = configuration();
    config.setClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var provider =
        provider(
            "clock-provider",
            restored -> restored.setClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
    var frozen = FrozenSolverConfig.capture(config, provider);
    var first = frozen.freshConfig();
    var second = frozen.freshConfig();
    assertThat(first.getClock().instant()).isEqualTo(Instant.EPOCH);
    assertThat(second.getClock().instant()).isEqualTo(Instant.EPOCH);
    assertThat(FrozenSolverConfig.canonicalIdentity(first, provider.identity()))
        .isEqualTo(frozen.identity());
  }

  @Test
  void providerChangingPersistedConfigurationFailsBeforeCaptureReturns() {
    var config = configuration();
    assertThatThrownBy(
            () ->
                FrozenSolverConfig.capture(
                    config, provider("bad-provider", restored -> restored.setMoveThreadCount("2"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("changed XML-visible");
  }

  @Test
  void providerChangingSeedIsRejectedEvenThoughCandidateIdentityIgnoresIt() {
    var config = configuration().withRandomSeed(11L);
    assertThatThrownBy(
            () ->
                FrozenSolverConfig.capture(
                    config,
                    provider("seed-changing-provider", restored -> restored.setRandomSeed(99L))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("XML-visible");
  }

  @Test
  void runtimeProviderIdentityParticipatesInCandidateIdentity() {
    var config = configuration();
    var first = FrozenSolverConfig.capture(config, provider("provider-a", ignored -> {}));
    var second = FrozenSolverConfig.capture(config, provider("provider-b", ignored -> {}));
    assertThat(first.identity()).isNotEqualTo(second.identity());
    assertThat(first.xml()).isEqualTo(second.xml());
    assertThat(
            FrozenSolverConfig.canonicalIdentity(
                config, new ComponentIdentity("provider-a", "1", "jar-a")))
        .isNotEqualTo(
            FrozenSolverConfig.canonicalIdentity(
                config, new ComponentIdentity("provider-a", "1", "jar-b")));
  }

  @Test
  void nestedRuntimeCommandsAreDetectedAndReconstructedAsFreshInstances() {
    var custom = new CustomPhaseConfig().withCustomPhaseCommands(new NoOpCommand());
    var island = new IslandModelPhaseConfig();
    island.setPhaseConfigList(List.of(custom));
    var config = configuration();
    config.setPhaseConfigList(List.of(island));
    assertThatThrownBy(() -> FrozenSolverConfig.capture(config, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("phase[0][0].customPhaseCommands");
    var frozen =
        FrozenSolverConfig.capture(
            config,
            provider(
                "commands",
                restored -> {
                  var restoredIsland =
                      (IslandModelPhaseConfig) restored.getPhaseConfigList().getFirst();
                  ((CustomPhaseConfig) restoredIsland.getPhaseConfigList().getFirst())
                      .setCustomPhaseCommandList(List.of(new NoOpCommand()));
                }));
    var first = (IslandModelPhaseConfig) frozen.freshConfig().getPhaseConfigList().getFirst();
    var second = (IslandModelPhaseConfig) frozen.freshConfig().getPhaseConfigList().getFirst();
    var firstCommand =
        ((CustomPhaseConfig) first.getPhaseConfigList().getFirst())
            .getCustomPhaseCommandList()
            .getFirst();
    var secondCommand =
        ((CustomPhaseConfig) second.getPhaseConfigList().getFirst())
            .getCustomPhaseCommandList()
            .getFirst();
    assertThat(firstCommand).isNotSameAs(secondCommand);
  }

  private static ValueSelectorConfig valueSelector(SolverConfig config) {
    return ((ChangeMoveSelectorConfig)
            ((LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst())
                .getMoveSelectorConfig())
        .getValueSelectorConfig();
  }

  private static SolverConfig configuration() {
    var config = new SolverConfig();
    config.setSolutionClass(TestdataSolution.class);
    config.setEntityClassList(List.of(TestdataEntity.class));
    var scoring = new ScoreDirectorFactoryConfig();
    scoring.setEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    config.setScoreDirectorFactoryConfig(scoring);
    return config;
  }

  private static RuntimeReconstructionProvider provider(
      String id, java.util.function.Consumer<SolverConfig> action) {
    return new RuntimeReconstructionProvider() {
      @Override
      public ComponentIdentity identity() {
        return new ComponentIdentity(id, "1");
      }

      @Override
      public void reconstruct(SolverConfig config) {
        action.accept(config);
      }
    };
  }

  private static final class NoOpCommand implements PhaseCommand<Object> {
    @Override
    public void changeWorkingSolution(PhaseCommandContext<Object> context) {}
  }
}
