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

  /** Creates a new island model configuration with default values. */
  public IslandModelConfig() {}

  /**
   * Creates a new island model configuration with specified values.
   *
   * @param islandCount number of islands (must be positive)
   * @param migrationFrequency number of steps between migrations (must be positive)
   */
  public IslandModelConfig(int islandCount, int migrationFrequency) {
    setIslandCount(islandCount);
    setMigrationFrequency(migrationFrequency);
  }

  /**
   * Returns the number of islands to use in the island model.
   *
   * @return number of islands (at least 1)
   */
  public int getIslandCount() {
    return islandCount;
  }

  /**
   * Sets the number of islands to use.
   *
   * @param islandCount number of islands (must be at least 1)
   * @throws IllegalArgumentException if islandCount is less than 1
   */
  public void setIslandCount(int islandCount) {
    if (islandCount < 1) {
      throw new IllegalArgumentException("Island count (" + islandCount + ") must be at least 1.");
    }
    this.islandCount = islandCount;
  }

  /**
   * Returns the migration frequency (number of steps between migrations).
   *
   * @return number of steps between migrations (at least 1)
   */
  public int getMigrationFrequency() {
    return migrationFrequency;
  }

  /**
   * Sets the migration frequency.
   *
   * @param migrationFrequency number of steps between migrations (must be at least 1)
   * @throws IllegalArgumentException if migrationFrequency is less than 1
   */
  public void setMigrationFrequency(int migrationFrequency) {
    if (migrationFrequency < 1) {
      throw new IllegalArgumentException(
          "Migration frequency (" + migrationFrequency + ") must be at least 1.");
    }
    this.migrationFrequency = migrationFrequency;
  }

  /**
   * Returns whether the island model is enabled.
   *
   * @return true if island model is enabled, false otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether the island model is enabled. Default is false to maintain backward compatibility.
   *
   * @param enabled true to enable island model, false to disable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns whether comparing to global best is enabled.
   *
   * <p>When enabled, agents periodically check the shared global best solution and adopt it if it's
   * better than their current best. This provides faster convergence and better solution quality.
   *
   * @return true if compare-to-global is enabled, false otherwise
   */
  public boolean isCompareGlobalEnabled() {
    return compareGlobalEnabled;
  }

  /**
   * Sets whether comparing to global best is enabled.
   *
   * @param compareGlobalEnabled true to enable compare-to-global, false to disable
   */
  public void setCompareGlobalEnabled(boolean compareGlobalEnabled) {
    this.compareGlobalEnabled = compareGlobalEnabled;
  }

  /**
   * Returns the frequency of comparing to global best (number of steps between checks).
   *
   * @return number of steps between global best comparisons (at least 1)
   */
  public int getCompareGlobalFrequency() {
    return compareGlobalFrequency;
  }

  /**
   * Sets the frequency of comparing to global best.
   *
   * @param compareGlobalFrequency number of steps between comparisons (must be at least 1)
   * @throws IllegalArgumentException if compareGlobalFrequency is less than 1
   */
  public void setCompareGlobalFrequency(int compareGlobalFrequency) {
    if (compareGlobalFrequency < 1) {
      throw new IllegalArgumentException(
          "Compare global frequency (" + compareGlobalFrequency + ") must be at least 1.");
    }
    this.compareGlobalFrequency = compareGlobalFrequency;
  }

  /**
   * Creates a builder for IslandModelConfig.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for creating IslandModelConfig instances. */
  public static class Builder {
    private int islandCount = DEFAULT_ISLAND_COUNT;
    private int migrationFrequency = DEFAULT_MIGRATION_FREQUENCY;
    private boolean enabled = false;
    private boolean compareGlobalEnabled = true;
    private int compareGlobalFrequency = DEFAULT_COMPARE_GLOBAL_FREQUENCY;

    /**
     * Sets the number of islands.
     *
     * @param islandCount number of islands
     * @return this builder
     */
    public Builder withIslandCount(int islandCount) {
      this.islandCount = islandCount;
      return this;
    }

    /**
     * Sets the migration frequency.
     *
     * @param migrationFrequency migration frequency
     * @return this builder
     */
    public Builder withMigrationFrequency(int migrationFrequency) {
      this.migrationFrequency = migrationFrequency;
      return this;
    }

    /**
     * Sets whether the island model is enabled.
     *
     * @param enabled true to enable, false to disable
     * @return this builder
     */
    public Builder withEnabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * Sets whether comparing to global best is enabled.
     *
     * @param compareGlobalEnabled true to enable compare-to-global, false to disable
     * @return this builder
     */
    public Builder withCompareGlobalEnabled(boolean compareGlobalEnabled) {
      this.compareGlobalEnabled = compareGlobalEnabled;
      return this;
    }

    /**
     * Sets the frequency of comparing to global best.
     *
     * @param compareGlobalFrequency number of steps between comparisons
     * @return this builder
     */
    public Builder withCompareGlobalFrequency(int compareGlobalFrequency) {
      this.compareGlobalFrequency = compareGlobalFrequency;
      return this;
    }

    /**
     * Builds the IslandModelConfig instance.
     *
     * @return a new IslandModelConfig with the configured values
     */
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
