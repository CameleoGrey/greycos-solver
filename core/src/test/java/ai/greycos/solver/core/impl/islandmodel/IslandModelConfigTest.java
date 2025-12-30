package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IslandModelConfig}.
 *
 * <p>These tests verify that island model configuration works correctly, including
 * compare-to-global parameters.
 */
class IslandModelConfigTest {

  @Test
  void testDefaultValues() {
    var config = new IslandModelConfig();

    assertThat(config.getIslandCount()).isEqualTo(IslandModelConfig.DEFAULT_ISLAND_COUNT);
    assertThat(config.getMigrationRate()).isEqualTo(IslandModelConfig.DEFAULT_MIGRATION_RATE);
    assertThat(config.getMigrationFrequency())
        .isEqualTo(IslandModelConfig.DEFAULT_MIGRATION_FREQUENCY);
    assertThat(config.isEnabled()).isFalse();
    assertThat(config.isCompareGlobalEnabled()).isTrue();
    assertThat(config.getCompareGlobalFrequency())
        .isEqualTo(IslandModelConfig.DEFAULT_COMPARE_GLOBAL_FREQUENCY);
  }

  @Test
  void testCompareGlobalEnabled_default() {
    var config = new IslandModelConfig();
    assertThat(config.isCompareGlobalEnabled()).isTrue();
  }

  @Test
  void testCompareGlobalEnabled_setter() {
    var config = new IslandModelConfig();
    config.setCompareGlobalEnabled(false);
    assertThat(config.isCompareGlobalEnabled()).isFalse();

    config.setCompareGlobalEnabled(true);
    assertThat(config.isCompareGlobalEnabled()).isTrue();
  }

  @Test
  void testCompareGlobalFrequency_default() {
    var config = new IslandModelConfig();
    assertThat(config.getCompareGlobalFrequency()).isEqualTo(50);
  }

  @Test
  void testCompareGlobalFrequency_setter() {
    var config = new IslandModelConfig();
    config.setCompareGlobalFrequency(100);
    assertThat(config.getCompareGlobalFrequency()).isEqualTo(100);

    config.setCompareGlobalFrequency(25);
    assertThat(config.getCompareGlobalFrequency()).isEqualTo(25);
  }

  @Test
  void testCompareGlobalFrequency_validation() {
    var config = new IslandModelConfig();

    assertThatThrownBy(() -> config.setCompareGlobalFrequency(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Compare global frequency (0) must be at least 1.");

    assertThatThrownBy(() -> config.setCompareGlobalFrequency(-10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Compare global frequency (-10) must be at least 1.");
  }

  @Test
  void testBuilder_withCompareGlobalEnabled() {
    var config =
        IslandModelConfig.builder()
            .withIslandCount(4)
            .withMigrationRate(0.1)
            .withMigrationFrequency(100)
            .withCompareGlobalEnabled(false)
            .build();

    assertThat(config.getIslandCount()).isEqualTo(4);
    assertThat(config.getMigrationRate()).isEqualTo(0.1);
    assertThat(config.getMigrationFrequency()).isEqualTo(100);
    assertThat(config.isCompareGlobalEnabled()).isFalse();
  }

  @Test
  void testBuilder_withCompareGlobalFrequency() {
    var config =
        IslandModelConfig.builder()
            .withIslandCount(2)
            .withCompareGlobalEnabled(true)
            .withCompareGlobalFrequency(75)
            .build();

    assertThat(config.getIslandCount()).isEqualTo(2);
    assertThat(config.isCompareGlobalEnabled()).isTrue();
    assertThat(config.getCompareGlobalFrequency()).isEqualTo(75);
  }

  @Test
  void testBuilder_withAllCompareGlobalParameters() {
    var config =
        IslandModelConfig.builder()
            .withIslandCount(3)
            .withMigrationRate(0.2)
            .withMigrationFrequency(50)
            .withEnabled(true)
            .withCompareGlobalEnabled(true)
            .withCompareGlobalFrequency(25)
            .build();

    assertThat(config.getIslandCount()).isEqualTo(3);
    assertThat(config.getMigrationRate()).isEqualTo(0.2);
    assertThat(config.getMigrationFrequency()).isEqualTo(50);
    assertThat(config.isEnabled()).isTrue();
    assertThat(config.isCompareGlobalEnabled()).isTrue();
    assertThat(config.getCompareGlobalFrequency()).isEqualTo(25);
  }

  @Test
  void testEquals_withCompareGlobalParameters() {
    var config1 =
        IslandModelConfig.builder()
            .withIslandCount(2)
            .withCompareGlobalEnabled(true)
            .withCompareGlobalFrequency(50)
            .build();

    var config2 =
        IslandModelConfig.builder()
            .withIslandCount(2)
            .withCompareGlobalEnabled(true)
            .withCompareGlobalFrequency(50)
            .build();

    assertThat(config1).isEqualTo(config2);
    assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
  }

  @Test
  void testEquals_differentCompareGlobalEnabled() {
    var config1 =
        IslandModelConfig.builder().withIslandCount(2).withCompareGlobalEnabled(true).build();

    var config2 =
        IslandModelConfig.builder().withIslandCount(2).withCompareGlobalEnabled(false).build();

    assertThat(config1).isNotEqualTo(config2);
  }

  @Test
  void testEquals_differentCompareGlobalFrequency() {
    var config1 =
        IslandModelConfig.builder().withIslandCount(2).withCompareGlobalFrequency(50).build();

    var config2 =
        IslandModelConfig.builder().withIslandCount(2).withCompareGlobalFrequency(100).build();

    assertThat(config1).isNotEqualTo(config2);
  }

  @Test
  void testToString_includesCompareGlobalParameters() {
    var config =
        IslandModelConfig.builder()
            .withIslandCount(2)
            .withCompareGlobalEnabled(true)
            .withCompareGlobalFrequency(75)
            .build();

    var str = config.toString();
    assertThat(str).contains("islandCount=2");
    assertThat(str).contains("compareGlobalEnabled=true");
    assertThat(str).contains("compareGlobalFrequency=75");
  }
}
