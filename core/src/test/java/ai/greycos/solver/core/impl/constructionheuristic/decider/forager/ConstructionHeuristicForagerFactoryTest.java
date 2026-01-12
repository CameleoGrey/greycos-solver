package ai.greycos.solver.core.impl.constructionheuristic.decider.forager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import ai.greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import ai.greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.score.trend.InitializingScoreTrend;

import org.junit.jupiter.api.Test;

class ConstructionHeuristicForagerFactoryTest {

  @Test
  void buildForager_builtInWithDefaults() {
    var config = new ConstructionHeuristicForagerConfig();
    var factory = ConstructionHeuristicForagerFactory.<Object>create(config);

    var configPolicy = mock(HeuristicConfigPolicy.class);
    when(configPolicy.getInitializingScoreTrend())
        .thenReturn(InitializingScoreTrend.buildUniformTrend(InitializingScoreTrendLevel.ANY, 1));

    var forager = factory.buildForager(configPolicy);

    assertThat(forager).isExactlyInstanceOf(DefaultConstructionHeuristicForager.class);
    var defaultForager = (DefaultConstructionHeuristicForager<Object>) forager;

    try {
      Field pickEarlyTypeField =
          DefaultConstructionHeuristicForager.class.getDeclaredField("pickEarlyType");
      pickEarlyTypeField.setAccessible(true);
      var pickEarlyType =
          (ConstructionHeuristicPickEarlyType) pickEarlyTypeField.get(defaultForager);
      assertThat(pickEarlyType).isEqualTo(ConstructionHeuristicPickEarlyType.NEVER);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read field via reflection.", e);
    }
  }

  @Test
  void buildForager_customForagerBuiltInClass_throwsException() {
    var config =
        new ConstructionHeuristicForagerConfig()
            .withForagerClass(DefaultConstructionHeuristicForager.class);
    var factory = ConstructionHeuristicForagerFactory.<Object>create(config);
    var configPolicy = mock(HeuristicConfigPolicy.class);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> factory.buildForager(configPolicy))
        .withMessageContaining("is a built-in forager");
  }

  @Test
  void buildForager_customForagerNoValidConstructor_throwsException() {
    var config =
        new ConstructionHeuristicForagerConfig()
            .withForagerClass(CustomConstructionHeuristicForagerNoValidConstructor.class);
    var factory = ConstructionHeuristicForagerFactory.<Object>create(config);
    var configPolicy = mock(HeuristicConfigPolicy.class);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> factory.buildForager(configPolicy))
        .withMessageContaining("must have either a no-arg constructor");
  }

  @Test
  void buildForager_customForagerNoArgConstructor() {
    var customProperties = new java.util.HashMap<String, String>();
    customProperties.put("limit", "42");
    customProperties.put("randomTieBreaker", "false");

    var config =
        new ConstructionHeuristicForagerConfig()
            .withForagerClass(TestCustomConstructionHeuristicForager.class)
            .withCustomProperties(customProperties);
    var factory = ConstructionHeuristicForagerFactory.<Object>create(config);
    var configPolicy = mock(HeuristicConfigPolicy.class);

    var forager = factory.buildForager(configPolicy);

    assertThat(forager).isExactlyInstanceOf(TestCustomConstructionHeuristicForager.class);
    var customForager = (TestCustomConstructionHeuristicForager) forager;
    assertThat(customForager.getLimit()).isEqualTo(42);
    assertThat(customForager.isRandomTieBreaker()).isFalse();
  }

  @Test
  void buildForager_customForagerConfigConstructor() {
    var config =
        new ConstructionHeuristicForagerConfig()
            .withForagerClass(TestCustomConstructionHeuristicForagerWithConfigConstructor.class)
            .withCustomProperties(java.util.Map.of("limit", "999", "randomTieBreaker", "false"));
    var factory = ConstructionHeuristicForagerFactory.<Object>create(config);
    var configPolicy = mock(HeuristicConfigPolicy.class);

    var forager = factory.buildForager(configPolicy);

    assertThat(forager)
        .isExactlyInstanceOf(TestCustomConstructionHeuristicForagerWithConfigConstructor.class);
    var customForager = (TestCustomConstructionHeuristicForagerWithConfigConstructor) forager;
    assertThat(customForager.getConfigPolicy()).isSameAs(configPolicy);
    assertThat(customForager.getLimit()).isEqualTo(0); // Not injected, constructor gets config
    assertThat(customForager.isRandomTieBreaker()).isTrue(); // Not injected
  }

  // Test helper classes

  public static class TestCustomConstructionHeuristicForager<Solution_>
      implements ConstructionHeuristicForager<Solution_> {

    private int limit = 0;
    private boolean randomTieBreaker = true;

    public int getLimit() {
      return limit;
    }

    public void setLimit(String limit) {
      this.limit = Integer.parseInt(limit);
    }

    public boolean isRandomTieBreaker() {
      return randomTieBreaker;
    }

    public void setRandomTieBreaker(String randomTieBreaker) {
      this.randomTieBreaker = Boolean.parseBoolean(randomTieBreaker);
    }

    @Override
    public void addMove(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope<
                Solution_>
            moveScope) {}

    @Override
    public boolean isQuitEarly() {
      return false;
    }

    @Override
    public ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope<
            Solution_>
        pickMove(
            ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                    Solution_>
                stepScope) {
      return null;
    }

    @Override
    public void solvingStarted(
        ai.greycos.solver.core.impl.solver.scope.SolverScope<Solution_> solverScope) {}

    @Override
    public void solvingEnded(
        ai.greycos.solver.core.impl.solver.scope.SolverScope<Solution_> solverScope) {}

    @Override
    public void phaseStarted(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope<
                Solution_>
            phaseScope) {}

    @Override
    public void phaseEnded(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope<
                Solution_>
            phaseScope) {}

    @Override
    public void stepStarted(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                Solution_>
            stepScope) {}

    @Override
    public void stepEnded(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                Solution_>
            stepScope) {}
  }

  public static class TestCustomConstructionHeuristicForagerWithConfigConstructor<Solution_>
      implements ConstructionHeuristicForager<Solution_> {

    private final HeuristicConfigPolicy<Solution_> configPolicy;
    private int limit = 0;
    private boolean randomTieBreaker = true;

    public TestCustomConstructionHeuristicForagerWithConfigConstructor(
        HeuristicConfigPolicy<Solution_> configPolicy) {
      this.configPolicy = configPolicy;
    }

    public HeuristicConfigPolicy<Solution_> getConfigPolicy() {
      return configPolicy;
    }

    public int getLimit() {
      return limit;
    }

    public void setLimit(String limit) {
      this.limit = Integer.parseInt(limit);
    }

    public boolean isRandomTieBreaker() {
      return randomTieBreaker;
    }

    public void setRandomTieBreaker(String randomTieBreaker) {
      this.randomTieBreaker = Boolean.parseBoolean(randomTieBreaker);
    }

    @Override
    public void addMove(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope<
                Solution_>
            moveScope) {}

    @Override
    public boolean isQuitEarly() {
      return false;
    }

    @Override
    public ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope<
            Solution_>
        pickMove(
            ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                    Solution_>
                stepScope) {
      return null;
    }

    @Override
    public void solvingStarted(
        ai.greycos.solver.core.impl.solver.scope.SolverScope<Solution_> solverScope) {}

    @Override
    public void solvingEnded(
        ai.greycos.solver.core.impl.solver.scope.SolverScope<Solution_> solverScope) {}

    @Override
    public void phaseStarted(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope<
                Solution_>
            phaseScope) {}

    @Override
    public void phaseEnded(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope<
                Solution_>
            phaseScope) {}

    @Override
    public void stepStarted(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                Solution_>
            stepScope) {}

    @Override
    public void stepEnded(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                Solution_>
            stepScope) {}
  }

  public static class CustomConstructionHeuristicForagerNoValidConstructor<Solution_>
      implements ConstructionHeuristicForager<Solution_> {

    public CustomConstructionHeuristicForagerNoValidConstructor(String invalidArg) {}

    @Override
    public void addMove(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope<
                Solution_>
            moveScope) {}

    @Override
    public boolean isQuitEarly() {
      return false;
    }

    @Override
    public ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope<
            Solution_>
        pickMove(
            ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                    Solution_>
                stepScope) {
      return null;
    }

    @Override
    public void solvingStarted(
        ai.greycos.solver.core.impl.solver.scope.SolverScope<Solution_> solverScope) {}

    @Override
    public void solvingEnded(
        ai.greycos.solver.core.impl.solver.scope.SolverScope<Solution_> solverScope) {}

    @Override
    public void phaseStarted(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope<
                Solution_>
            phaseScope) {}

    @Override
    public void phaseEnded(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope<
                Solution_>
            phaseScope) {}

    @Override
    public void stepStarted(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                Solution_>
            stepScope) {}

    @Override
    public void stepEnded(
        ai.greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope<
                Solution_>
            stepScope) {}
  }
}
