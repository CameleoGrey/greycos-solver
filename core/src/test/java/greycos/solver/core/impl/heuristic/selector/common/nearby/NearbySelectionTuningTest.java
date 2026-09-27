package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NearbySelectionTuningTest {

  @Test
  void defaultDistributionHasNoImplicitSortLimit() {
    var config = new NearbySelectionConfig();

    assertThat(NearbySelectionTuning.calculateMaxNearbySortSize(config))
        .isEqualTo(Integer.MAX_VALUE);
  }

  @ParameterizedTest
  @EnumSource(NearbySelectionDistributionType.class)
  void unlimitedDistributionsHaveNoImplicitSortLimit(NearbySelectionDistributionType type) {
    var config = new NearbySelectionConfig().withNearbySelectionDistributionType(type);

    assertThat(NearbySelectionTuning.calculateMaxNearbySortSize(config))
        .isEqualTo(Integer.MAX_VALUE);
    assertThat(NearbyRandomFactory.create(config).buildNearbyRandom(true).getOverallSizeMaximum())
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void implicitAndExplicitDistributionsHaveEqualSupport() {
    assertEqualSupport(
        new NearbySelectionConfig().withBlockDistributionSizeMaximum(5000),
        NearbySelectionDistributionType.BLOCK_DISTRIBUTION);
    assertEqualSupport(
        new NearbySelectionConfig().withLinearDistributionSizeMaximum(5000),
        NearbySelectionDistributionType.LINEAR_DISTRIBUTION);
    assertEqualSupport(
        new NearbySelectionConfig().withParabolicDistributionSizeMaximum(5000),
        NearbySelectionDistributionType.PARABOLIC_DISTRIBUTION);
  }

  private static void assertEqualSupport(
      NearbySelectionConfig implicit, NearbySelectionDistributionType type) {
    var explicit = implicit.copyConfig().withNearbySelectionDistributionType(type);
    var implicitRandom = NearbyRandomFactory.create(implicit).buildNearbyRandom(true);
    var explicitRandom = NearbyRandomFactory.create(explicit).buildNearbyRandom(true);

    assertThat(implicitRandom).isEqualTo(explicitRandom);
    assertThat(NearbySelectionTuning.calculateMaxNearbySortSize(implicit))
        .isGreaterThanOrEqualTo(5000);
    assertThat(NearbySelectionTuning.calculateMaxNearbySortSize(explicit))
        .isEqualTo(NearbySelectionTuning.calculateMaxNearbySortSize(implicit));
    assertThat(implicitRandom.getOverallSizeMaximum()).isEqualTo(5000);
  }

  @Test
  void explicitSortLimitIsRetained() {
    var config = new NearbySelectionConfig().withMaxNearbySortSize(250);

    assertThat(NearbySelectionTuning.calculateMaxNearbySortSize(config)).isEqualTo(250);
  }

  @Test
  void rejectsNonPositiveExplicitSortLimit() {
    var config = new NearbySelectionConfig().withMaxNearbySortSize(0);

    assertThatThrownBy(() -> NearbySelectionTuning.calculateMaxNearbySortSize(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxNearbySortSize", "at least 1");
  }
}
