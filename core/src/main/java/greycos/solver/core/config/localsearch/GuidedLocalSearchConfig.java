package greycos.solver.core.config.localsearch;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.config.AbstractConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Configures guided local search. Penalties affect search decisions only; the solution's score
 * remains the original business score.
 */
@XmlType(
    propOrder = {
      "featureProviderClass",
      "featureComposition",
      "automaticListOwnershipEnabled",
      "directedOriginSelection",
      "penaltyFactor",
      "guidanceMode",
      "targetScoreLevelIndex",
      "levelScaleList",
      "focusStepLimit",
      "focusPenaltyUpdateLimit",
      "maxPenaltyUpdatesPerStep",
      "excursionStepLimit",
      "excursionRepairStepLimit",
      "searchMode",
      "sampleSize",
      "maxUnproductiveRounds",
      "resetPenaltiesOnNewBest"
    })
public class GuidedLocalSearchConfig extends AbstractConfig<GuidedLocalSearchConfig> {

  // Keep defaults out of configuration objects so inheritance and XML round-trips preserve intent.
  private String featureProviderClass;
  private GuidedLocalSearchFeatureComposition featureComposition;
  private Boolean automaticListOwnershipEnabled;
  private Boolean directedOriginSelection;
  private BigDecimal penaltyFactor;
  private GuidedLocalSearchGuidanceMode guidanceMode;
  private Integer targetScoreLevelIndex;

  @XmlElement(name = "levelScale")
  private List<GuidedLocalSearchLevelScaleConfig> levelScaleList;

  private Integer focusStepLimit;
  private Integer focusPenaltyUpdateLimit;
  private Integer maxPenaltyUpdatesPerStep;
  private Integer excursionStepLimit;
  private Integer excursionRepairStepLimit;
  private GuidedLocalSearchSearchMode searchMode;
  private Integer sampleSize;
  private Integer maxUnproductiveRounds;
  private Boolean resetPenaltiesOnNewBest;

  /**
   * Required by CUSTOM and COMBINED feature composition. The provider must have a public no-arg
   * constructor. Each solve and island owns independent feature sessions and penalty histories.
   */
  @SuppressWarnings("rawtypes")
  public @Nullable Class<? extends GuidedLocalSearchFeatureProvider> getFeatureProviderClass() {
    return ConfigUtils.resolveClass(featureProviderClass, "featureProviderClass", this);
  }

  @SuppressWarnings("rawtypes")
  public void setFeatureProviderClass(
      @Nullable Class<? extends GuidedLocalSearchFeatureProvider> featureProviderClass) {
    this.featureProviderClass =
        featureProviderClass == null ? null : featureProviderClass.getName();
  }

  @SuppressWarnings("rawtypes")
  public @NonNull GuidedLocalSearchConfig withFeatureProviderClass(
      @NonNull Class<? extends GuidedLocalSearchFeatureProvider> featureProviderClass) {
    setFeatureProviderClass(featureProviderClass);
    return this;
  }

  /**
   * When omitted, ALL_LEVELS uses AUTOMATIC without a provider and COMBINED with a provider;
   * FIXED_TARGET uses CUSTOM. Explicit AUTOMATIC also supports FIXED_TARGET without a provider.
   */
  public @Nullable GuidedLocalSearchFeatureComposition getFeatureComposition() {
    return featureComposition;
  }

  public void setFeatureComposition(
      @Nullable GuidedLocalSearchFeatureComposition featureComposition) {
    this.featureComposition = featureComposition;
  }

  public @NonNull GuidedLocalSearchConfig withFeatureComposition(
      @NonNull GuidedLocalSearchFeatureComposition featureComposition) {
    setFeatureComposition(featureComposition);
    return this;
  }

  /** Whether automatic list features include value ownership; defaults to {@code false}. */
  public @Nullable Boolean getAutomaticListOwnershipEnabled() {
    return automaticListOwnershipEnabled;
  }

  public void setAutomaticListOwnershipEnabled(@Nullable Boolean automaticListOwnershipEnabled) {
    this.automaticListOwnershipEnabled = automaticListOwnershipEnabled;
  }

  public @NonNull GuidedLocalSearchConfig withAutomaticListOwnershipEnabled(
      @NonNull Boolean automaticListOwnershipEnabled) {
    setAutomaticListOwnershipEnabled(automaticListOwnershipEnabled);
    return this;
  }

  /** Whether supported selectors prefer origins with high penalty pressure; defaults to false. */
  public @Nullable Boolean getDirectedOriginSelection() {
    return directedOriginSelection;
  }

  public void setDirectedOriginSelection(@Nullable Boolean directedOriginSelection) {
    this.directedOriginSelection = directedOriginSelection;
  }

  public @NonNull GuidedLocalSearchConfig withDirectedOriginSelection(
      @NonNull Boolean directedOriginSelection) {
    setDirectedOriginSelection(directedOriginSelection);
    return this;
  }

  /** Positive exact decimal factor applied to weighted penalties; defaults to {@code 0.1}. */
  public @Nullable BigDecimal getPenaltyFactor() {
    return penaltyFactor;
  }

  public void setPenaltyFactor(@Nullable BigDecimal penaltyFactor) {
    this.penaltyFactor = penaltyFactor;
  }

  public @NonNull GuidedLocalSearchConfig withPenaltyFactor(@NonNull BigDecimal penaltyFactor) {
    setPenaltyFactor(penaltyFactor);
    return this;
  }

  /** Defaults to ALL_LEVELS unless an explicit target selects FIXED_TARGET. */
  public @Nullable GuidedLocalSearchGuidanceMode getGuidanceMode() {
    return guidanceMode;
  }

  public void setGuidanceMode(@Nullable GuidedLocalSearchGuidanceMode guidanceMode) {
    this.guidanceMode = guidanceMode;
  }

  public @NonNull GuidedLocalSearchConfig withGuidanceMode(
      @NonNull GuidedLocalSearchGuidanceMode guidanceMode) {
    setGuidanceMode(guidanceMode);
    return this;
  }

  public @Nullable List<GuidedLocalSearchLevelScaleConfig> getLevelScaleList() {
    return levelScaleList;
  }

  public void setLevelScaleList(@Nullable List<GuidedLocalSearchLevelScaleConfig> levelScaleList) {
    this.levelScaleList = levelScaleList;
  }

  public @NonNull GuidedLocalSearchConfig withLevelScaleList(
      @NonNull List<GuidedLocalSearchLevelScaleConfig> levelScaleList) {
    setLevelScaleList(levelScaleList);
    return this;
  }

  /** Maximum committed moves per focus epoch; defaults to 64. Progress renews the same focus. */
  public @Nullable Integer getFocusStepLimit() {
    return focusStepLimit;
  }

  public void setFocusStepLimit(@Nullable Integer focusStepLimit) {
    this.focusStepLimit = focusStepLimit;
  }

  public @NonNull GuidedLocalSearchConfig withFocusStepLimit(int focusStepLimit) {
    setFocusStepLimit(focusStepLimit);
    return this;
  }

  /** Maximum penalty updates per focus epoch; defaults to 8. */
  public @Nullable Integer getFocusPenaltyUpdateLimit() {
    return focusPenaltyUpdateLimit;
  }

  public void setFocusPenaltyUpdateLimit(@Nullable Integer focusPenaltyUpdateLimit) {
    this.focusPenaltyUpdateLimit = focusPenaltyUpdateLimit;
  }

  public @NonNull GuidedLocalSearchConfig withFocusPenaltyUpdateLimit(int focusPenaltyUpdateLimit) {
    setFocusPenaltyUpdateLimit(focusPenaltyUpdateLimit);
    return this;
  }

  /** Maximum penalty updates across all focuses in one real decision; defaults to 64. */
  public @Nullable Integer getMaxPenaltyUpdatesPerStep() {
    return maxPenaltyUpdatesPerStep;
  }

  public void setMaxPenaltyUpdatesPerStep(@Nullable Integer maxPenaltyUpdatesPerStep) {
    this.maxPenaltyUpdatesPerStep = maxPenaltyUpdatesPerStep;
  }

  public @NonNull GuidedLocalSearchConfig withMaxPenaltyUpdatesPerStep(
      int maxPenaltyUpdatesPerStep) {
    setMaxPenaltyUpdatesPerStep(maxPenaltyUpdatesPerStep);
    return this;
  }

  /** Maximum committed moves in a deliberate hard-score excursion; defaults to 8. */
  public @Nullable Integer getExcursionStepLimit() {
    return excursionStepLimit;
  }

  public void setExcursionStepLimit(@Nullable Integer excursionStepLimit) {
    this.excursionStepLimit = excursionStepLimit;
  }

  public @NonNull GuidedLocalSearchConfig withExcursionStepLimit(int excursionStepLimit) {
    setExcursionStepLimit(excursionStepLimit);
    return this;
  }

  /** Maximum committed repair moves following a hard-score excursion; defaults to 64. */
  public @Nullable Integer getExcursionRepairStepLimit() {
    return excursionRepairStepLimit;
  }

  public void setExcursionRepairStepLimit(@Nullable Integer excursionRepairStepLimit) {
    this.excursionRepairStepLimit = excursionRepairStepLimit;
  }

  public @NonNull GuidedLocalSearchConfig withExcursionRepairStepLimit(
      int excursionRepairStepLimit) {
    setExcursionRepairStepLimit(excursionRepairStepLimit);
    return this;
  }

  /**
   * Selects FIXED_TARGET when the guidance mode is unspecified. In FIXED_TARGET, defaults to the
   * last level. Structural and unassigned state, and business levels above the target, retain
   * priority. A hard target may become infeasible during search. Feature costs must use the target
   * level's units.
   */
  public @Nullable Integer getTargetScoreLevelIndex() {
    return targetScoreLevelIndex;
  }

  public void setTargetScoreLevelIndex(@Nullable Integer targetScoreLevelIndex) {
    this.targetScoreLevelIndex = targetScoreLevelIndex;
  }

  public @NonNull GuidedLocalSearchConfig withTargetScoreLevelIndex(
      @NonNull Integer targetScoreLevelIndex) {
    setTargetScoreLevelIndex(targetScoreLevelIndex);
    return this;
  }

  /** Defaults to {@link GuidedLocalSearchSearchMode#SAMPLED}. */
  public @Nullable GuidedLocalSearchSearchMode getSearchMode() {
    return searchMode;
  }

  public void setSearchMode(@Nullable GuidedLocalSearchSearchMode searchMode) {
    this.searchMode = searchMode;
  }

  public @NonNull GuidedLocalSearchConfig withSearchMode(
      @NonNull GuidedLocalSearchSearchMode searchMode) {
    setSearchMode(searchMode);
    return this;
  }

  /**
   * Maximum selected candidates per sampled round, including non-doable candidates; defaults to
   * {@code 1000}. Must be positive and must not be configured in exhaustive mode.
   */
  public @Nullable Integer getSampleSize() {
    return sampleSize;
  }

  public void setSampleSize(@Nullable Integer sampleSize) {
    this.sampleSize = sampleSize;
  }

  public @NonNull GuidedLocalSearchConfig withSampleSize(@NonNull Integer sampleSize) {
    setSampleSize(sampleSize);
    return this;
  }

  /**
   * Positive bound on consecutive sampled rounds with no structurally valid, fully assigned
   * candidate admitted by the active focus and excursion state; defaults to 3. Exhaustive search
   * acts after its first such completed round. A bounded focus escalation may follow where the
   * guidance mode permits it.
   */
  public @Nullable Integer getMaxUnproductiveRounds() {
    return maxUnproductiveRounds;
  }

  public void setMaxUnproductiveRounds(@Nullable Integer maxUnproductiveRounds) {
    this.maxUnproductiveRounds = maxUnproductiveRounds;
  }

  public @NonNull GuidedLocalSearchConfig withMaxUnproductiveRounds(
      @NonNull Integer maxUnproductiveRounds) {
    setMaxUnproductiveRounds(maxUnproductiveRounds);
    return this;
  }

  /** Whether to clear penalties on a new best business score; defaults to {@code false}. */
  public @Nullable Boolean getResetPenaltiesOnNewBest() {
    return resetPenaltiesOnNewBest;
  }

  public void setResetPenaltiesOnNewBest(@Nullable Boolean resetPenaltiesOnNewBest) {
    this.resetPenaltiesOnNewBest = resetPenaltiesOnNewBest;
  }

  public @NonNull GuidedLocalSearchConfig withResetPenaltiesOnNewBest(
      @NonNull Boolean resetPenaltiesOnNewBest) {
    setResetPenaltiesOnNewBest(resetPenaltiesOnNewBest);
    return this;
  }

  @Override
  public @NonNull GuidedLocalSearchConfig inherit(
      @NonNull GuidedLocalSearchConfig inheritedConfig) {
    featureProviderClass =
        ConfigUtils.inheritOverwritableProperty(
            featureProviderClass, inheritedConfig.featureProviderClass);
    featureComposition =
        ConfigUtils.inheritOverwritableProperty(
            featureComposition, inheritedConfig.featureComposition);
    automaticListOwnershipEnabled =
        ConfigUtils.inheritOverwritableProperty(
            automaticListOwnershipEnabled, inheritedConfig.automaticListOwnershipEnabled);
    directedOriginSelection =
        ConfigUtils.inheritOverwritableProperty(
            directedOriginSelection, inheritedConfig.directedOriginSelection);
    penaltyFactor =
        ConfigUtils.inheritOverwritableProperty(penaltyFactor, inheritedConfig.penaltyFactor);
    guidanceMode =
        ConfigUtils.inheritOverwritableProperty(guidanceMode, inheritedConfig.guidanceMode);
    targetScoreLevelIndex =
        ConfigUtils.inheritOverwritableProperty(
            targetScoreLevelIndex, inheritedConfig.targetScoreLevelIndex);
    levelScaleList =
        ConfigUtils.inheritMergeableListConfig(levelScaleList, inheritedConfig.levelScaleList);
    focusStepLimit =
        ConfigUtils.inheritOverwritableProperty(focusStepLimit, inheritedConfig.focusStepLimit);
    focusPenaltyUpdateLimit =
        ConfigUtils.inheritOverwritableProperty(
            focusPenaltyUpdateLimit, inheritedConfig.focusPenaltyUpdateLimit);
    maxPenaltyUpdatesPerStep =
        ConfigUtils.inheritOverwritableProperty(
            maxPenaltyUpdatesPerStep, inheritedConfig.maxPenaltyUpdatesPerStep);
    excursionStepLimit =
        ConfigUtils.inheritOverwritableProperty(
            excursionStepLimit, inheritedConfig.excursionStepLimit);
    excursionRepairStepLimit =
        ConfigUtils.inheritOverwritableProperty(
            excursionRepairStepLimit, inheritedConfig.excursionRepairStepLimit);
    searchMode = ConfigUtils.inheritOverwritableProperty(searchMode, inheritedConfig.searchMode);
    sampleSize = ConfigUtils.inheritOverwritableProperty(sampleSize, inheritedConfig.sampleSize);
    maxUnproductiveRounds =
        ConfigUtils.inheritOverwritableProperty(
            maxUnproductiveRounds, inheritedConfig.maxUnproductiveRounds);
    resetPenaltiesOnNewBest =
        ConfigUtils.inheritOverwritableProperty(
            resetPenaltiesOnNewBest, inheritedConfig.resetPenaltiesOnNewBest);
    return this;
  }

  @Override
  public @NonNull GuidedLocalSearchConfig copyConfig() {
    return new GuidedLocalSearchConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (featureProviderClass != null) {
      classVisitor.accept(getFeatureProviderClass());
    }
  }
}
