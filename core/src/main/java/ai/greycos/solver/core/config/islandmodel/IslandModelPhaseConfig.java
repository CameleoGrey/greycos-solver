package ai.greycos.solver.core.config.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;

import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.chained.SubChainChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.chained.SubChainSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.chained.TailChainSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchType;
import ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.greycos.solver.core.config.phase.NoChangePhaseConfig;
import ai.greycos.solver.core.config.phase.PhaseConfig;
import ai.greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for island model phase.
 *
 * <p>The island model runs multiple independent island agents in parallel, each running local
 * search independently. Agents periodically exchange their best solutions through migration in a
 * ring topology.
 *
 * <p>This is an opt-in feature that provides:
 *
 * <ul>
 *   <li>Enhanced solution quality through migration
 *   <li>Near-linear horizontal scaling
 *   <li>Fault tolerance (if one island fails, others continue)
 * </ul>
 *
 * <p>IslandModelPhaseConfig includes all local search configuration options (move selector,
 * acceptor, forager, etc.) that each island uses. Each island runs the same local search
 * configuration, but with independent random seeds and solution states.
 */
@XmlType(
    propOrder = {
            "islandCount",
            "migrationFrequency",
            "compareGlobalEnabled",
            "receiveGlobalUpdateFrequency",
            "compareGlobalFrequency",
            "migrationTimeout",
            "localSearchType",
            "phaseConfigList",
            "acceptorConfig",
            "foragerConfig",
            "moveSelectorConfig",
            "moveThreadCount",
    })
public class IslandModelPhaseConfig extends PhaseConfig<IslandModelPhaseConfig> {

  // Local search configuration fields (inherited from LocalSearchPhaseConfig behavior)
  protected LocalSearchType localSearchType = null;

  @XmlElements({
    @XmlElement(
        name = CartesianProductMoveSelectorConfig.XML_ELEMENT_NAME,
        type = CartesianProductMoveSelectorConfig.class),
    @XmlElement(
        name = ChangeMoveSelectorConfig.XML_ELEMENT_NAME,
        type = ChangeMoveSelectorConfig.class),
    @XmlElement(
        name = ListChangeMoveSelectorConfig.XML_ELEMENT_NAME,
        type = ListChangeMoveSelectorConfig.class),
    @XmlElement(
        name = ListSwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = ListSwapMoveSelectorConfig.class),
    @XmlElement(
        name = MoveIteratorFactoryConfig.XML_ELEMENT_NAME,
        type = MoveIteratorFactoryConfig.class),
    @XmlElement(name = MoveListFactoryConfig.XML_ELEMENT_NAME, type = MoveListFactoryConfig.class),
    @XmlElement(
        name = PillarChangeMoveSelectorConfig.XML_ELEMENT_NAME,
        type = PillarChangeMoveSelectorConfig.class),
    @XmlElement(
        name = PillarSwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = PillarSwapMoveSelectorConfig.class),
    @XmlElement(
        name = RuinRecreateMoveSelectorConfig.XML_ELEMENT_NAME,
        type = RuinRecreateMoveSelectorConfig.class),
    @XmlElement(
        name = ListRuinRecreateMoveSelectorConfig.XML_ELEMENT_NAME,
        type = ListRuinRecreateMoveSelectorConfig.class),
    @XmlElement(
        name = MultistageMoveSelectorConfig.XML_ELEMENT_NAME,
        type = MultistageMoveSelectorConfig.class),
    @XmlElement(
        name = ListMultistageMoveSelectorConfig.XML_ELEMENT_NAME,
        type = ListMultistageMoveSelectorConfig.class),
    @XmlElement(
        name = SubChainChangeMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SubChainChangeMoveSelectorConfig.class),
    @XmlElement(
        name = SubChainSwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SubChainSwapMoveSelectorConfig.class),
    @XmlElement(
        name = SubListChangeMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SubListChangeMoveSelectorConfig.class),
    @XmlElement(
        name = SubListSwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SubListSwapMoveSelectorConfig.class),
    @XmlElement(
        name = SwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SwapMoveSelectorConfig.class),
    @XmlElement(
        name = TailChainSwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = TailChainSwapMoveSelectorConfig.class),
    @XmlElement(
        name = UnionMoveSelectorConfig.XML_ELEMENT_NAME,
        type = UnionMoveSelectorConfig.class)
  })
  private MoveSelectorConfig moveSelectorConfig = null;

  @XmlElement(name = "acceptor")
  private LocalSearchAcceptorConfig acceptorConfig = null;

  @XmlElement(name = "forager")
  private LocalSearchForagerConfig foragerConfig = null;

  protected String moveThreadCount = null;

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
    @XmlElement(name = NoChangePhaseConfig.XML_ELEMENT_NAME, type = NoChangePhaseConfig.class)
  })
  private List<PhaseConfig<?>> phaseConfigList = null;

  public static final String XML_ELEMENT_NAME = "islandModel";

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

  // Warning: all fields are null (and not defaulted) because they can be inherited
  // and also because input config file should match output config file

  @XmlElement(name = "islandCount")
  private Integer islandCount = null;

  @XmlElement(name = "migrationFrequency")
  private Integer migrationFrequency = null;

  @XmlElement(name = "compareGlobalEnabled")
  private Boolean compareGlobalEnabled = null;

  // NEW: Receive global update frequency
  @XmlElement(name = "receiveGlobalUpdateFrequency")
  private Integer receiveGlobalUpdateFrequency = null;

  // DEPRECATED: Compare global frequency
  @Deprecated
  @XmlElement(name = "compareGlobalFrequency")
  private Integer compareGlobalFrequency = null;

  @XmlElement(name = "migrationTimeout")
  private Long migrationTimeout = null;

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
   * Returns whether comparing to global best is enabled.
   *
   * <p>When enabled, agents periodically check the shared global best solution and adopt it if it's
   * better than their current best. This provides faster convergence and better solution quality.
   *
   * @return true if compare-to-global is enabled, false otherwise, or null if not specified
   */
  public @Nullable Boolean getCompareGlobalEnabled() {
    return compareGlobalEnabled;
  }

  /**
   * Sets whether comparing to global best is enabled.
   *
   * @param compareGlobalEnabled true to enable compare-to-global, false to disable
   */
  public void setCompareGlobalEnabled(@Nullable Boolean compareGlobalEnabled) {
    this.compareGlobalEnabled = compareGlobalEnabled;
  }

  /**
   * Returns the frequency at which islands receive and check global best updates. Islands will
   * check the global best solution every N steps and adopt it if better.
   *
   * @return number of steps between global best checks, or null if not specified
   */
  public @Nullable Integer getReceiveGlobalUpdateFrequency() {
    return receiveGlobalUpdateFrequency;
  }

  /**
   * Sets the frequency at which islands receive and check global best updates.
   *
   * @param receiveGlobalUpdateFrequency number of steps between checks (must be at least 1)
   */
  public void setReceiveGlobalUpdateFrequency(@Nullable Integer receiveGlobalUpdateFrequency) {
    this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
  }

  /**
   * Returns the frequency of comparing to global best (number of steps between checks).
   *
   * @return number of steps between global best comparisons, or null if not specified
   * @deprecated Use {@link #getReceiveGlobalUpdateFrequency()} instead.
   */
  @Deprecated
  public @Nullable Integer getCompareGlobalFrequency() {
    return compareGlobalFrequency;
  }

  /**
   * Sets the frequency of comparing to global best.
   *
   * @param compareGlobalFrequency number of steps between comparisons (must be at least 1)
   * @deprecated Use {@link #setReceiveGlobalUpdateFrequency(Integer)} instead.
   */
  @Deprecated
  public void setCompareGlobalFrequency(@Nullable Integer compareGlobalFrequency) {
    this.compareGlobalFrequency = compareGlobalFrequency;
  }

  /**
   * Returns the timeout for migration operations (send and receive).
   *
   * @return timeout in milliseconds, or null if not specified
   */
  public @Nullable Long getMigrationTimeout() {
    return migrationTimeout;
  }

  /**
   * Sets the timeout for migration operations (send and receive).
   *
   * @param migrationTimeout timeout in milliseconds (must be at least 1)
   */
  public void setMigrationTimeout(@Nullable Long migrationTimeout) {
    this.migrationTimeout = migrationTimeout;
  }

  // Local search configuration getters/setters (from LocalSearchPhaseConfig)

  public @Nullable LocalSearchType getLocalSearchType() {
    return localSearchType;
  }

  public void setLocalSearchType(@Nullable LocalSearchType localSearchType) {
    this.localSearchType = localSearchType;
  }

  public @Nullable MoveSelectorConfig getMoveSelectorConfig() {
    return moveSelectorConfig;
  }

  public void setMoveSelectorConfig(@Nullable MoveSelectorConfig moveSelectorConfig) {
    this.moveSelectorConfig = moveSelectorConfig;
  }

  public @Nullable LocalSearchAcceptorConfig getAcceptorConfig() {
    return acceptorConfig;
  }

  public void setAcceptorConfig(@Nullable LocalSearchAcceptorConfig acceptorConfig) {
    this.acceptorConfig = acceptorConfig;
  }

  public @Nullable LocalSearchForagerConfig getForagerConfig() {
    return foragerConfig;
  }

  public void setForagerConfig(@Nullable LocalSearchForagerConfig foragerConfig) {
    this.foragerConfig = foragerConfig;
  }

  public @Nullable String getMoveThreadCount() {
    return moveThreadCount;
  }

  public void setMoveThreadCount(@Nullable String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
  }

  /**
   * Returns the list of phase configurations for each island.
   *
   * <p>Each island runs the same sequence of phases. This allows defining multi-phase optimization
   * strategies (e.g., construction heuristic followed by local search) that will be executed
   * independently on each island.
   *
   * @return list of phase configurations, or null if not specified
   */
  public @Nullable List<PhaseConfig<?>> getPhaseConfigList() {
    return phaseConfigList;
  }

  /**
   * Sets the list of phase configurations for each island.
   *
   * @param phaseConfigList list of phase configurations
   */
  public void setPhaseConfigList(@Nullable List<PhaseConfig<?>> phaseConfigList) {
    this.phaseConfigList = phaseConfigList;
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
   * Sets whether comparing to global best is enabled and returns this config.
   *
   * @param compareGlobalEnabled true to enable compare-to-global, false to disable
   * @return this config
   */
  public @NonNull IslandModelPhaseConfig withCompareGlobalEnabled(boolean compareGlobalEnabled) {
    this.compareGlobalEnabled = compareGlobalEnabled;
    return this;
  }

  /**
   * Sets the frequency at which islands receive and check global best updates and returns this
   * config.
   *
   * @param receiveGlobalUpdateFrequency number of steps between checks
   * @return this config
   */
  public @NonNull IslandModelPhaseConfig withReceiveGlobalUpdateFrequency(
      int receiveGlobalUpdateFrequency) {
    this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
    return this;
  }

  /**
   * Sets the frequency of comparing to global best and returns this config.
   *
   * @param compareGlobalFrequency number of steps between comparisons
   * @return this config
   * @deprecated Use {@link #withReceiveGlobalUpdateFrequency(int)} instead.
   */
  @Deprecated
  public @NonNull IslandModelPhaseConfig withCompareGlobalFrequency(int compareGlobalFrequency) {
    this.compareGlobalFrequency = compareGlobalFrequency;
    return this;
  }

  /**
   * Sets the timeout for migration operations and returns this config.
   *
   * @param migrationTimeout timeout in milliseconds
   * @return this config
   */
  public @NonNull IslandModelPhaseConfig withMigrationTimeout(long migrationTimeout) {
    this.migrationTimeout = migrationTimeout;
    return this;
  }

  // Local search configuration with methods (from LocalSearchPhaseConfig)

  public @NonNull IslandModelPhaseConfig withLocalSearchType(
      @NonNull LocalSearchType localSearchType) {
    this.localSearchType = localSearchType;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withMoveSelectorConfig(
      @NonNull MoveSelectorConfig moveSelectorConfig) {
    this.moveSelectorConfig = moveSelectorConfig;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withAcceptorConfig(
      @NonNull LocalSearchAcceptorConfig acceptorConfig) {
    this.acceptorConfig = acceptorConfig;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withForagerConfig(
      @NonNull LocalSearchForagerConfig foragerConfig) {
    this.foragerConfig = foragerConfig;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withMoveThreadCount(@NonNull String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withPhaseConfigList(
      @NonNull List<PhaseConfig<?>> phaseConfigList) {
    this.phaseConfigList = phaseConfigList;
    return this;
  }

  @Override
  public @NonNull IslandModelPhaseConfig inherit(@NonNull IslandModelPhaseConfig inheritedConfig) {
    // Call parent's inherit with the inheritedConfig
    super.inherit(inheritedConfig);

    // Inherit local search configuration
    localSearchType =
        ConfigUtils.inheritOverwritableProperty(
            localSearchType, inheritedConfig.getLocalSearchType());
    setMoveSelectorConfig(
        ConfigUtils.inheritOverwritableProperty(
            getMoveSelectorConfig(), inheritedConfig.getMoveSelectorConfig()));
    acceptorConfig = ConfigUtils.inheritConfig(acceptorConfig, inheritedConfig.getAcceptorConfig());
    foragerConfig = ConfigUtils.inheritConfig(foragerConfig, inheritedConfig.getForagerConfig());
    moveThreadCount =
        ConfigUtils.inheritOverwritableProperty(
            moveThreadCount, inheritedConfig.getMoveThreadCount());

    // Inherit island model configuration
    islandCount =
        ConfigUtils.inheritOverwritableProperty(islandCount, inheritedConfig.getIslandCount());
    migrationFrequency =
        ConfigUtils.inheritOverwritableProperty(
            migrationFrequency, inheritedConfig.getMigrationFrequency());
    compareGlobalEnabled =
        ConfigUtils.inheritOverwritableProperty(
            compareGlobalEnabled, inheritedConfig.getCompareGlobalEnabled());

    // NEW: Inherit receive global update frequency
    receiveGlobalUpdateFrequency =
        ConfigUtils.inheritOverwritableProperty(
            receiveGlobalUpdateFrequency, inheritedConfig.getReceiveGlobalUpdateFrequency());

    // DEPRECATED: Inherit compare global frequency for backward compatibility
    compareGlobalFrequency =
        ConfigUtils.inheritOverwritableProperty(
            compareGlobalFrequency, inheritedConfig.getCompareGlobalFrequency());

    // Inherit migration timeout
    migrationTimeout =
        ConfigUtils.inheritOverwritableProperty(
            migrationTimeout, inheritedConfig.getMigrationTimeout());

    // Inherit phase configuration list
    if (inheritedConfig.getPhaseConfigList() != null && phaseConfigList == null) {
      // Only copy phase list if we don't already have one
      // Inheritance of phase list is not supported when both have phases
      phaseConfigList = new ArrayList<>(inheritedConfig.getPhaseConfigList().size());
      for (PhaseConfig<?> inheritedPhase : inheritedConfig.getPhaseConfigList()) {
        phaseConfigList.add(inheritedPhase.copyConfig());
      }
    }

    return this;
  }

  @Override
  public @NonNull IslandModelPhaseConfig copyConfig() {
    return new IslandModelPhaseConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    // Handle termination config
    if (terminationConfig != null) {
      terminationConfig.visitReferencedClasses(classVisitor);
    }

    // Handle local search referenced classes
    if (moveSelectorConfig != null) {
      moveSelectorConfig.visitReferencedClasses(classVisitor);
    }
    if (acceptorConfig != null) {
      acceptorConfig.visitReferencedClasses(classVisitor);
    }
    if (foragerConfig != null) {
      foragerConfig.visitReferencedClasses(classVisitor);
    }

    // Handle phase config list
    if (phaseConfigList != null) {
      for (PhaseConfig<?> phaseConfig : phaseConfigList) {
        phaseConfig.visitReferencedClasses(classVisitor);
      }
    }
  }
}
