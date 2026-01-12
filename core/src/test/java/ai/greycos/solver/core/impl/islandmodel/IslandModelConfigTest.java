package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IslandModelConfigTest {

  @Test
  void islandCountBelowMinimumThrows() {
    assertThatThrownBy(() -> new IslandModelConfig().setIslandCount(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Island count (0) must be at least 1");
  }

  @Test
  void islandCountNegativeThrows() {
    assertThatThrownBy(() -> new IslandModelConfig().setIslandCount(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Island count (-1) must be at least 1");
  }

  @Test
  void islandCountOneIsAccepted() {
    IslandModelConfig config = new IslandModelConfig();
    config.setIslandCount(1);
    assertThat(config.getIslandCount()).isEqualTo(1);
  }

  @Test
  void migrationFrequencyBelowMinimumThrows() {
    assertThatThrownBy(() -> new IslandModelConfig().setMigrationFrequency(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Migration frequency (0) must be at least 1");
  }

  @Test
  void migrationFrequencyOneIsAccepted() {
    IslandModelConfig config = new IslandModelConfig();
    config.setMigrationFrequency(1);
    assertThat(config.getMigrationFrequency()).isEqualTo(1);
  }

  @Test
  void receiveGlobalUpdateFrequencyBelowMinimumThrows() {
    assertThatThrownBy(() -> new IslandModelConfig().setReceiveGlobalUpdateFrequency(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Receive global update frequency (0) must be at least 1");
  }

  @Test
  void receiveGlobalUpdateFrequencyOneIsAccepted() {
    IslandModelConfig config = new IslandModelConfig();
    config.setReceiveGlobalUpdateFrequency(1);
    assertThat(config.getReceiveGlobalUpdateFrequency()).isEqualTo(1);
  }

  @Test
  void migrationTimeoutBelowMinimumThrows() {
    assertThatThrownBy(() -> new IslandModelConfig().setMigrationTimeout(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Migration timeout (0) must be at least 1");
  }

  @Test
  void migrationTimeoutOneIsAccepted() {
    IslandModelConfig config = new IslandModelConfig();
    config.setMigrationTimeout(1L);
    assertThat(config.getMigrationTimeout()).isEqualTo(1L);
  }

  @Test
  void builderCreatesValidConfig() {
    IslandModelConfig config =
        IslandModelConfig.builder()
            .withIslandCount(8)
            .withMigrationFrequency(50)
            .withEnabled(true)
            .withCompareGlobalEnabled(false)
            .withReceiveGlobalUpdateFrequency(25)
            .withMigrationTimeout(500L)
            .build();

    assertThat(config.getIslandCount()).isEqualTo(8);
    assertThat(config.getMigrationFrequency()).isEqualTo(50);
    assertThat(config.isEnabled()).isTrue();
    assertThat(config.isCompareGlobalEnabled()).isFalse();
    assertThat(config.getReceiveGlobalUpdateFrequency()).isEqualTo(25);
    assertThat(config.getMigrationTimeout()).isEqualTo(500L);
  }

  @Test
  void defaultValuesAreSensible() {
    IslandModelConfig config = new IslandModelConfig();

    assertThat(config.getIslandCount()).isEqualTo(IslandModelConfig.DEFAULT_ISLAND_COUNT);
    assertThat(config.getMigrationFrequency())
        .isEqualTo(IslandModelConfig.DEFAULT_MIGRATION_FREQUENCY);
    assertThat(config.getReceiveGlobalUpdateFrequency())
        .isEqualTo(IslandModelConfig.DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY);
    assertThat(config.getMigrationTimeout()).isEqualTo(IslandModelConfig.DEFAULT_MIGRATION_TIMEOUT);
    assertThat(config.isEnabled()).isFalse();
    assertThat(config.isCompareGlobalEnabled()).isTrue();
  }
}
