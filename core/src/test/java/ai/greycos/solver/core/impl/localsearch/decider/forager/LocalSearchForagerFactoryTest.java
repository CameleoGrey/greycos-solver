package ai.greycos.solver.core.impl.localsearch.decider.forager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Field;

import ai.greycos.solver.core.config.localsearch.decider.forager.FinalistPodiumType;
import ai.greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;

import org.junit.jupiter.api.Test;

class LocalSearchForagerFactoryTest {

  @Test
  void buildForager_builtInWithDefaults() {
    var config = new LocalSearchForagerConfig();
    var factory = LocalSearchForagerFactory.<Object>create(config);
    var forager = factory.buildForager();

    assertThat(forager).isExactlyInstanceOf(AcceptedLocalSearchForager.class);
    var acceptedForager = (AcceptedLocalSearchForager<Object>) forager;

    try {
      Field pickEarlyTypeField = AcceptedLocalSearchForager.class.getDeclaredField("pickEarlyType");
      pickEarlyTypeField.setAccessible(true);
      var pickEarlyType = (LocalSearchPickEarlyType) pickEarlyTypeField.get(acceptedForager);
      assertThat(pickEarlyType).isEqualTo(LocalSearchPickEarlyType.NEVER);

      Field acceptedCountLimitField =
          AcceptedLocalSearchForager.class.getDeclaredField("acceptedCountLimit");
      acceptedCountLimitField.setAccessible(true);
      var acceptedCountLimit = (int) acceptedCountLimitField.get(acceptedForager);
      assertThat(acceptedCountLimit).isEqualTo(Integer.MAX_VALUE);

      Field breakTieRandomlyField =
          AcceptedLocalSearchForager.class.getDeclaredField("breakTieRandomly");
      breakTieRandomlyField.setAccessible(true);
      var breakTieRandomly = (boolean) breakTieRandomlyField.get(acceptedForager);
      assertThat(breakTieRandomly).isTrue();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read field via reflection.", e);
    }
  }

  @Test
  void buildForager_builtInWithAllProperties() {
    var config =
        new LocalSearchForagerConfig()
            .withPickEarlyType(LocalSearchPickEarlyType.FIRST_BEST_SCORE_IMPROVING)
            .withAcceptedCountLimit(10)
            .withFinalistPodiumType(FinalistPodiumType.HIGHEST_SCORE)
            .withBreakTieRandomly(false);
    var factory = LocalSearchForagerFactory.<Object>create(config);
    var forager = factory.buildForager();

    assertThat(forager).isExactlyInstanceOf(AcceptedLocalSearchForager.class);
    var acceptedForager = (AcceptedLocalSearchForager<Object>) forager;

    try {
      Field pickEarlyTypeField = AcceptedLocalSearchForager.class.getDeclaredField("pickEarlyType");
      pickEarlyTypeField.setAccessible(true);
      var pickEarlyType = (LocalSearchPickEarlyType) pickEarlyTypeField.get(acceptedForager);
      assertThat(pickEarlyType).isEqualTo(LocalSearchPickEarlyType.FIRST_BEST_SCORE_IMPROVING);

      Field acceptedCountLimitField =
          AcceptedLocalSearchForager.class.getDeclaredField("acceptedCountLimit");
      acceptedCountLimitField.setAccessible(true);
      var acceptedCountLimit = (int) acceptedCountLimitField.get(acceptedForager);
      assertThat(acceptedCountLimit).isEqualTo(10);

      Field breakTieRandomlyField =
          AcceptedLocalSearchForager.class.getDeclaredField("breakTieRandomly");
      breakTieRandomlyField.setAccessible(true);
      var breakTieRandomly = (boolean) breakTieRandomlyField.get(acceptedForager);
      assertThat(breakTieRandomly).isFalse();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read field via reflection.", e);
    }
  }

  @Test
  void buildForager_customForagerBuiltInClass_throwsException() {
    var config = new LocalSearchForagerConfig().withForagerClass(AcceptedLocalSearchForager.class);
    var factory = LocalSearchForagerFactory.<Object>create(config);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> factory.buildForager())
        .withMessageContaining("is a built-in forager");
  }

  @Test
  void buildForager_customForagerNoValidConstructor_throwsException() {
    var config =
        new LocalSearchForagerConfig()
            .withForagerClass(CustomLocalSearchForagerNoValidConstructor.class);
    var factory = LocalSearchForagerFactory.<Object>create(config);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> factory.buildForager())
        .withMessageContaining("must have either a no-arg constructor");
  }

  @Test
  void buildForager_customForagerNoArgConstructor() {
    var customProperties = new java.util.HashMap<String, String>();
    customProperties.put("limit", "42");
    customProperties.put("randomTieBreaker", "false");

    var config =
        new LocalSearchForagerConfig()
            .withForagerClass(TestCustomLocalSearchForager.class)
            .withCustomProperties(customProperties);
    var factory = LocalSearchForagerFactory.<Object>create(config);
    var forager = factory.buildForager();

    assertThat(forager).isExactlyInstanceOf(TestCustomLocalSearchForager.class);
    var customForager = (TestCustomLocalSearchForager) forager;
    assertThat(customForager.getLimit()).isEqualTo(42);
    assertThat(customForager.isRandomTieBreaker()).isFalse();
  }

  @Test
  void buildForager_customForagerConfigConstructor() {
    var config =
        new LocalSearchForagerConfig()
            .withForagerClass(TestCustomLocalSearchForagerWithConfigConstructor.class)
            .withCustomProperties(java.util.Map.of("limit", "999", "randomTieBreaker", "false"));
    var factory = LocalSearchForagerFactory.<Object>create(config);
    var forager = factory.buildForager();

    assertThat(forager)
        .isExactlyInstanceOf(TestCustomLocalSearchForagerWithConfigConstructor.class);
    var customForager = (TestCustomLocalSearchForagerWithConfigConstructor) forager;
    assertThat(customForager.getConfig()).isSameAs(config);
    assertThat(customForager.getLimit()).isEqualTo(0); // Not injected, constructor gets config
    assertThat(customForager.isRandomTieBreaker()).isTrue(); // Not injected
  }

  @Test
  void buildForager_customForagerInvalidPropertySetter() {
    var customProperties = new java.util.HashMap<String, String>();
    customProperties.put("limit", "42");
    customProperties.put("nonExistentProperty", "someValue");

    var config =
        new LocalSearchForagerConfig()
            .withForagerClass(TestCustomLocalSearchForager.class)
            .withCustomProperties(customProperties);
    var factory = LocalSearchForagerFactory.<Object>create(config);
    var forager = factory.buildForager();

    assertThat(forager).isExactlyInstanceOf(TestCustomLocalSearchForager.class);
    var customForager = (TestCustomLocalSearchForager) forager;
    assertThat(customForager.getLimit()).isEqualTo(42); // Valid property injected
    assertThat(customForager.isRandomTieBreaker()).isTrue(); // Invalid setter silently ignored
  }

  // Test helper classes

  public static class TestCustomLocalSearchForager<Solution_>
      implements LocalSearchForager<Solution_> {

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
    public boolean supportsNeverEndingMoveSelector() {
      return false;
    }

    @Override
    public void addMove(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope<Solution_> moveScope) {}

    @Override
    public boolean isQuitEarly() {
      return false;
    }

    @Override
    public ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope<Solution_> pickMove(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {
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
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope<Solution_>
            phaseScope) {}

    @Override
    public void phaseEnded(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope<Solution_>
            phaseScope) {}

    @Override
    public void stepStarted(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {}

    @Override
    public void stepEnded(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {}
  }

  public static class TestCustomLocalSearchForagerWithConfigConstructor<Solution_>
      implements LocalSearchForager<Solution_> {

    private final LocalSearchForagerConfig config;
    private int limit = 0;
    private boolean randomTieBreaker = true;

    public TestCustomLocalSearchForagerWithConfigConstructor(LocalSearchForagerConfig config) {
      this.config = config;
    }

    public LocalSearchForagerConfig getConfig() {
      return config;
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
    public boolean supportsNeverEndingMoveSelector() {
      return false;
    }

    @Override
    public void addMove(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope<Solution_> moveScope) {}

    @Override
    public boolean isQuitEarly() {
      return false;
    }

    @Override
    public ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope<Solution_> pickMove(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {
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
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope<Solution_>
            phaseScope) {}

    @Override
    public void phaseEnded(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope<Solution_>
            phaseScope) {}

    @Override
    public void stepStarted(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {}

    @Override
    public void stepEnded(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {}
  }

  public static class CustomLocalSearchForagerNoValidConstructor<Solution_>
      implements LocalSearchForager<Solution_> {

    public CustomLocalSearchForagerNoValidConstructor(String invalidArg) {}

    @Override
    public boolean supportsNeverEndingMoveSelector() {
      return false;
    }

    @Override
    public void addMove(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope<Solution_> moveScope) {}

    @Override
    public boolean isQuitEarly() {
      return false;
    }

    @Override
    public ai.greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope<Solution_> pickMove(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {
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
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope<Solution_>
            phaseScope) {}

    @Override
    public void phaseEnded(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope<Solution_>
            phaseScope) {}

    @Override
    public void stepStarted(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {}

    @Override
    public void stepEnded(
        ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope<Solution_> stepScope) {}
  }
}
