package ai.greycos.solver.core.impl.islandmodel;

import java.util.Objects;

/**
 * Configuration for the island model phase in Greycos. Controls the number of islands, migration
 * behavior, and related parameters.
 */
public class IslandModelConfig {

  /** Default number of islands to use. */
  public static final int DEFAULT_ISLAND_COUNT = 4;

  /** Default frequency of migration (number of steps between migrations). */
  public static final int DEFAULT_MIGRATION_FREQUENCY = 100;

  /** Default frequency of comparing to global best (number of steps between checks). */
  public static final int DEFAULT_COMPARE_GLOBAL_FREQUENCY = 50;

  private int islandCount = DEFAULT_ISLAND_COUNT;
  private int migrationFrequency = DEFAULT_MIGRATION_FREQUENCY;
  private boolean enabled = false; // Default disabled for backward compatibility
  private boolean compareGlobalEnabled = true; // Default enabled for compare-to-global
  private int compareGlobalFrequency = DEFAULT_COMPARE_GLOBAL_FREQUENCY;

  public IslandModelConfig() {}

  public IslandModelConfig(int islandCount, int migrationFrequency) {
    setIslandCount(islandCount);
    setMigrationFrequency(migrationFrequency);
  }

  public int getIslandCount() {
    return islandCount;
  }

  public void setIslandCount(int islandCount) {
    if (islandCount < 1) {
      throw new IllegalArgumentException("Island count (" + islandCount + ") must be at least 1.");
    }
    this.islandCount = islandCount;
  }

  public int getMigrationFrequency() {
    return migrationFrequency;
  }

  public void setMigrationFrequency(int migrationFrequency) {
    if (migrationFrequency < 1) {
      throw new IllegalArgumentException(
          "Migration frequency (" + migrationFrequency + ") must be at least 1.");
    }
    this.migrationFrequency = migrationFrequency;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isCompareGlobalEnabled() {
    return compareGlobalEnabled;
  }

  public void setCompareGlobalEnabled(boolean compareGlobalEnabled) {
    this.compareGlobalEnabled = compareGlobalEnabled;
  }

  public int getCompareGlobalFrequency() {
    return compareGlobalFrequency;
  }

  public void setCompareGlobalFrequency(int compareGlobalFrequency) {
    if (compareGlobalFrequency < 1) {
      throw new IllegalArgumentException(
          "Compare global frequency (" + compareGlobalFrequency + ") must be at least 1.");
    }
    this.compareGlobalFrequency = compareGlobalFrequency;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private int islandCount = DEFAULT_ISLAND_COUNT;
    private int migrationFrequency = DEFAULT_MIGRATION_FREQUENCY;
    private boolean enabled = false;
    private boolean compareGlobalEnabled = true;
    private int compareGlobalFrequency = DEFAULT_COMPARE_GLOBAL_FREQUENCY;

    public Builder withIslandCount(int islandCount) {
      this.islandCount = islandCount;
      return this;
    }

    public Builder withMigrationFrequency(int migrationFrequency) {
      this.migrationFrequency = migrationFrequency;
      return this;
    }

    public Builder withEnabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder withCompareGlobalEnabled(boolean compareGlobalEnabled) {
      this.compareGlobalEnabled = compareGlobalEnabled;
      return this;
    }

    public Builder withCompareGlobalFrequency(int compareGlobalFrequency) {
      this.compareGlobalFrequency = compareGlobalFrequency;
      return this;
    }

    public IslandModelConfig build() {
      IslandModelConfig config = new IslandModelConfig();
      config.setIslandCount(islandCount);
      config.setMigrationFrequency(migrationFrequency);
      config.setEnabled(enabled);
      config.setCompareGlobalEnabled(compareGlobalEnabled);
      config.setCompareGlobalFrequency(compareGlobalFrequency);
      return config;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IslandModelConfig that = (IslandModelConfig) o;
    return islandCount == that.islandCount
        && migrationFrequency == that.migrationFrequency
        && enabled == that.enabled
        && compareGlobalEnabled == that.compareGlobalEnabled
        && compareGlobalFrequency == that.compareGlobalFrequency;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        islandCount,
        migrationFrequency,
        enabled,
        compareGlobalEnabled,
        compareGlobalFrequency);
  }

  @Override
  public String toString() {
    return "IslandModelConfig{"
        + "islandCount="
        + islandCount
        + ", migrationFrequency="
        + migrationFrequency
        + ", enabled="
        + enabled
        + ", compareGlobalEnabled="
        + compareGlobalEnabled
        + ", compareGlobalFrequency="
        + compareGlobalFrequency
        + '}';
  }
}
