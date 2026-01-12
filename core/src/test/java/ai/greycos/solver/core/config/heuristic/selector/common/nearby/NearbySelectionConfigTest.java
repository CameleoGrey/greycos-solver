package ai.greycos.solver.core.config.heuristic.selector.common.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ai.greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.BetaDistributionNearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.BlockDistributionNearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.LinearDistributionNearbyRandom;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandomFactory;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.ParabolicDistributionNearbyRandom;

import org.junit.jupiter.api.Test;

/** Tests for {@link NearbySelectionConfig}. */
class NearbySelectionConfigTest {

  private static final String ENTITY_SELECTOR_ID = "entitySelector";

  // ************************************************************************
  // Origin selector validation tests
  // ************************************************************************

  @Test
  void withNoOriginSelectorConfig() {
    var nearbySelectionConfig = new NearbySelectionConfig();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                nearbySelectionConfig.validateNearby(
                    SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL))
        .withMessageContainingAll(
            "lacks an origin selector config",
            "originEntitySelectorConfig",
            "originSubListSelectorConfig",
            "originValueSelectorConfig");
  }

  @Test
  void withMultipleOriginSelectorConfigs() {
    var nearbySelectionConfig =
        new NearbySelectionConfig()
            .withOriginEntitySelectorConfig(new EntitySelectorConfig())
            .withOriginSubListSelectorConfig(
                new ai.greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig())
            .withOriginValueSelectorConfig(new ValueSelectorConfig());
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                nearbySelectionConfig.validateNearby(
                    SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL))
        .withMessageContainingAll(
            "has multiple origin selector configs",
            "originEntitySelectorConfig",
            "originSubListSelectorConfig",
            "originValueSelectorConfig");
  }

  @Test
  void originEntitySelectorWithoutMimicSelectorRef() {
    var nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setOriginEntitySelectorConfig(new EntitySelectorConfig());
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                nearbySelectionConfig.validateNearby(
                    SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL))
        .withMessageContaining("mimicSelectorRef");
  }

  @Test
  void originValueSelectorWithoutMimicSelectorRef() {
    var nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setOriginValueSelectorConfig(new ValueSelectorConfig());
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                nearbySelectionConfig.validateNearby(
                    SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL))
        .withMessageContaining("mimicSelectorRef");
  }

  // ************************************************************************
  // Distance meter validation tests
  // ************************************************************************

  @Test
  void withNoDistanceMeter() {
    var entitySelectorConfig = new EntitySelectorConfig().withId(ENTITY_SELECTOR_ID);
    var nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setOriginEntitySelectorConfig(
        EntitySelectorConfig.newMimicSelectorConfig(entitySelectorConfig.getId()));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                nearbySelectionConfig.validateNearby(
                    entitySelectorConfig.getCacheType(), entitySelectorConfig.getSelectionOrder()))
        .withMessageContaining("nearbyDistanceMeterClass");
  }

  // ************************************************************************
  // Selection order validation tests
  // ************************************************************************

  @Test
  void withWrongSelectionOrder() {
    var entitySelectorConfig =
        new EntitySelectorConfig()
            .withId(ENTITY_SELECTOR_ID)
            .withSelectionOrder(SelectionOrder.SORTED);
    var nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setOriginEntitySelectorConfig(
        EntitySelectorConfig.newMimicSelectorConfig(entitySelectorConfig.getId()));
    nearbySelectionConfig.setNearbyDistanceMeterClass(TestDistanceMeter.class);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                nearbySelectionConfig.validateNearby(
                    entitySelectorConfig.getCacheType(), entitySelectorConfig.getSelectionOrder()))
        .withMessageContaining("resolvedSelectionOrder");
  }

  // ************************************************************************
  // Cache type validation tests
  // ************************************************************************

  @Test
  void withCachedResolvedCachedType() {
    var entitySelectorConfig =
        new EntitySelectorConfig()
            .withId(ENTITY_SELECTOR_ID)
            .withSelectionOrder(SelectionOrder.ORIGINAL)
            .withCacheType(SelectionCacheType.STEP);
    var nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setOriginEntitySelectorConfig(
        EntitySelectorConfig.newMimicSelectorConfig(entitySelectorConfig.getId()));
    nearbySelectionConfig.setNearbyDistanceMeterClass(TestDistanceMeter.class);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                nearbySelectionConfig.validateNearby(
                    entitySelectorConfig.getCacheType(), entitySelectorConfig.getSelectionOrder()))
        .withMessageContaining("cached");
  }

  // ************************************************************************
  // NearbyRandom building tests
  // ************************************************************************

  @Test
  void buildNearbyRandomWithNoRandomSelection() {
    var nearbySelectionConfig = buildNearbySelectionConfig();

    assertThat(NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(false)).isNull();
  }

  @Test
  void buildNearbyRandomWithNoRandomSelectionAndWithDistribution() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    nearbySelectionConfig.setBlockDistributionSizeMinimum(1);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(false))
        .withMessageContainingAll("randomSelection", "distribution");
  }

  // ************************************************************************
  // Multiple distribution conflict tests
  // ************************************************************************

  @Test
  void buildNearbyRandomWithBlockAndLinear() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    nearbySelectionConfig.setBlockDistributionSizeMinimum(1);
    nearbySelectionConfig.setLinearDistributionSizeMaximum(100);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true))
        .withMessageContaining("distribution");
  }

  @Test
  void buildNearbyRandomWithBlockAndParabolic() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    nearbySelectionConfig.setBlockDistributionSizeMinimum(1);
    nearbySelectionConfig.setParabolicDistributionSizeMaximum(100);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true))
        .withMessageContaining("distribution");
  }

  @Test
  void buildNearbyRandomWithBlockAndBeta() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    nearbySelectionConfig.setBlockDistributionSizeMinimum(1);
    nearbySelectionConfig.setBetaDistributionAlpha(0.5);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true))
        .withMessageContaining("distribution");
  }

  @Test
  void buildNearbyRandomWithLinearAndParabolic() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    nearbySelectionConfig.setLinearDistributionSizeMaximum(100);
    nearbySelectionConfig.setParabolicDistributionSizeMaximum(100);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true))
        .withMessageContaining("distribution");
  }

  @Test
  void buildNearbyRandomWithLinearAndBeta() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    nearbySelectionConfig.setLinearDistributionSizeMaximum(100);
    nearbySelectionConfig.setBetaDistributionAlpha(0.5);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true))
        .withMessageContaining("distribution");
  }

  @Test
  void buildNearbyRandomWithParabolicAndBeta() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    nearbySelectionConfig.setParabolicDistributionSizeMaximum(100);
    nearbySelectionConfig.setBetaDistributionAlpha(0.5);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true))
        .withMessageContaining("distribution");
  }

  // ************************************************************************
  // Distribution-specific tests
  // ************************************************************************

  @Test
  void buildNearbyRandomWithBlockDistribution() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    int minimum = 2;
    nearbySelectionConfig.setBlockDistributionSizeMinimum(minimum);
    int maximum = 3;
    nearbySelectionConfig.setBlockDistributionSizeMaximum(maximum);
    double sizeRatio = 0.2;
    nearbySelectionConfig.setBlockDistributionSizeRatio(sizeRatio);
    double probability = 0.1;
    nearbySelectionConfig.setBlockDistributionUniformDistributionProbability(probability);

    var nearbyRandom = NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true);

    assertThat(nearbyRandom)
        .usingRecursiveComparison()
        .isEqualTo(new BlockDistributionNearbyRandom(minimum, maximum, sizeRatio, probability));
  }

  @Test
  void buildNearbyRandomWithLinearDistribution() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    int maximum = 2;
    nearbySelectionConfig.setLinearDistributionSizeMaximum(maximum);

    var nearbyRandom = NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true);

    assertThat(nearbyRandom)
        .usingRecursiveComparison()
        .isEqualTo(new LinearDistributionNearbyRandom(maximum));
  }

  @Test
  void buildNearbyRandomWithParabolicDistribution() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    int maximum = 2;
    nearbySelectionConfig.setParabolicDistributionSizeMaximum(maximum);

    var nearbyRandom = NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true);

    assertThat(nearbyRandom)
        .usingRecursiveComparison()
        .isEqualTo(new ParabolicDistributionNearbyRandom(maximum));
  }

  @Test
  void buildNearbyRandomWithBetaDistribution() {
    var nearbySelectionConfig = buildNearbySelectionConfig();
    double alpha = 0.1;
    nearbySelectionConfig.setBetaDistributionAlpha(alpha);
    double beta = 0.2;
    nearbySelectionConfig.setBetaDistributionBeta(beta);

    var nearbyRandom = NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true);

    assertThat(nearbyRandom.getClass()).isEqualTo(BetaDistributionNearbyRandom.class);
  }

  @Test
  void buildNearbyRandomWithDefaultDistribution() {
    var nearbySelectionConfig = buildNearbySelectionConfig();

    var nearbyRandom = NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(true);

    assertThat(nearbyRandom)
        .usingRecursiveComparison()
        .isEqualTo(new LinearDistributionNearbyRandom(Integer.MAX_VALUE));
  }

  // ************************************************************************
  // Helper methods
  // ************************************************************************

  private NearbySelectionConfig buildNearbySelectionConfig() {
    var entitySelectorConfig =
        new EntitySelectorConfig()
            .withId(ENTITY_SELECTOR_ID)
            .withSelectionOrder(SelectionOrder.RANDOM)
            .withCacheType(SelectionCacheType.JUST_IN_TIME);
    var nearbySelectionConfig = new NearbySelectionConfig();
    nearbySelectionConfig.setOriginEntitySelectorConfig(
        EntitySelectorConfig.newMimicSelectorConfig(entitySelectorConfig.getId()));
    nearbySelectionConfig.setNearbyDistanceMeterClass(TestDistanceMeter.class);
    return nearbySelectionConfig;
  }

  // Test distance meter class
  public static class TestDistanceMeter
      implements ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter<
          Object, Object> {

    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      return 0.0;
    }
  }
}
