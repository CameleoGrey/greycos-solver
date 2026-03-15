package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Iterator;
import java.util.Random;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;
import ai.greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.entity.nearby.NearEntityNearbyEntitySelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.ClassInstanceCache;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

/**
 * Integration test for Nearby Selection feature.
 *
 * <p>This test validates complete integration of nearby selection with value selectors and entity
 * selectors, ensuring all components work together correctly.
 */
class NearbySelectionIntegrationTest {

  private static final double DISTANCE_TOLERANCE = 0.001;

  @Test
  void testNearbySelectionConfigValidation() {
    // Test missing origin selector
    var config = new NearbySelectionConfig();
    assertThrows(
        IllegalArgumentException.class,
        () -> config.validateNearby(SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL));

    // Test with origin entity selector - must have mimicSelectorRef
    var configWithEntity =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("entitySelector"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            configWithEntity.validateNearby(
                SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM));

    // Test with origin entity selector and distance meter
    var configWithEntityAndMeter =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("entitySelector"))
            .withNearbyDistanceMeterClass(TestDistanceMeter.class);
    assertDoesNotThrow(
        () ->
            configWithEntityAndMeter.validateNearby(
                SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM));

    // Test with origin value selector - must have mimicSelectorRef
    var configWithValue =
        new NearbySelectionConfig()
            .withOriginValueSelectorConfig(
                new ValueSelectorConfig().withMimicSelectorRef("valueSelector"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            configWithValue.validateNearby(SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM));

    // Test with origin value selector and distance meter
    var configWithValueAndMeter =
        new NearbySelectionConfig()
            .withOriginValueSelectorConfig(
                new ValueSelectorConfig().withMimicSelectorRef("valueSelector"))
            .withNearbyDistanceMeterClass(TestDistanceMeter.class);
    assertDoesNotThrow(
        () ->
            configWithValueAndMeter.validateNearby(
                SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM));

    // Test missing distance meter
    var configNoMeter =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("entitySelector"));
    assertThrows(
        IllegalArgumentException.class,
        () -> configNoMeter.validateNearby(SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM));
  }

  @Test
  void testNearbyRandomFactory() {
    var random = new Random(42);

    // Test block distribution
    var blockConfig =
        new NearbySelectionConfig()
            .withNearbySelectionDistributionType(NearbySelectionDistributionType.BLOCK_DISTRIBUTION)
            .withBlockDistributionSizeMinimum(5)
            .withBlockDistributionSizeMaximum(20)
            .withBlockDistributionSizeRatio(0.5)
            .withBlockDistributionUniformDistributionProbability(0.1);

    var blockRandom = NearbyRandomFactory.create(blockConfig).buildNearbyRandom(true);
    assertTrue(blockRandom instanceof BlockDistributionNearbyRandom);
    assertEquals(20, ((BlockDistributionNearbyRandom) blockRandom).getOverallSizeMaximum());

    // Test linear distribution
    var linearConfig =
        new NearbySelectionConfig()
            .withNearbySelectionDistributionType(
                NearbySelectionDistributionType.LINEAR_DISTRIBUTION)
            .withLinearDistributionSizeMaximum(100);

    var linearRandom = NearbyRandomFactory.create(linearConfig).buildNearbyRandom(true);
    assertTrue(linearRandom instanceof LinearDistributionNearbyRandom);
    assertEquals(100, ((LinearDistributionNearbyRandom) linearRandom).getOverallSizeMaximum());

    // Test parabolic distribution
    var parabolicConfig =
        new NearbySelectionConfig()
            .withNearbySelectionDistributionType(
                NearbySelectionDistributionType.PARABOLIC_DISTRIBUTION)
            .withParabolicDistributionSizeMaximum(50);

    var parabolicRandom = NearbyRandomFactory.create(parabolicConfig).buildNearbyRandom(true);
    assertTrue(parabolicRandom instanceof ParabolicDistributionNearbyRandom);
    assertEquals(50, ((ParabolicDistributionNearbyRandom) parabolicRandom).getOverallSizeMaximum());

    // Test beta distribution
    var betaConfig =
        new NearbySelectionConfig()
            .withNearbySelectionDistributionType(NearbySelectionDistributionType.BETA_DISTRIBUTION)
            .withBetaDistributionAlpha(1.5)
            .withBetaDistributionBeta(3.0);

    var betaRandom = NearbyRandomFactory.create(betaConfig).buildNearbyRandom(true);
    assertTrue(betaRandom instanceof BetaDistributionNearbyRandom);
    assertEquals(
        Integer.MAX_VALUE, ((BetaDistributionNearbyRandom) betaRandom).getOverallSizeMaximum());

    // Test default (linear with no max)
    var defaultConfig = new NearbySelectionConfig();
    var defaultRandom = NearbyRandomFactory.create(defaultConfig).buildNearbyRandom(true);
    assertTrue(defaultRandom instanceof LinearDistributionNearbyRandom);
    assertEquals(
        Integer.MAX_VALUE,
        ((LinearDistributionNearbyRandom) defaultRandom).getOverallSizeMaximum());
  }

  @Test
  void testNearbyEntitySelectorIntegration() {
    var configPolicy = mock(HeuristicConfigPolicy.class);
    var entityDescriptor = mock(EntityDescriptor.class);
    var classInstanceCache = mock(ClassInstanceCache.class);
    var workingRandom = new Random(42);

    when(configPolicy.getClassInstanceCache()).thenReturn(classInstanceCache);
    when(configPolicy.getRandom()).thenReturn(workingRandom);
    when(entityDescriptor.getEntityClass()).thenReturn(TestEntity.class);
    when(classInstanceCache.newInstance(any(), any(), eq(TestDistanceMeter.class)))
        .thenReturn(new TestDistanceMeter());

    // Test with RANDOM order (distribution parameters allowed)
    var nearbyConfigRandom =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("originEntitySelector"))
            .withNearbyDistanceMeterClass(TestDistanceMeter.class)
            .withNearbySelectionDistributionType(
                NearbySelectionDistributionType.LINEAR_DISTRIBUTION)
            .withLinearDistributionSizeMaximum(10);

    var originEntitySelector =
        SelectorTestUtils.mockReplayingEntitySelector(
            entityDescriptor, new TestEntity("A"), new TestEntity("B"), new TestEntity("C"));

    var childEntitySelector =
        SelectorTestUtils.mockEntitySelector(
            entityDescriptor, new TestEntity("1"), new TestEntity("2"), new TestEntity("3"));

    var nearbyEntitySelectorRandom =
        new NearEntityNearbyEntitySelector<>(
            childEntitySelector,
            originEntitySelector,
            new TestDistanceMeter(),
            NearbyRandomFactory.create(nearbyConfigRandom).buildNearbyRandom(true),
            true);

    // Verify it's a nearby selector
    assertNotNull(nearbyEntitySelectorRandom);
    assertTrue(nearbyEntitySelectorRandom.toString().contains("NearEntityNearbyEntitySelector"));

    // Test original iteration with ORIGINAL order (no distribution parameters)
    var nearbyConfigOriginal =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(
                new EntitySelectorConfig().withMimicSelectorRef("originEntitySelector"))
            .withNearbyDistanceMeterClass(TestDistanceMeter.class);

    var nearbyEntitySelectorOriginal =
        new NearEntityNearbyEntitySelector<>(
            childEntitySelector,
            originEntitySelector,
            new TestDistanceMeter(),
            NearbyRandomFactory.create(nearbyConfigOriginal).buildNearbyRandom(false),
            false);

    InnerScoreDirector<TestEntity, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolverScope<TestEntity> solverScope =
        SelectorTestUtils.solvingStarted(
            nearbyEntitySelectorOriginal, scoreDirector, workingRandom);
    AbstractPhaseScope<TestEntity> phaseScope = PlannerTestUtils.delegatingPhaseScope(solverScope);
    nearbyEntitySelectorOriginal.phaseStarted(phaseScope);
    AbstractStepScope<TestEntity> stepScope = PlannerTestUtils.delegatingStepScope(phaseScope);
    nearbyEntitySelectorOriginal.stepStarted(stepScope);

    var originalIterator = nearbyEntitySelectorOriginal.iterator();
    assertTrue(originalIterator.hasNext());
    var entity = originalIterator.next();
    assertNotNull(entity);

    nearbyEntitySelectorOriginal.stepEnded(stepScope);
    nearbyEntitySelectorOriginal.phaseEnded(phaseScope);
    nearbyEntitySelectorOriginal.solvingEnded(solverScope);
  }

  @Test
  void testNearbyDistanceMatrix() {
    var distanceMeter = new TestDistanceMeter();
    var destinations =
        java.util.List.of(new TestEntity("A"), new TestEntity("B"), new TestEntity("C"));
    NearbyDistanceMatrix<TestEntity, Object> matrix =
        new NearbyDistanceMatrix<>(
            distanceMeter,
            3,
            origin -> (Iterator<Object>) (Iterator<?>) destinations.iterator(),
            o -> 3);

    // Test distance calculations
    var originA = new TestEntity("A");
    var originB = new TestEntity("B");
    var originC = new TestEntity("C");

    assertEquals(0.0, distanceMeter.getNearbyDistance(originA, originA));
    assertEquals(1.0, distanceMeter.getNearbyDistance(originA, originB));
    assertEquals(2.0, distanceMeter.getNearbyDistance(originA, originC));

    // Test sorted destinations retrieval
    var destA = matrix.getDestination(originA, 0);
    var destB = matrix.getDestination(originA, 1);
    var destC = matrix.getDestination(originA, 2);

    // Compare by name since these are different instances
    assertEquals("A", ((TestEntity) destA).getName());
    assertEquals("B", ((TestEntity) destB).getName());
    assertEquals("C", ((TestEntity) destC).getName());
  }

  @Test
  void testConfigurationInheritance() {
    var parentConfig =
        new NearbySelectionConfig()
            .withNearbyDistanceMeterClass(TestDistanceMeter.class)
            .withNearbySelectionDistributionType(NearbySelectionDistributionType.BLOCK_DISTRIBUTION)
            .withBlockDistributionSizeMinimum(5)
            .withBlockDistributionSizeMaximum(20);

    var childConfig = new NearbySelectionConfig();
    var inherited = childConfig.inherit(parentConfig);

    // Verify inheritance
    assertEquals(TestDistanceMeter.class, inherited.getNearbyDistanceMeterClass());
    assertEquals(
        NearbySelectionDistributionType.BLOCK_DISTRIBUTION,
        inherited.getNearbySelectionDistributionType());
    assertEquals(5, inherited.getBlockDistributionSizeMinimum());
    assertEquals(20, inherited.getBlockDistributionSizeMaximum());
  }

  @Test
  void testNearbySelectionWithMimicSelector() {
    var configPolicy = mock(HeuristicConfigPolicy.class);
    var entityDescriptor = mock(EntityDescriptor.class);
    var classInstanceCache = mock(ClassInstanceCache.class);
    var workingRandom = new Random(42);

    when(configPolicy.getClassInstanceCache()).thenReturn(classInstanceCache);
    when(configPolicy.getRandom()).thenReturn(workingRandom);
    when(entityDescriptor.getEntityClass()).thenReturn(TestEntity.class);
    when(classInstanceCache.newInstance(any(), any(), eq(TestDistanceMeter.class)))
        .thenReturn(new TestDistanceMeter());

    // Create a mimic selector reference (simulates mimicSelectorRef)
    var entitySelectorConfig = new EntitySelectorConfig().withMimicSelectorRef("entitySelectorRef");

    var nearbyConfig =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(entitySelectorConfig)
            .withNearbyDistanceMeterClass(TestDistanceMeter.class)
            .withNearbySelectionDistributionType(
                NearbySelectionDistributionType.LINEAR_DISTRIBUTION)
            .withLinearDistributionSizeMaximum(10);

    // This should NOT throw an exception because we have mimicSelectorRef
    assertDoesNotThrow(
        () -> nearbyConfig.validateNearby(SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM));
  }

  // Test entities for testing
  static class TestEntity {
    private final String name;

    public TestEntity(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  // Test distance meter for testing
  static class TestDistanceMeter implements NearbyDistanceMeter<TestEntity, Object> {

    @Override
    public double getNearbyDistance(TestEntity origin, Object destination) {
      if (origin == null || destination == null) {
        return Double.MAX_VALUE;
      }
      // Get the name string from destination (could be TestEntity or String)
      String destinationName;
      if (destination instanceof TestEntity) {
        destinationName = ((TestEntity) destination).getName();
      } else if (destination instanceof String) {
        destinationName = (String) destination;
      } else {
        return Double.MAX_VALUE;
      }
      // Simple distance based on name difference
      int diff = Math.abs(origin.getName().charAt(0) - destinationName.charAt(0));
      return (double) diff;
    }
  }
}
