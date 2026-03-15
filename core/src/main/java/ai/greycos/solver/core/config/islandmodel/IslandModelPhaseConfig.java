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
import ai.greycos.solver.core.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchType;
import ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
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
      "moveThreadCount",
      "migrationFrequency",
      "compareGlobalEnabled",
      "receiveGlobalUpdateFrequency",
      "compareGlobalFrequency",
      "migrationTimeout",
      "localSearchType",
      "acceptorConfig",
      "foragerConfig",
      "moveSelectorConfig",
      "phaseConfigList",
    })
public class IslandModelPhaseConfig extends PhaseConfig<IslandModelPhaseConfig> {

  @XmlElement(name = "localSearchType")
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
        name = SubListChangeMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SubListChangeMoveSelectorConfig.class),
    @XmlElement(
        name = SubListSwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SubListSwapMoveSelectorConfig.class),
    @XmlElement(
        name = SwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SwapMoveSelectorConfig.class),
    @XmlElement(
        name = UnionMoveSelectorConfig.XML_ELEMENT_NAME,
        type = UnionMoveSelectorConfig.class)
  })
  private MoveSelectorConfig moveSelectorConfig = null;

  @XmlElement(name = "acceptor")
  private LocalSearchAcceptorConfig acceptorConfig = null;

  @XmlElement(name = "forager")
  private LocalSearchForagerConfig foragerConfig = null;

  @XmlElement(name = "moveThreadCount")
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
  })
  private List<PhaseConfig<?>> phaseConfigList = null;

  public static final String XML_ELEMENT_NAME = "islandModel";
  public static final int DEFAULT_ISLAND_COUNT = 4;
  public static final int DEFAULT_MIGRATION_FREQUENCY = Integer.MAX_VALUE;
  public static final int DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY = 400;
  public static final long DEFAULT_MIGRATION_TIMEOUT = 1000L;

  @XmlElement(name = "islandCount")
  private Integer islandCount = null;

  @XmlElement(name = "migrationFrequency")
  private Integer migrationFrequency = null;

  @XmlElement(name = "compareGlobalEnabled")
  private Boolean compareGlobalEnabled = null;

  @XmlElement(name = "receiveGlobalUpdateFrequency")
  private Integer receiveGlobalUpdateFrequency = null;

  @Deprecated
  @XmlElement(name = "compareGlobalFrequency")
  private Integer compareGlobalFrequency = null;

  @XmlElement(name = "migrationTimeout")
  private Long migrationTimeout = null;

  public @Nullable Integer getIslandCount() {
    return islandCount;
  }

  public void setIslandCount(@Nullable Integer islandCount) {
    this.islandCount = islandCount;
  }

  public @Nullable Integer getMigrationFrequency() {
    return migrationFrequency;
  }

  public void setMigrationFrequency(@Nullable Integer migrationFrequency) {
    this.migrationFrequency = migrationFrequency;
  }

  public @Nullable Boolean getCompareGlobalEnabled() {
    return compareGlobalEnabled;
  }

  public void setCompareGlobalEnabled(@Nullable Boolean compareGlobalEnabled) {
    this.compareGlobalEnabled = compareGlobalEnabled;
  }

  public @Nullable Integer getReceiveGlobalUpdateFrequency() {
    return receiveGlobalUpdateFrequency;
  }

  public void setReceiveGlobalUpdateFrequency(@Nullable Integer receiveGlobalUpdateFrequency) {
    this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
  }

  @Deprecated
  public @Nullable Integer getCompareGlobalFrequency() {
    return compareGlobalFrequency;
  }

  @Deprecated
  public void setCompareGlobalFrequency(@Nullable Integer compareGlobalFrequency) {
    this.compareGlobalFrequency = compareGlobalFrequency;
  }

  public @Nullable Long getMigrationTimeout() {
    return migrationTimeout;
  }

  public void setMigrationTimeout(@Nullable Long migrationTimeout) {
    this.migrationTimeout = migrationTimeout;
  }

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

  public @Nullable List<PhaseConfig<?>> getPhaseConfigList() {
    return phaseConfigList;
  }

  public void setPhaseConfigList(@Nullable List<PhaseConfig<?>> phaseConfigList) {
    this.phaseConfigList = phaseConfigList;
  }

  public @NonNull IslandModelPhaseConfig withIslandCount(int islandCount) {
    this.islandCount = islandCount;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withMigrationFrequency(int migrationFrequency) {
    this.migrationFrequency = migrationFrequency;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withCompareGlobalEnabled(boolean compareGlobalEnabled) {
    this.compareGlobalEnabled = compareGlobalEnabled;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withReceiveGlobalUpdateFrequency(
      int receiveGlobalUpdateFrequency) {
    this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
    return this;
  }

  @Deprecated
  public @NonNull IslandModelPhaseConfig withCompareGlobalFrequency(int compareGlobalFrequency) {
    this.compareGlobalFrequency = compareGlobalFrequency;
    return this;
  }

  public @NonNull IslandModelPhaseConfig withMigrationTimeout(long migrationTimeout) {
    this.migrationTimeout = migrationTimeout;
    return this;
  }

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
    super.inherit(inheritedConfig);

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

    islandCount =
        ConfigUtils.inheritOverwritableProperty(islandCount, inheritedConfig.getIslandCount());
    migrationFrequency =
        ConfigUtils.inheritOverwritableProperty(
            migrationFrequency, inheritedConfig.getMigrationFrequency());
    compareGlobalEnabled =
        ConfigUtils.inheritOverwritableProperty(
            compareGlobalEnabled, inheritedConfig.getCompareGlobalEnabled());

    receiveGlobalUpdateFrequency =
        ConfigUtils.inheritOverwritableProperty(
            receiveGlobalUpdateFrequency, inheritedConfig.getReceiveGlobalUpdateFrequency());

    compareGlobalFrequency =
        ConfigUtils.inheritOverwritableProperty(
            compareGlobalFrequency, inheritedConfig.getCompareGlobalFrequency());

    migrationTimeout =
        ConfigUtils.inheritOverwritableProperty(
            migrationTimeout, inheritedConfig.getMigrationTimeout());

    if (inheritedConfig.getPhaseConfigList() != null && phaseConfigList == null) {

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

    if (terminationConfig != null) {
      terminationConfig.visitReferencedClasses(classVisitor);
    }

    if (moveSelectorConfig != null) {
      moveSelectorConfig.visitReferencedClasses(classVisitor);
    }
    if (acceptorConfig != null) {
      acceptorConfig.visitReferencedClasses(classVisitor);
    }
    if (foragerConfig != null) {
      foragerConfig.visitReferencedClasses(classVisitor);
    }

    if (phaseConfigList != null) {
      for (PhaseConfig<?> phaseConfig : phaseConfigList) {
        phaseConfig.visitReferencedClasses(classVisitor);
      }
    }
  }
}
