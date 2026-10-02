package greycos.solver.core.config.constructionheuristic;

import java.util.List;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.placer.EntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySorterManner;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSorterManner;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@XmlType(
    propOrder = {
      "moveThreadCount",
      "constructionHeuristicType",
      "nearbySelectionAutoConfigurationEnabled",
      "nearbySelectionSize",
      "entitySorterManner",
      "valueSorterManner",
      "entityPlacerConfig",
      "moveSelectorConfigList",
      "foragerConfig"
    })
public class ConstructionHeuristicPhaseConfig
    extends PhaseConfig<ConstructionHeuristicPhaseConfig> {

  public static final String XML_ELEMENT_NAME = "constructionHeuristic";

  // Warning: all fields are null (and not defaulted) because they can be inherited
  // and also because the input config file should match the output config file

  protected ConstructionHeuristicType constructionHeuristicType = null;
  protected Boolean nearbySelectionAutoConfigurationEnabled = null;
  protected Integer nearbySelectionSize = null;
  protected String moveThreadCount = null;
  protected EntitySorterManner entitySorterManner = null;
  protected ValueSorterManner valueSorterManner = null;

  @XmlElements({
    @XmlElement(name = "queuedEntityPlacer", type = QueuedEntityPlacerConfig.class),
    @XmlElement(name = "queuedValuePlacer", type = QueuedValuePlacerConfig.class),
    @XmlElement(name = "pooledEntityPlacer", type = PooledEntityPlacerConfig.class)
  })
  protected EntityPlacerConfig entityPlacerConfig = null;

  /** Simpler alternative for {@link #entityPlacerConfig}. */
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
        name = SwapMoveSelectorConfig.XML_ELEMENT_NAME,
        type = SwapMoveSelectorConfig.class),
    @XmlElement(
        name = UnionMoveSelectorConfig.XML_ELEMENT_NAME,
        type = UnionMoveSelectorConfig.class)
  })
  protected List<MoveSelectorConfig> moveSelectorConfigList = null;

  @XmlElement(name = "forager")
  protected ConstructionHeuristicForagerConfig foragerConfig = null;

  // ************************************************************************
  // Constructors and simple getters/setters
  // ************************************************************************

  public @Nullable ConstructionHeuristicType getConstructionHeuristicType() {
    return constructionHeuristicType;
  }

  public void setConstructionHeuristicType(
      @Nullable ConstructionHeuristicType constructionHeuristicType) {
    this.constructionHeuristicType = constructionHeuristicType;
  }

  /**
   * Whether nearby selection configured for local search is also used during construction. A null
   * value defaults to false. Disabling inference does not disable explicitly configured
   * construction nearby selectors.
   */
  public @Nullable Boolean getNearbySelectionAutoConfigurationEnabled() {
    return nearbySelectionAutoConfigurationEnabled;
  }

  public void setNearbySelectionAutoConfigurationEnabled(
      @Nullable Boolean nearbySelectionAutoConfigurationEnabled) {
    this.nearbySelectionAutoConfigurationEnabled = nearbySelectionAutoConfigurationEnabled;
  }

  /**
   * Initial number of complete nearby assignments evaluated for each construction origin. A null
   * value defaults to 40. The neighborhood grows when all evaluated assignments worsen the hard
   * score; this setting is independent of local search's random distribution. Setting this size
   * does not enable automatic nearby selection; also enable {@link
   * #getNearbySelectionAutoConfigurationEnabled()}.
   */
  public @Nullable Integer getNearbySelectionSize() {
    return nearbySelectionSize;
  }

  public void setNearbySelectionSize(@Nullable Integer nearbySelectionSize) {
    this.nearbySelectionSize = nearbySelectionSize;
  }

  public @Nullable String getMoveThreadCount() {
    return moveThreadCount;
  }

  public void setMoveThreadCount(@Nullable String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
  }

  public @Nullable EntitySorterManner getEntitySorterManner() {
    return entitySorterManner;
  }

  public void setEntitySorterManner(@Nullable EntitySorterManner entitySorterManner) {
    this.entitySorterManner = entitySorterManner;
  }

  public @Nullable ValueSorterManner getValueSorterManner() {
    return valueSorterManner;
  }

  public void setValueSorterManner(@Nullable ValueSorterManner valueSorterManner) {
    this.valueSorterManner = valueSorterManner;
  }

  public @Nullable EntityPlacerConfig getEntityPlacerConfig() {
    return entityPlacerConfig;
  }

  public void setEntityPlacerConfig(@Nullable EntityPlacerConfig entityPlacerConfig) {
    this.entityPlacerConfig = entityPlacerConfig;
  }

  public @Nullable List<@NonNull MoveSelectorConfig> getMoveSelectorConfigList() {
    return moveSelectorConfigList;
  }

  public void setMoveSelectorConfigList(
      @Nullable List<@NonNull MoveSelectorConfig> moveSelectorConfigList) {
    this.moveSelectorConfigList = moveSelectorConfigList;
  }

  public @Nullable ConstructionHeuristicForagerConfig getForagerConfig() {
    return foragerConfig;
  }

  public void setForagerConfig(@Nullable ConstructionHeuristicForagerConfig foragerConfig) {
    this.foragerConfig = foragerConfig;
  }

  // ************************************************************************
  // With methods
  // ************************************************************************

  public @NonNull ConstructionHeuristicPhaseConfig withConstructionHeuristicType(
      ConstructionHeuristicType constructionHeuristicType) {
    this.constructionHeuristicType = constructionHeuristicType;
    return this;
  }

  public @NonNull ConstructionHeuristicPhaseConfig withMoveThreadCount(
      @NonNull String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
    return this;
  }

  public @NonNull ConstructionHeuristicPhaseConfig withNearbySelectionAutoConfigurationEnabled(
      @NonNull Boolean nearbySelectionAutoConfigurationEnabled) {
    setNearbySelectionAutoConfigurationEnabled(nearbySelectionAutoConfigurationEnabled);
    return this;
  }

  public @NonNull ConstructionHeuristicPhaseConfig withNearbySelectionSize(
      @NonNull Integer nearbySelectionSize) {
    setNearbySelectionSize(nearbySelectionSize);
    return this;
  }

  public @NonNull ConstructionHeuristicPhaseConfig withEntitySorterManner(
      @NonNull EntitySorterManner entitySorterManner) {
    this.entitySorterManner = entitySorterManner;
    return this;
  }

  public @NonNull ConstructionHeuristicPhaseConfig withValueSorterManner(
      @NonNull ValueSorterManner valueSorterManner) {
    this.valueSorterManner = valueSorterManner;
    return this;
  }

  public @NonNull ConstructionHeuristicPhaseConfig withEntityPlacerConfig(
      @NonNull EntityPlacerConfig<?> entityPlacerConfig) {
    this.entityPlacerConfig = entityPlacerConfig;
    return this;
  }

  public @NonNull ConstructionHeuristicPhaseConfig withMoveSelectorConfigList(
      @NonNull List<@NonNull MoveSelectorConfig> moveSelectorConfigList) {
    this.moveSelectorConfigList = moveSelectorConfigList;
    return this;
  }

  public @NonNull ConstructionHeuristicPhaseConfig withForagerConfig(
      @NonNull ConstructionHeuristicForagerConfig foragerConfig) {
    this.foragerConfig = foragerConfig;
    return this;
  }

  @Override
  public @NonNull ConstructionHeuristicPhaseConfig inherit(
      @NonNull ConstructionHeuristicPhaseConfig inheritedConfig) {
    super.inherit(inheritedConfig);
    constructionHeuristicType =
        ConfigUtils.inheritOverwritableProperty(
            constructionHeuristicType, inheritedConfig.getConstructionHeuristicType());
    nearbySelectionAutoConfigurationEnabled =
        ConfigUtils.inheritOverwritableProperty(
            nearbySelectionAutoConfigurationEnabled,
            inheritedConfig.getNearbySelectionAutoConfigurationEnabled());
    nearbySelectionSize =
        ConfigUtils.inheritOverwritableProperty(
            nearbySelectionSize, inheritedConfig.getNearbySelectionSize());
    moveThreadCount =
        ConfigUtils.inheritOverwritableProperty(
            moveThreadCount, inheritedConfig.getMoveThreadCount());
    entitySorterManner =
        ConfigUtils.inheritOverwritableProperty(
            entitySorterManner, inheritedConfig.getEntitySorterManner());
    valueSorterManner =
        ConfigUtils.inheritOverwritableProperty(
            valueSorterManner, inheritedConfig.getValueSorterManner());
    setEntityPlacerConfig(
        ConfigUtils.inheritOverwritableProperty(
            getEntityPlacerConfig(), inheritedConfig.getEntityPlacerConfig()));
    moveSelectorConfigList =
        ConfigUtils.inheritMergeableListConfig(
            moveSelectorConfigList, inheritedConfig.getMoveSelectorConfigList());
    foragerConfig = ConfigUtils.inheritConfig(foragerConfig, inheritedConfig.getForagerConfig());
    return this;
  }

  @Override
  public @NonNull ConstructionHeuristicPhaseConfig copyConfig() {
    var copy = new ConstructionHeuristicPhaseConfig().inherit(this);
    if (entityPlacerConfig != null) {
      copy.setEntityPlacerConfig((EntityPlacerConfig) entityPlacerConfig.copyConfig());
    }
    return copy;
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (terminationConfig != null) {
      terminationConfig.visitReferencedClasses(classVisitor);
    }
    if (entityPlacerConfig != null) {
      entityPlacerConfig.visitReferencedClasses(classVisitor);
    }
    if (moveSelectorConfigList != null) {
      moveSelectorConfigList.forEach(ms -> ms.visitReferencedClasses(classVisitor));
    }
    if (foragerConfig != null) {
      foragerConfig.visitReferencedClasses(classVisitor);
    }
  }
}
