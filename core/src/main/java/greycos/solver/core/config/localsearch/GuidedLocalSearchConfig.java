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
      "penaltyFactor",
      "guidanceMode",
      "targetScoreLevelIndex",
      "levelScaleList",
      "focusStepLimit",
      "focusPenaltyUpdateLimit",
      "searchMode",
      "sampleSize",
      "maxUnproductiveRounds",
      "resetPenaltiesOnNewBest"
    })
public class GuidedLocalSearchConfig extends AbstractConfig<GuidedLocalSearchConfig> {

  // Keep defaults out of configuration objects so inheritance and XML round-trips preserve intent.
  private String featureProviderClass;
  private BigDecimal penaltyFactor;
  private GuidedLocalSearchGuidanceMode guidanceMode;
  private Integer targetScoreLevelIndex;

  @XmlElement(name = "levelScale")
  private List<GuidedLocalSearchLevelScaleConfig> levelScaleList;

  private Integer focusStepLimit;
  private Integer focusPenaltyUpdateLimit;
  private GuidedLocalSearchSearchMode searchMode;
  private Integer sampleSize;
  private Integer maxUnproductiveRounds;
  private Boolean resetPenaltiesOnNewBest;

  /**
   * Optional supplemental provider in ALL_LEVELS; required in FIXED_TARGET. The provider must have
   * a public no-arg constructor. Each solve and island owns independent feature sessions and
   * penalty histories.
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

  /** Defaults to ALL_LEVELS unless an explicit target selects legacy FIXED_TARGET behavior. */
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

  /** Maximum committed moves per focus epoch in ALL_LEVELS; defaults to 64. */
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

  /** Maximum penalty updates per focus epoch in ALL_LEVELS; defaults to 8. */
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
   * candidate; FIXED_TARGET also requires preserving the protected score prefix. Defaults to 3.
   * Exhaustive search stops after its first such completed round.
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
