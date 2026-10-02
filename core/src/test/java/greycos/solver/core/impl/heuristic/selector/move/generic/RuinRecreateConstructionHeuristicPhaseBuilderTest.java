package greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyMoveSelector;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfileResolver;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RuinRecreateConstructionHeuristicPhaseBuilderTest {

  @Test
  void buildSingleThreaded() {
    var solverConfigPolicy =
        new HeuristicConfigPolicy.Builder<TestdataSolution>()
            .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
            .withSolutionDescriptor(TestdataSolution.buildSolutionDescriptor())
            .withInitializingScoreTrend(
                new InitializingScoreTrend(
                    new InitializingScoreTrendLevel[] {
                      InitializingScoreTrendLevel.ANY,
                      InitializingScoreTrendLevel.ANY,
                      InitializingScoreTrendLevel.ANY
                    }))
            .build();
    assertThat(solverConfigPolicy.isConstructionHeuristicNearbyAutoConfigurationEnabled())
        .isFalse();
    var constructionHeuristicConfig = new ConstructionHeuristicPhaseConfig();
    var builder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            solverConfigPolicy, constructionHeuristicConfig);
    var phase = builder.build();
    assertThat(phase.getEntityPlacer()).isSameAs(builder.getEntityPlacer());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void nestedNearbySelectionRequiresItsOwnOptIn(Boolean enabled) {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var enclosingPolicy =
        new HeuristicConfigPolicy.Builder<TestdataSolution>()
            .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
            .withSolutionDescriptor(descriptor)
            .withInitializingScoreTrend(
                new InitializingScoreTrend(
                    new InitializingScoreTrendLevel[] {InitializingScoreTrendLevel.ANY}))
            .withConstructionHeuristicNearbyProfiles(
                ConstructionHeuristicNearbyProfileResolver.resolveGlobal(
                    descriptor, TestNearbyDistanceMeter.class))
            .withConstructionHeuristicNearbyAutoConfigurationEnabled(true)
            .build();
    var constructionHeuristicConfig =
        new ConstructionHeuristicPhaseConfig().withNearbySelectionSize(5);
    constructionHeuristicConfig.setNearbySelectionAutoConfigurationEnabled(enabled);

    var builder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            enclosingPolicy, constructionHeuristicConfig);

    assertThat(builder.getEntityPlacer().getCandidateMoveSelectors())
        .singleElement()
        .satisfies(
            selector ->
                assertThat(selector instanceof ConstructionHeuristicNearbyMoveSelector<?>)
                    .isEqualTo(Boolean.TRUE.equals(enabled)));
  }

  @Test
  void nestedPhaseRunsInTheEnclosingPhaseEnvironmentMode() {
    // A ruin & recreate move selector is built from its enclosing phase's config policy, not the
    // solver's,
    // so this policy stands for a local search phase which overrode the solver's environment mode.
    var phaseConfigPolicy =
        new HeuristicConfigPolicy.Builder<TestdataSolution>()
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withSolutionDescriptor(TestdataSolution.buildSolutionDescriptor())
            .withInitializingScoreTrend(
                new InitializingScoreTrend(
                    new InitializingScoreTrendLevel[] {
                      InitializingScoreTrendLevel.ANY,
                      InitializingScoreTrendLevel.ANY,
                      InitializingScoreTrendLevel.ANY
                    }))
            .build();
    var constructionHeuristicConfig = new ConstructionHeuristicPhaseConfig();
    var builder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            phaseConfigPolicy, constructionHeuristicConfig);
    // The nested construction heuristic is dragged along into the enclosing phase's mode.
    assertThat(builder.build().getEnvironmentMode()).isEqualTo(EnvironmentMode.FULL_ASSERT);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void buildMultiThreaded(boolean derived) {
    var solverConfigPolicy =
        new HeuristicConfigPolicy.Builder<TestdataSolution>()
            .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
            .withSolutionDescriptor(TestdataSolution.buildSolutionDescriptor())
            .withMoveThreadCount(2)
            .withInitializingScoreTrend(
                new InitializingScoreTrend(
                    new InitializingScoreTrendLevel[] {
                      InitializingScoreTrendLevel.ANY,
                      InitializingScoreTrendLevel.ANY,
                      InitializingScoreTrendLevel.ANY
                    }))
            .build();
    var constructionHeuristicConfig = new ConstructionHeuristicPhaseConfig();
    var builder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            solverConfigPolicy, constructionHeuristicConfig);
    var scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.isDerived()).thenReturn(derived);
    var phase = builder.ensureThreadSafe(scoreDirector).build();
    assertThat(phase.getEntityPlacer()).isNotSameAs(builder.getEntityPlacer());
  }

  @Test
  void nestedNoneStillCopiesForEnclosingMoveThreads() {
    var enclosingPolicy =
        new HeuristicConfigPolicy.Builder<TestdataSolution>()
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withSolutionDescriptor(TestdataSolution.buildSolutionDescriptor())
            .withMoveThreadCount(2)
            .withInitializingScoreTrend(
                new InitializingScoreTrend(
                    new InitializingScoreTrendLevel[] {InitializingScoreTrendLevel.ANY}))
            .build();
    var builder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            enclosingPolicy, new ConstructionHeuristicPhaseConfig().withMoveThreadCount("NONE"));
    var scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.isDerived()).thenReturn(true);

    var first = builder.ensureThreadSafe(scoreDirector);
    var second = builder.ensureThreadSafe(scoreDirector);
    assertThat(first).isNotSameAs(builder).isNotSameAs(second);
    assertThat(first.getEntityPlacer())
        .isNotSameAs(builder.getEntityPlacer())
        .isNotSameAs(second.getEntityPlacer());
    var element = new Object();
    first.withElementsToRecreate(List.of(element)).withElementsToRuin(Set.of(element));
    assertThat(second.elementsToRecreate).isNull();
    assertThat(second.elementsToRuin).isNull();
    assertThat(builder.elementsToRecreate).isNull();
    assertThat(builder.elementsToRuin).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2", "4"})
  void explicitNestedMoveThreadCountStillRequiresCopies(String nestedMoveThreadCount) {
    var enclosingPolicy =
        new HeuristicConfigPolicy.Builder<TestdataSolution>()
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withSolutionDescriptor(TestdataSolution.buildSolutionDescriptor())
            .withInitializingScoreTrend(
                new InitializingScoreTrend(
                    new InitializingScoreTrendLevel[] {InitializingScoreTrendLevel.ANY}))
            .build();
    var builder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            enclosingPolicy,
            new ConstructionHeuristicPhaseConfig().withMoveThreadCount(nestedMoveThreadCount));
    var scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.isDerived()).thenReturn(true);
    var copy = builder.ensureThreadSafe(scoreDirector);
    assertThat(copy).isNotSameAs(builder);
    assertThat(copy.getEntityPlacer()).isNotSameAs(builder.getEntityPlacer());
    when(scoreDirector.isDerived()).thenReturn(false);
    var anotherCopy = builder.ensureThreadSafe(scoreDirector);
    assertThat(anotherCopy).isNotSameAs(builder).isNotSameAs(copy);
    assertThat(anotherCopy.getEntityPlacer())
        .isNotSameAs(builder.getEntityPlacer())
        .isNotSameAs(copy.getEntityPlacer());
  }

  public static final class TestNearbyDistanceMeter
      implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {

    @Override
    public double getNearbyDistance(TestdataEntity origin, TestdataValue destination) {
      return 0;
    }
  }
}
