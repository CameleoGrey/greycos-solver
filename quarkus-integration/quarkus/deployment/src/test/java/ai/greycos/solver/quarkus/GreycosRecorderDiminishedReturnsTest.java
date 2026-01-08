package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.quarkus.config.DiminishedReturnsRuntimeConfig;
import ai.greycos.solver.quarkus.config.SolverRuntimeConfig;
import ai.greycos.solver.quarkus.config.TerminationRuntimeConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GreyCOSRecorderDiminishedReturnsTest {
  SolverConfig solverConfig;
  SolverRuntimeConfig solverRuntimeConfig;
  TerminationRuntimeConfig terminationRuntimeConfig;
  DiminishedReturnsRuntimeConfig diminishedReturnsRuntimeConfig;

  @BeforeEach
  void setUp() {
    solverConfig = new SolverConfig();
    solverRuntimeConfig = Mockito.mock(SolverRuntimeConfig.class);
    terminationRuntimeConfig = Mockito.mock(TerminationRuntimeConfig.class);
    diminishedReturnsRuntimeConfig = Mockito.mock(DiminishedReturnsRuntimeConfig.class);

    Mockito.when(solverRuntimeConfig.termination()).thenReturn(terminationRuntimeConfig);
    Mockito.when(terminationRuntimeConfig.diminishedReturns())
        .thenReturn(Optional.of(diminishedReturnsRuntimeConfig));
  }

  void assertNoDiminishedReturns(SolverConfig solverConfig) {
    assertThat(solverConfig.getTerminationConfig().getDiminishedReturnsConfig()).isNull();
    if (solverConfig.getPhaseConfigList() != null) {
      assertThat(solverConfig.getPhaseConfigList())
          .allMatch(
              phaseConfig -> phaseConfig.getTerminationConfig() == null,
              "has a null termination config");
    }
  }

  void assertDiminishedReturns(
      SolverConfig solverConfig, Duration slidingWindowDuration, Double minimumImprovementRatio) {
    assertThat(solverConfig.getTerminationConfig())
        .extracting(TerminationConfig::getDiminishedReturnsConfig)
        .isNotNull()
        .hasFieldOrPropertyWithValue("minimumImprovementRatio", minimumImprovementRatio)
        .hasFieldOrPropertyWithValue("slidingWindowDuration", slidingWindowDuration);
  }

  @Test
  void nothingSet() {
    GreyCOSRecorder.updateSolverConfigWithRuntimeProperties(solverConfig, solverRuntimeConfig);
    assertNoDiminishedReturns(solverConfig);
  }

  @Test
  void onlyEnabledSet() {
    Mockito.when(diminishedReturnsRuntimeConfig.enabled()).thenReturn(Optional.of(true));
    GreyCOSRecorder.updateSolverConfigWithRuntimeProperties(solverConfig, solverRuntimeConfig);
    assertDiminishedReturns(solverConfig, null, null);
  }

  @Test
  void onlySlidingWindowSet() {
    Mockito.when(diminishedReturnsRuntimeConfig.slidingWindowDuration())
        .thenReturn(Optional.ofNullable(Duration.ofMinutes(30)));
    GreyCOSRecorder.updateSolverConfigWithRuntimeProperties(solverConfig, solverRuntimeConfig);
    assertDiminishedReturns(solverConfig, Duration.ofMinutes(30), null);
  }

  @Test
  void onlyMinimumImprovementRatioSet() {
    Mockito.when(diminishedReturnsRuntimeConfig.minimumImprovementRatio())
        .thenReturn(OptionalDouble.of(123.0));
    GreyCOSRecorder.updateSolverConfigWithRuntimeProperties(solverConfig, solverRuntimeConfig);
    assertDiminishedReturns(solverConfig, null, 123.0);
  }

  @Test
  void minimumImprovementRatioAndSlidingWindowSet() {
    Mockito.when(diminishedReturnsRuntimeConfig.slidingWindowDuration())
        .thenReturn(Optional.ofNullable(Duration.ofMinutes(30)));
    Mockito.when(diminishedReturnsRuntimeConfig.minimumImprovementRatio())
        .thenReturn(OptionalDouble.of(123.0));
    GreyCOSRecorder.updateSolverConfigWithRuntimeProperties(solverConfig, solverRuntimeConfig);
    assertDiminishedReturns(solverConfig, Duration.ofMinutes(30), 123.0);
  }

  @Test
  void disabledAndMinimumImprovementRatioAndSlidingWindowSet() {
    Mockito.when(diminishedReturnsRuntimeConfig.enabled()).thenReturn(Optional.of(false));
    Mockito.when(diminishedReturnsRuntimeConfig.slidingWindowDuration())
        .thenReturn(Optional.ofNullable(Duration.ofMinutes(30)));
    Mockito.when(diminishedReturnsRuntimeConfig.minimumImprovementRatio())
        .thenReturn(OptionalDouble.of(123.0));
    GreyCOSRecorder.updateSolverConfigWithRuntimeProperties(solverConfig, solverRuntimeConfig);
    assertNoDiminishedReturns(solverConfig);
  }

  @Test
  void disabledAndPhasesConfigured() {
    solverConfig.setPhaseConfigList(
        List.of(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig()));
    Mockito.when(diminishedReturnsRuntimeConfig.enabled()).thenReturn(Optional.of(false));

    GreyCOSRecorder.updateSolverConfigWithRuntimeProperties(solverConfig, solverRuntimeConfig);
    assertNoDiminishedReturns(solverConfig);
  }
}
