package ai.greycos.solver.core.impl.islandmodel;

import java.util.Objects;

/**
 * Configuration for the island model phase in Greycos. Controls the number of islands, migration
 * behavior, and global best synchronization.
 */
public class IslandModelConfig {

  /** Default number of islands to use. */
  public static final int DEFAULT_ISLAND_COUNT = 4;

  /** Default frequency of migration (number of steps between migrations). */
  public static final int DEFAULT_MIGRATION_FREQUENCY = 100;

  /**
   * Default frequency of receiving global best updates (number of steps between checks). This is
   * the frequency at which islands check and adopt the global best solution.
   */
  public static final int DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY = 50;

  /** Default timeout for migration operations (in milliseconds). */
  public static final long DEFAULT_MIGRATION_TIMEOUT = 100L;

  private int islandCount = DEFAULT_ISLAND_COUNT;
  private int migrationFrequency = DEFAULT_MIGRATION_FREQUENCY;
  private boolean enabled = false; // Default disabled for backward compatibility
  private boolean compareGlobalEnabled = true; // Default enabled for compare-to-global
  private int receiveGlobalUpdateFrequency = DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;
  private long migrationTimeout = DEFAULT_MIGRATION_TIMEOUT;

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

  /**
   * Gets the frequency at which islands receive and check global best updates. Islands will check
   * the global best solution every N steps and adopt it if better.
   *
   * @return the receive global update frequency (in steps)
   */
  public int getReceiveGlobalUpdateFrequency() {
    return receiveGlobalUpdateFrequency;
  }

  /**
   * Sets the frequency at which islands receive and check global best updates.
   *
   * @param receiveGlobalUpdateFrequency the frequency (must be at least 1)
   * @throws IllegalArgumentException if frequency is less than 1
   */
  public void setReceiveGlobalUpdateFrequency(int receiveGlobalUpdateFrequency) {
    if (receiveGlobalUpdateFrequency < 1) {
      throw new IllegalArgumentException(
          "Receive global update frequency ("
              + receiveGlobalUpdateFrequency
              + ") must be at least 1.");
    }
    this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
  }

  /**
   * Gets the timeout for migration operations (send and receive).
   *
   * @return the migration timeout in milliseconds
   */
  public long getMigrationTimeout() {
    return migrationTimeout;
  }

  /**
   * Sets the timeout for migration operations (send and receive).
   *
   * @param migrationTimeout the timeout in milliseconds (must be at least 1)
   * @throws IllegalArgumentException if timeout is less than 1
   */
  public void setMigrationTimeout(long migrationTimeout) {
    if (migrationTimeout < 1) {
      throw new IllegalArgumentException(
          "Migration timeout (" + migrationTimeout + ") must be at least 1.");
    }
    this.migrationTimeout = migrationTimeout;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private int islandCount = DEFAULT_ISLAND_COUNT;
    private int migrationFrequency = DEFAULT_MIGRATION_FREQUENCY;
    private boolean enabled = false;
    private boolean compareGlobalEnabled = true;
    private int receiveGlobalUpdateFrequency = DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;
    private long migrationTimeout = DEFAULT_MIGRATION_TIMEOUT;

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

    public Builder withReceiveGlobalUpdateFrequency(int receiveGlobalUpdateFrequency) {
      this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
      return this;
    }

    public Builder withMigrationTimeout(long migrationTimeout) {
      this.migrationTimeout = migrationTimeout;
      return this;
    }

    public IslandModelConfig build() {
      IslandModelConfig config = new IslandModelConfig();
      config.setIslandCount(islandCount);
      config.setMigrationFrequency(migrationFrequency);
      config.setEnabled(enabled);
      config.setCompareGlobalEnabled(compareGlobalEnabled);
      config.setReceiveGlobalUpdateFrequency(receiveGlobalUpdateFrequency);
      config.setMigrationTimeout(migrationTimeout);
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
        && receiveGlobalUpdateFrequency == that.receiveGlobalUpdateFrequency
        && migrationTimeout == that.migrationTimeout;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        islandCount,
        migrationFrequency,
        enabled,
        compareGlobalEnabled,
        receiveGlobalUpdateFrequency,
        migrationTimeout);
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
        + ", receiveGlobalUpdateFrequency="
        + receiveGlobalUpdateFrequency
        + ", migrationTimeout="
        + migrationTimeout
        + '}';
  }
}
