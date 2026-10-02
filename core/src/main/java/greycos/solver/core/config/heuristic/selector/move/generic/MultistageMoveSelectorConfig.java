package greycos.solver.core.config.heuristic.selector.move.generic;

import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Configures bounded multistage candidates for one basic planning variable. */
@XmlType(
    propOrder = {
      "stageProviderClass",
      "entityClass",
      "variableName",
      "candidateCountLimit",
      "probeCountLimit"
    })
public final class MultistageMoveSelectorConfig
    extends MoveSelectorConfig<MultistageMoveSelectorConfig> {

  public static final String XML_ELEMENT_NAME = "multistageMoveSelector";
  public static final int DEFAULT_CANDIDATE_COUNT_LIMIT = 64;
  public static final int DEFAULT_PROBE_COUNT_LIMIT = 10_000;

  private String stageProviderClass;
  private String entityClass;
  private String variableName;
  private Integer candidateCountLimit;
  private Integer probeCountLimit;

  public @Nullable Class<? extends BasicVariableStageProvider> getStageProviderClass() {
    return ConfigUtils.resolveClass(stageProviderClass, "stageProviderClass", this);
  }

  public void setStageProviderClass(
      @Nullable Class<? extends BasicVariableStageProvider> stageProviderClass) {
    this.stageProviderClass = stageProviderClass == null ? null : stageProviderClass.getName();
  }

  public @Nullable Class<?> getEntityClass() {
    return ConfigUtils.resolveClass(entityClass, "entityClass", this);
  }

  public void setEntityClass(@Nullable Class<?> entityClass) {
    this.entityClass = entityClass == null ? null : entityClass.getName();
  }

  public @Nullable String getVariableName() {
    return variableName;
  }

  public void setVariableName(@Nullable String variableName) {
    this.variableName = variableName;
  }

  /**
   * Maximum candidate requests per step, including aborted or unchanged requests; defaults to 64.
   */
  public @Nullable Integer getCandidateCountLimit() {
    return candidateCountLimit;
  }

  public void setCandidateCountLimit(@Nullable Integer candidateCountLimit) {
    this.candidateCountLimit = candidateCountLimit;
  }

  /** Maximum scoring probes per candidate; defaults to 10,000. */
  public @Nullable Integer getProbeCountLimit() {
    return probeCountLimit;
  }

  public void setProbeCountLimit(@Nullable Integer probeCountLimit) {
    this.probeCountLimit = probeCountLimit;
  }

  public @NonNull MultistageMoveSelectorConfig withStageProviderClass(
      @NonNull Class<? extends BasicVariableStageProvider> stageProviderClass) {
    setStageProviderClass(stageProviderClass);
    return this;
  }

  public @NonNull MultistageMoveSelectorConfig withEntityClass(@NonNull Class<?> entityClass) {
    setEntityClass(entityClass);
    return this;
  }

  public @NonNull MultistageMoveSelectorConfig withVariableName(@NonNull String variableName) {
    setVariableName(variableName);
    return this;
  }

  public @NonNull MultistageMoveSelectorConfig withCandidateCountLimit(
      @NonNull Integer candidateCountLimit) {
    setCandidateCountLimit(candidateCountLimit);
    return this;
  }

  public @NonNull MultistageMoveSelectorConfig withProbeCountLimit(
      @NonNull Integer probeCountLimit) {
    setProbeCountLimit(probeCountLimit);
    return this;
  }

  public int determineCandidateCountLimit() {
    int resolved =
        candidateCountLimit == null ? DEFAULT_CANDIDATE_COUNT_LIMIT : candidateCountLimit;
    if (resolved <= 0) {
      throw new IllegalArgumentException(
          "The multistageMoveSelector candidateCountLimit (%d) must be positive."
              .formatted(resolved));
    }
    return resolved;
  }

  public int determineProbeCountLimit() {
    int resolved = probeCountLimit == null ? DEFAULT_PROBE_COUNT_LIMIT : probeCountLimit;
    if (resolved <= 0) {
      throw new IllegalArgumentException(
          "The multistageMoveSelector probeCountLimit (%d) must be positive.".formatted(resolved));
    }
    return resolved;
  }

  @Override
  public @NonNull MultistageMoveSelectorConfig inherit(
      @NonNull MultistageMoveSelectorConfig inheritedConfig) {
    super.inherit(inheritedConfig);
    stageProviderClass =
        ConfigUtils.inheritOverwritableProperty(
            stageProviderClass, inheritedConfig.stageProviderClass);
    entityClass = ConfigUtils.inheritOverwritableProperty(entityClass, inheritedConfig.entityClass);
    variableName =
        ConfigUtils.inheritOverwritableProperty(variableName, inheritedConfig.variableName);
    candidateCountLimit =
        ConfigUtils.inheritOverwritableProperty(
            candidateCountLimit, inheritedConfig.candidateCountLimit);
    probeCountLimit =
        ConfigUtils.inheritOverwritableProperty(probeCountLimit, inheritedConfig.probeCountLimit);
    return this;
  }

  @Override
  public @NonNull MultistageMoveSelectorConfig copyConfig() {
    return new MultistageMoveSelectorConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    visitCommonReferencedClasses(classVisitor);
    if (stageProviderClass != null) classVisitor.accept(getStageProviderClass());
    if (entityClass != null) classVisitor.accept(getEntityClass());
  }

  @Override
  public boolean hasNearbySelectionConfig() {
    return false;
  }
}
