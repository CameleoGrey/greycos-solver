package ai.greycos.solver.core.config.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;

import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import ai.greycos.solver.core.config.phase.PhaseConfig;
import ai.greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for island model phase.
 *
 * <p>The island model runs multiple independent island agents in parallel, each running same phases
 * independently. Agents periodically exchange their best solutions through migration in a ring
 * topology.
 *
 * <p>This is an opt-in feature that provides:
 *
 * <ul>
 *   <li>Enhanced solution quality through migration
 *   <li>Near-linear horizontal scaling
 *   <li>Fault tolerance (if one island fails, others continue)
 * </ul>
 */
@XmlType
public class IslandModelPhaseConfig extends PhaseConfig<IslandModelPhaseConfig> {

  public static final String XML_ELEMENT_NAME = "islandModel";

  /** Default number of islands to use. */
  public static final int DEFAULT_ISLAND_COUNT = 4;

  /**
   * Default migration rate (proportion of solution to migrate). This is currently a placeholder for
   * future expansion.
   */
  public static final double DEFAULT_MIGRATION_RATE = 0.1;

  /** Default frequency of migration (number of steps between migrations). */
  public static final int DEFAULT_MIGRATION_FREQUENCY = 100;

  // Warning: all fields are null (and not defaulted) because they can be inherited
  // and also because input config file should match output config file

  @XmlElement(name = "islandCount")
  private Integer islandCount = null;

  @XmlElement(name = "migrationRate")
  private Double migrationRate = null;

  @XmlElement(name = "migrationFrequency")
  private Integer migrationFrequency = null;

  @XmlElements({
    @XmlElement(
        name = ConstructionHeuristicPhaseConfig.XML_ELEMENT_NAME,
        type = ConstructionHeuristicPhaseConfig.class),
    @XmlElement(name = CustomPhaseConfig.XML_ELEMENT_NAME, type = CustomPhaseConfig.class),
    @XmlElement(
        name = ExhaustiveSearchPhaseConfig.XML_ELEMENT_NAME,
        type = ExhaustiveSearchPhaseConfig.class),
    @XmlElement(
        name = LocalSearchPhaseConfig.XML_ELEMENT_NAME,
        type = LocalSearchPhaseConfig.class),
    @XmlElement(
        name = PartitionedSearchPhaseConfig.XML_ELEMENT_NAME,
        type = PartitionedSearchPhaseConfig.class)
  })
  private List<PhaseConfig<?>> phaseConfigList = null;

  // ************************************************************************
  // Constructors and simple getters/setters
  // ************************************************************************

  /**
   * Returns number of islands to use in island model.
   *
   * @return number of islands, or null if not specified
   */
  public @Nullable Integer getIslandCount() {
    return islandCount;
  }

  /**
   * Sets number of islands to use.
   *
   * @param islandCount number of islands (must be at least 1)
   */
  public void setIslandCount(@Nullable Integer islandCount) {
    this.islandCount = islandCount;
  }

  /**
   * Returns migration rate. Currently a placeholder for future expansion where partial solutions
   * might be migrated.
   *
   * @return migration rate between 0 and 1, or null if not specified
   */
  public @Nullable Double getMigrationRate() {
    return migrationRate;
  }

  /**
   * Sets migration rate.
   *
   * @param migrationRate migration rate between 0 and 1
   */
  public void setMigrationRate(@Nullable Double migrationRate) {
    this.migrationRate = migrationRate;
  }

  /**
   * Returns migration frequency (number of steps between migrations).
   *
   * @return migration frequency, or null if not specified
   */
  public @Nullable Integer getMigrationFrequency() {
    return migrationFrequency;
  }

  /**
   * Sets migration frequency.
   *
   * @param migrationFrequency number of steps between migrations (must be at least 1)
   */
  public void setMigrationFrequency(@Nullable Integer migrationFrequency) {
    this.migrationFrequency = migrationFrequency;
  }

  /**
   * Returns phase configurations to run on each island.
   *
   * @return list of phase configurations, or null if not specified
   */
  public @Nullable List<PhaseConfig<?>> getPhaseConfigList() {
    return phaseConfigList;
  }

  /**
   * Sets phase configurations to run on each island.
   *
   * @param phaseConfigList list of phase configurations
   */
  public void setPhaseConfigList(@Nullable List<PhaseConfig<?>> phaseConfigList) {
    this.phaseConfigList = phaseConfigList;
  }

  /**
   * Returns a single phase configuration for backward compatibility.
   *
   * @return first phase configuration, or null if not specified
   * @deprecated Use {@link #getPhaseConfigList()} instead
   */
  @Deprecated
  public @Nullable PhaseConfig<?> getPhaseConfig() {
    return phaseConfigList != null && !phaseConfigList.isEmpty() ? phaseConfigList.get(0) : null;
  }

  /**
   * Sets a single phase configuration for backward compatibility.
   *
   * @param phaseConfig phase configuration
   * @deprecated Use {@link #setPhaseConfigList(List)} instead
   */
  @Deprecated
  public void setPhaseConfig(@Nullable PhaseConfig<?> phaseConfig) {
    if (phaseConfig == null) {
      this.phaseConfigList = null;
    } else {
      this.phaseConfigList = new ArrayList<>();
      this.phaseConfigList.add(phaseConfig);
    }
  }

  // ************************************************************************
  // With methods
  // ************************************************************************

  /**
   * Sets number of islands and returns this config.
   *
   * @param islandCount number of islands
   * @return this config
   */
  public @NonNull IslandModelPhaseConfig withIslandCount(int islandCount) {
    this.islandCount = islandCount;
    return this;
  }

  /**
   * Sets migration rate and returns this config.
   *
   * @param migrationRate migration rate
   * @return this config
   */
  public @NonNull IslandModelPhaseConfig withMigrationRate(double migrationRate) {
    this.migrationRate = migrationRate;
    return this;
  }

  /**
   * Sets migration frequency and returns this config.
   *
   * @param migrationFrequency migration frequency
   * @return this config
   */
  public @NonNull IslandModelPhaseConfig withMigrationFrequency(int migrationFrequency) {
    this.migrationFrequency = migrationFrequency;
    return this;
  }

  /**
   * Sets phase configurations and returns this config.
   *
   * @param phaseConfigList list of phase configurations
   * @return this config
   */
  public @NonNull IslandModelPhaseConfig withPhaseConfigList(
      @NonNull List<PhaseConfig<?>> phaseConfigList) {
    this.phaseConfigList = phaseConfigList;
    return this;
  }

  /**
   * Sets a single phase configuration and returns this config.
   *
   * @param phaseConfig phase configuration
   * @return this config
   * @deprecated Use {@link #withPhaseConfigList(List)} instead
   */
  @Deprecated
  public @NonNull IslandModelPhaseConfig withPhaseConfig(@NonNull PhaseConfig<?> phaseConfig) {
    if (this.phaseConfigList == null) {
      this.phaseConfigList = new ArrayList<>();
    } else {
      this.phaseConfigList.clear();
    }
    this.phaseConfigList.add(phaseConfig);
    return this;
  }

  @Override
  public @NonNull IslandModelPhaseConfig inherit(@NonNull IslandModelPhaseConfig inheritedConfig) {
    super.inherit(inheritedConfig);
    islandCount =
        ConfigUtils.inheritOverwritableProperty(islandCount, inheritedConfig.getIslandCount());
    migrationRate =
        ConfigUtils.inheritOverwritableProperty(migrationRate, inheritedConfig.getMigrationRate());
    migrationFrequency =
        ConfigUtils.inheritOverwritableProperty(
            migrationFrequency, inheritedConfig.getMigrationFrequency());
    phaseConfigList =
        ConfigUtils.inheritOverwritableProperty(
            phaseConfigList, inheritedConfig.getPhaseConfigList());
    return this;
  }

  @Override
  public @NonNull IslandModelPhaseConfig copyConfig() {
    return new IslandModelPhaseConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (terminationConfig != null) {
      terminationConfig.visitReferencedClasses(classVisitor);
    }
    if (phaseConfigList != null) {
      for (PhaseConfig<?> config : phaseConfigList) {
        config.visitReferencedClasses(classVisitor);
      }
    }
  }
}
