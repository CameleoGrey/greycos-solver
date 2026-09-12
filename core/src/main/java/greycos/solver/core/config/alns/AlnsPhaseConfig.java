package greycos.solver.core.config.alns;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.io.jaxb.JaxbCustomPropertiesAdapter;
import greycos.solver.core.impl.io.jaxb.JaxbDurationAdapter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Adaptive large neighborhood search configuration. Trials execute sequentially on each solver or
 * island. Move workers evaluate independent probes or explicitly configured randomized repair
 * attempts.
 */
@XmlType(
    propOrder = {
      "moveThreadCount",
      "moveThreadingMode",
      "repairAttemptCount",
      "destroyOperatorConfigList",
      "repairOperatorConfigList",
      "selectionPolicyType",
      "selectionPolicyClass",
      "selectionPolicyCustomProperties",
      "acceptanceType",
      "acceptancePolicyClass",
      "acceptancePolicyCustomProperties",
      "lateAcceptanceSize",
      "startingTemperature",
      "coolingRate",
      "segmentLength",
      "reactionFactor",
      "minimumWeight",
      "ucbExploration",
      "newBestReward",
      "improvedReward",
      "acceptedReward",
      "repairScoreCalculationLimit",
      "repairSpentLimit",
      "recoveryCount"
    })
public final class AlnsPhaseConfig extends PhaseConfig<AlnsPhaseConfig> {
  public static final String XML_ELEMENT_NAME = "alns";

  private String moveThreadCount;
  private AlnsMoveThreadingMode moveThreadingMode;
  private Integer repairAttemptCount;

  @XmlElement(name = "destroyOperator")
  private List<AlnsDestroyOperatorConfig> destroyOperatorConfigList;

  @XmlElement(name = "repairOperator")
  private List<AlnsRepairOperatorConfig> repairOperatorConfigList;

  private AlnsSelectionPolicyType selectionPolicyType;
  private String selectionPolicyClass;

  @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
  private Map<String, String> selectionPolicyCustomProperties;

  private AlnsAcceptanceType acceptanceType;
  private String acceptancePolicyClass;

  @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
  private Map<String, String> acceptancePolicyCustomProperties;

  private Integer lateAcceptanceSize;
  private String startingTemperature;
  private Double coolingRate;
  private Integer segmentLength;
  private Double reactionFactor;
  private Double minimumWeight;
  private Double ucbExploration;
  private Double newBestReward;
  private Double improvedReward;
  private Double acceptedReward;
  private Long repairScoreCalculationLimit;

  @XmlJavaTypeAdapter(JaxbDurationAdapter.class)
  private Duration repairSpentLimit;

  private Integer recoveryCount;

  /** Overrides the solver move-thread count for this phase; {@code NONE} disables move workers. */
  public @Nullable String getMoveThreadCount() {
    return moveThreadCount;
  }

  public void setMoveThreadCount(@Nullable String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
  }

  public @NonNull AlnsPhaseConfig withMoveThreadCount(@NonNull String moveThreadCount) {
    setMoveThreadCount(moveThreadCount);
    return this;
  }

  /** Defaults to {@link AlnsMoveThreadingMode#PROBES} after configuration inheritance. */
  public @Nullable AlnsMoveThreadingMode getMoveThreadingMode() {
    return moveThreadingMode;
  }

  public void setMoveThreadingMode(@Nullable AlnsMoveThreadingMode moveThreadingMode) {
    this.moveThreadingMode = moveThreadingMode;
  }

  public @NonNull AlnsPhaseConfig withMoveThreadingMode(
      @NonNull AlnsMoveThreadingMode moveThreadingMode) {
    setMoveThreadingMode(moveThreadingMode);
    return this;
  }

  /**
   * Number of randomized repairs per destroyed state, independent of worker count. Required and at
   * least two in {@link AlnsMoveThreadingMode#REPAIR_ATTEMPTS}; forbidden in probe mode.
   */
  public @Nullable Integer getRepairAttemptCount() {
    return repairAttemptCount;
  }

  public void setRepairAttemptCount(@Nullable Integer repairAttemptCount) {
    this.repairAttemptCount = repairAttemptCount;
  }

  public @NonNull AlnsPhaseConfig withRepairAttemptCount(@NonNull Integer repairAttemptCount) {
    setRepairAttemptCount(repairAttemptCount);
    return this;
  }

  public @Nullable List<AlnsDestroyOperatorConfig> getDestroyOperatorConfigList() {
    return destroyOperatorConfigList;
  }

  public void setDestroyOperatorConfigList(
      @Nullable List<AlnsDestroyOperatorConfig> destroyOperatorConfigList) {
    this.destroyOperatorConfigList = destroyOperatorConfigList;
  }

  public @NonNull AlnsPhaseConfig withDestroyOperatorConfigList(
      @NonNull List<AlnsDestroyOperatorConfig> destroyOperatorConfigList) {
    setDestroyOperatorConfigList(destroyOperatorConfigList);
    return this;
  }

  public @Nullable List<AlnsRepairOperatorConfig> getRepairOperatorConfigList() {
    return repairOperatorConfigList;
  }

  public void setRepairOperatorConfigList(
      @Nullable List<AlnsRepairOperatorConfig> repairOperatorConfigList) {
    this.repairOperatorConfigList = repairOperatorConfigList;
  }

  public @NonNull AlnsPhaseConfig withRepairOperatorConfigList(
      @NonNull List<AlnsRepairOperatorConfig> repairOperatorConfigList) {
    setRepairOperatorConfigList(repairOperatorConfigList);
    return this;
  }

  public @Nullable AlnsSelectionPolicyType getSelectionPolicyType() {
    return selectionPolicyType;
  }

  public void setSelectionPolicyType(@Nullable AlnsSelectionPolicyType selectionPolicyType) {
    this.selectionPolicyType = selectionPolicyType;
  }

  public @NonNull AlnsPhaseConfig withSelectionPolicyType(
      @NonNull AlnsSelectionPolicyType selectionPolicyType) {
    setSelectionPolicyType(selectionPolicyType);
    return this;
  }

  public @Nullable Class<? extends AlnsSelectionPolicy> getSelectionPolicyClass() {
    return ConfigUtils.resolveClass(selectionPolicyClass, "selectionPolicyClass", this);
  }

  public void setSelectionPolicyClass(
      @Nullable Class<? extends AlnsSelectionPolicy> selectionPolicyClass) {
    this.selectionPolicyClass =
        selectionPolicyClass == null ? null : selectionPolicyClass.getName();
  }

  public @NonNull AlnsPhaseConfig withSelectionPolicyClass(
      @NonNull Class<? extends AlnsSelectionPolicy> selectionPolicyClass) {
    setSelectionPolicyClass(selectionPolicyClass);
    return this;
  }

  public @Nullable AlnsAcceptanceType getAcceptanceType() {
    return acceptanceType;
  }

  public @Nullable Map<String, String> getSelectionPolicyCustomProperties() {
    return selectionPolicyCustomProperties;
  }

  public void setSelectionPolicyCustomProperties(@Nullable Map<String, String> properties) {
    selectionPolicyCustomProperties = properties;
  }

  public @NonNull AlnsPhaseConfig withSelectionPolicyCustomProperties(
      @NonNull Map<String, String> properties) {
    setSelectionPolicyCustomProperties(properties);
    return this;
  }

  public @Nullable Map<String, String> getAcceptancePolicyCustomProperties() {
    return acceptancePolicyCustomProperties;
  }

  public void setAcceptancePolicyCustomProperties(@Nullable Map<String, String> properties) {
    acceptancePolicyCustomProperties = properties;
  }

  public @NonNull AlnsPhaseConfig withAcceptancePolicyCustomProperties(
      @NonNull Map<String, String> properties) {
    setAcceptancePolicyCustomProperties(properties);
    return this;
  }

  public void setAcceptanceType(@Nullable AlnsAcceptanceType acceptanceType) {
    this.acceptanceType = acceptanceType;
  }

  public @NonNull AlnsPhaseConfig withAcceptanceType(@NonNull AlnsAcceptanceType acceptanceType) {
    setAcceptanceType(acceptanceType);
    return this;
  }

  public @Nullable Class<? extends AlnsAcceptancePolicy> getAcceptancePolicyClass() {
    return ConfigUtils.resolveClass(acceptancePolicyClass, "acceptancePolicyClass", this);
  }

  public void setAcceptancePolicyClass(
      @Nullable Class<? extends AlnsAcceptancePolicy> acceptancePolicyClass) {
    this.acceptancePolicyClass =
        acceptancePolicyClass == null ? null : acceptancePolicyClass.getName();
  }

  public @NonNull AlnsPhaseConfig withAcceptancePolicyClass(
      @NonNull Class<? extends AlnsAcceptancePolicy> acceptancePolicyClass) {
    setAcceptancePolicyClass(acceptancePolicyClass);
    return this;
  }

  public @Nullable Integer getLateAcceptanceSize() {
    return lateAcceptanceSize;
  }

  public void setLateAcceptanceSize(@Nullable Integer lateAcceptanceSize) {
    this.lateAcceptanceSize = lateAcceptanceSize;
  }

  public @NonNull AlnsPhaseConfig withLateAcceptanceSize(@NonNull Integer lateAcceptanceSize) {
    setLateAcceptanceSize(lateAcceptanceSize);
    return this;
  }

  public @Nullable String getStartingTemperature() {
    return startingTemperature;
  }

  public void setStartingTemperature(@Nullable String startingTemperature) {
    this.startingTemperature = startingTemperature;
  }

  public @NonNull AlnsPhaseConfig withStartingTemperature(@NonNull String startingTemperature) {
    setStartingTemperature(startingTemperature);
    return this;
  }

  public @Nullable Double getCoolingRate() {
    return coolingRate;
  }

  public void setCoolingRate(@Nullable Double coolingRate) {
    this.coolingRate = coolingRate;
  }

  public @NonNull AlnsPhaseConfig withCoolingRate(@NonNull Double coolingRate) {
    setCoolingRate(coolingRate);
    return this;
  }

  public @Nullable Integer getSegmentLength() {
    return segmentLength;
  }

  public void setSegmentLength(@Nullable Integer segmentLength) {
    this.segmentLength = segmentLength;
  }

  public @NonNull AlnsPhaseConfig withSegmentLength(@NonNull Integer segmentLength) {
    setSegmentLength(segmentLength);
    return this;
  }

  public @Nullable Double getReactionFactor() {
    return reactionFactor;
  }

  public void setReactionFactor(@Nullable Double reactionFactor) {
    this.reactionFactor = reactionFactor;
  }

  public @NonNull AlnsPhaseConfig withReactionFactor(@NonNull Double reactionFactor) {
    setReactionFactor(reactionFactor);
    return this;
  }

  public @Nullable Double getMinimumWeight() {
    return minimumWeight;
  }

  public void setMinimumWeight(@Nullable Double minimumWeight) {
    this.minimumWeight = minimumWeight;
  }

  public @NonNull AlnsPhaseConfig withMinimumWeight(@NonNull Double minimumWeight) {
    setMinimumWeight(minimumWeight);
    return this;
  }

  public @Nullable Double getUcbExploration() {
    return ucbExploration;
  }

  public void setUcbExploration(@Nullable Double ucbExploration) {
    this.ucbExploration = ucbExploration;
  }

  public @NonNull AlnsPhaseConfig withUcbExploration(@NonNull Double ucbExploration) {
    setUcbExploration(ucbExploration);
    return this;
  }

  public @Nullable Double getNewBestReward() {
    return newBestReward;
  }

  public void setNewBestReward(@Nullable Double newBestReward) {
    this.newBestReward = newBestReward;
  }

  public @NonNull AlnsPhaseConfig withNewBestReward(@NonNull Double newBestReward) {
    setNewBestReward(newBestReward);
    return this;
  }

  public @Nullable Double getImprovedReward() {
    return improvedReward;
  }

  public void setImprovedReward(@Nullable Double improvedReward) {
    this.improvedReward = improvedReward;
  }

  public @NonNull AlnsPhaseConfig withImprovedReward(@NonNull Double improvedReward) {
    setImprovedReward(improvedReward);
    return this;
  }

  public @Nullable Double getAcceptedReward() {
    return acceptedReward;
  }

  public void setAcceptedReward(@Nullable Double acceptedReward) {
    this.acceptedReward = acceptedReward;
  }

  public @NonNull AlnsPhaseConfig withAcceptedReward(@NonNull Double acceptedReward) {
    setAcceptedReward(acceptedReward);
    return this;
  }

  public @Nullable Long getRepairScoreCalculationLimit() {
    return repairScoreCalculationLimit;
  }

  public void setRepairScoreCalculationLimit(@Nullable Long repairScoreCalculationLimit) {
    this.repairScoreCalculationLimit = repairScoreCalculationLimit;
  }

  public @NonNull AlnsPhaseConfig withRepairScoreCalculationLimit(
      @NonNull Long repairScoreCalculationLimit) {
    setRepairScoreCalculationLimit(repairScoreCalculationLimit);
    return this;
  }

  public @Nullable Duration getRepairSpentLimit() {
    return repairSpentLimit;
  }

  public void setRepairSpentLimit(@Nullable Duration repairSpentLimit) {
    this.repairSpentLimit = repairSpentLimit;
  }

  public @NonNull AlnsPhaseConfig withRepairSpentLimit(@NonNull Duration repairSpentLimit) {
    setRepairSpentLimit(repairSpentLimit);
    return this;
  }

  public @Nullable Integer getRecoveryCount() {
    return recoveryCount;
  }

  public void setRecoveryCount(@Nullable Integer recoveryCount) {
    this.recoveryCount = recoveryCount;
  }

  public @NonNull AlnsPhaseConfig withRecoveryCount(@NonNull Integer recoveryCount) {
    setRecoveryCount(recoveryCount);
    return this;
  }

  public @NonNull AlnsPhaseConfig withDestroyOperators(AlnsDestroyOperatorConfig... operators) {
    return withDestroyOperatorConfigList(new ArrayList<>(List.of(operators)));
  }

  public @NonNull AlnsPhaseConfig withRepairOperators(AlnsRepairOperatorConfig... operators) {
    return withRepairOperatorConfigList(new ArrayList<>(List.of(operators)));
  }

  @Override
  public @NonNull AlnsPhaseConfig inherit(@NonNull AlnsPhaseConfig inheritedConfig) {
    super.inherit(inheritedConfig);
    moveThreadCount =
        ConfigUtils.inheritOverwritableProperty(moveThreadCount, inheritedConfig.moveThreadCount);
    moveThreadingMode =
        ConfigUtils.inheritOverwritableProperty(
            moveThreadingMode, inheritedConfig.moveThreadingMode);
    repairAttemptCount =
        ConfigUtils.inheritOverwritableProperty(
            repairAttemptCount, inheritedConfig.repairAttemptCount);
    if (destroyOperatorConfigList == null && inheritedConfig.destroyOperatorConfigList != null) {
      destroyOperatorConfigList = new ArrayList<>();
      for (var operator : inheritedConfig.destroyOperatorConfigList) {
        destroyOperatorConfigList.add(operator.copyConfig());
      }
    }
    if (repairOperatorConfigList == null && inheritedConfig.repairOperatorConfigList != null) {
      repairOperatorConfigList = new ArrayList<>();
      for (var operator : inheritedConfig.repairOperatorConfigList) {
        repairOperatorConfigList.add(operator.copyConfig());
      }
    }
    selectionPolicyType =
        ConfigUtils.inheritOverwritableProperty(
            selectionPolicyType, inheritedConfig.selectionPolicyType);
    selectionPolicyClass =
        ConfigUtils.inheritOverwritableProperty(
            selectionPolicyClass, inheritedConfig.selectionPolicyClass);
    if (selectionPolicyCustomProperties == null
        && inheritedConfig.selectionPolicyCustomProperties != null) {
      selectionPolicyCustomProperties =
          new LinkedHashMap<>(inheritedConfig.selectionPolicyCustomProperties);
    }
    acceptanceType =
        ConfigUtils.inheritOverwritableProperty(acceptanceType, inheritedConfig.acceptanceType);
    acceptancePolicyClass =
        ConfigUtils.inheritOverwritableProperty(
            acceptancePolicyClass, inheritedConfig.acceptancePolicyClass);
    if (acceptancePolicyCustomProperties == null
        && inheritedConfig.acceptancePolicyCustomProperties != null) {
      acceptancePolicyCustomProperties =
          new LinkedHashMap<>(inheritedConfig.acceptancePolicyCustomProperties);
    }
    lateAcceptanceSize =
        ConfigUtils.inheritOverwritableProperty(
            lateAcceptanceSize, inheritedConfig.lateAcceptanceSize);
    startingTemperature =
        ConfigUtils.inheritOverwritableProperty(
            startingTemperature, inheritedConfig.startingTemperature);
    coolingRate = ConfigUtils.inheritOverwritableProperty(coolingRate, inheritedConfig.coolingRate);
    segmentLength =
        ConfigUtils.inheritOverwritableProperty(segmentLength, inheritedConfig.segmentLength);
    reactionFactor =
        ConfigUtils.inheritOverwritableProperty(reactionFactor, inheritedConfig.reactionFactor);
    minimumWeight =
        ConfigUtils.inheritOverwritableProperty(minimumWeight, inheritedConfig.minimumWeight);
    ucbExploration =
        ConfigUtils.inheritOverwritableProperty(ucbExploration, inheritedConfig.ucbExploration);
    newBestReward =
        ConfigUtils.inheritOverwritableProperty(newBestReward, inheritedConfig.newBestReward);
    improvedReward =
        ConfigUtils.inheritOverwritableProperty(improvedReward, inheritedConfig.improvedReward);
    acceptedReward =
        ConfigUtils.inheritOverwritableProperty(acceptedReward, inheritedConfig.acceptedReward);
    repairScoreCalculationLimit =
        ConfigUtils.inheritOverwritableProperty(
            repairScoreCalculationLimit, inheritedConfig.repairScoreCalculationLimit);
    repairSpentLimit =
        ConfigUtils.inheritOverwritableProperty(repairSpentLimit, inheritedConfig.repairSpentLimit);
    recoveryCount =
        ConfigUtils.inheritOverwritableProperty(recoveryCount, inheritedConfig.recoveryCount);
    return this;
  }

  @Override
  public @NonNull AlnsPhaseConfig copyConfig() {
    return new AlnsPhaseConfig().inherit(this);
  }

  @Override
  public String toString() {
    return "AlnsPhaseConfig(moveThreadingMode="
        + Objects.requireNonNullElse(moveThreadingMode, AlnsMoveThreadingMode.PROBES)
        + ", repairAttemptCount="
        + repairAttemptCount
        + ")";
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (terminationConfig != null) {
      terminationConfig.visitReferencedClasses(classVisitor);
    }
    if (destroyOperatorConfigList != null) {
      destroyOperatorConfigList.forEach(operator -> operator.visitReferencedClasses(classVisitor));
    }
    if (repairOperatorConfigList != null) {
      repairOperatorConfigList.forEach(operator -> operator.visitReferencedClasses(classVisitor));
    }
    if (selectionPolicyClass != null) {
      classVisitor.accept(getSelectionPolicyClass());
    }
    if (acceptancePolicyClass != null) {
      classVisitor.accept(getAcceptancePolicyClass());
    }
  }
}
