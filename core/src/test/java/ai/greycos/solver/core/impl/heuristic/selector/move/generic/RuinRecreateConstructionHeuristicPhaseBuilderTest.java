package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.junit.jupiter.api.Test;

class RuinRecreateConstructionHeuristicPhaseBuilderTest {

  @Test
  void buildSingleThreaded() {
    var solverConfigPolicy =
        new HeuristicConfigPolicy.Builder<TestdataSolution>()
            .withSolutionDescriptor(TestdataSolution.buildSolutionDescriptor())
            .withInitializingScoreTrend(
                new InitializingScoreTrend(
                    new InitializingScoreTrendLevel[] {
                      InitializingScoreTrendLevel.ANY,
                      InitializingScoreTrendLevel.ANY,
                      InitializingScoreTrendLevel.ANY
                    }))
            .build();
    var constructionHeuristicConfig = mock(ConstructionHeuristicPhaseConfig.class);
    var builder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            solverConfigPolicy, constructionHeuristicConfig);
    var phase = builder.build();
    assertThat(phase.getEntityPlacer()).isSameAs(builder.getEntityPlacer());
  }

}
