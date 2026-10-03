package greycos.solver.core.config.heuristic.selector.move.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageVariableReference;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Configures bounded multistage candidates across explicitly declared basic and list variables. */
@XmlType(
    propOrder = {"stageProviderClass", "variableList", "candidateCountLimit", "probeCountLimit"})
public final class CrossVariableMultistageMoveSelectorConfig
    extends MoveSelectorConfig<CrossVariableMultistageMoveSelectorConfig> {

  public static final String XML_ELEMENT_NAME = "crossVariableMultistageMoveSelector";
  public static final int DEFAULT_CANDIDATE_COUNT_LIMIT = 64;
  public static final int DEFAULT_PROBE_COUNT_LIMIT = 10_000;

  private String stageProviderClass;

  @XmlElement(name = "variable")
  private List<MultistageVariableConfig> variableList;

  private Integer candidateCountLimit;
  private Integer probeCountLimit;

  public @Nullable Class<? extends CrossVariableStageProvider> getStageProviderClass() {
    return ConfigUtils.resolveClass(stageProviderClass, "stageProviderClass", this);
  }

  public void setStageProviderClass(
      @Nullable Class<? extends CrossVariableStageProvider> stageProviderClass) {
    this.stageProviderClass = stageProviderClass == null ? null : stageProviderClass.getName();
  }

  /**
   * Variables available to the provider. An unset list inherits a copy of the parent list; an
   * explicitly supplied list replaces it. An empty declaration list is invalid.
   */
  public @Nullable List<MultistageVariableConfig> getVariableList() {
    return variableList;
  }

  public void setVariableList(@Nullable List<MultistageVariableConfig> variableList) {
    this.variableList = variableList;
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

  /**
   * Maximum scoring probes per candidate, shared by all variables and stages; defaults to 10,000.
   */
  public @Nullable Integer getProbeCountLimit() {
    return probeCountLimit;
  }

  public void setProbeCountLimit(@Nullable Integer probeCountLimit) {
    this.probeCountLimit = probeCountLimit;
  }

  public @NonNull CrossVariableMultistageMoveSelectorConfig withStageProviderClass(
      @NonNull Class<? extends CrossVariableStageProvider> stageProviderClass) {
    setStageProviderClass(stageProviderClass);
    return this;
  }

  public @NonNull CrossVariableMultistageMoveSelectorConfig withVariableList(
      @NonNull List<MultistageVariableConfig> variableList) {
    setVariableList(variableList);
    return this;
  }

  /** Replaces the declaration list with independent configuration objects for these references. */
  public @NonNull CrossVariableMultistageMoveSelectorConfig withVariables(
      @NonNull MultistageVariableReference<?, ?>... variables) {
    Objects.requireNonNull(variables, "The variables must not be null.");
    var declarations = new ArrayList<MultistageVariableConfig>(variables.length);
    for (var variable : variables) {
      Objects.requireNonNull(variable, "A variable reference must not be null.");
      declarations.add(
          new MultistageVariableConfig()
              .withKind(
                  variable instanceof BasicVariableReference<?, ?>
                      ? MultistageVariableKind.BASIC
                      : MultistageVariableKind.LIST)
              .withEntityClass(variable.entityClass())
              .withVariableName(variable.variableName())
              .withValueClass(variable.valueClass()));
    }
    setVariableList(declarations);
    return this;
  }

  public @NonNull CrossVariableMultistageMoveSelectorConfig withCandidateCountLimit(
      @NonNull Integer candidateCountLimit) {
    setCandidateCountLimit(candidateCountLimit);
    return this;
  }

  public @NonNull CrossVariableMultistageMoveSelectorConfig withProbeCountLimit(
      @NonNull Integer probeCountLimit) {
    setProbeCountLimit(probeCountLimit);
    return this;
  }

  public int determineCandidateCountLimit() {
    int resolved =
        candidateCountLimit == null ? DEFAULT_CANDIDATE_COUNT_LIMIT : candidateCountLimit;
    if (resolved <= 0) {
      throw new IllegalArgumentException(
          "The crossVariableMultistageMoveSelector candidateCountLimit (%d) must be positive."
              .formatted(resolved));
    }
    return resolved;
  }

  public int determineProbeCountLimit() {
    int resolved = probeCountLimit == null ? DEFAULT_PROBE_COUNT_LIMIT : probeCountLimit;
    if (resolved <= 0) {
      throw new IllegalArgumentException(
          "The crossVariableMultistageMoveSelector probeCountLimit (%d) must be positive."
              .formatted(resolved));
    }
    return resolved;
  }

  @Override
  public @NonNull CrossVariableMultistageMoveSelectorConfig inherit(
      @NonNull CrossVariableMultistageMoveSelectorConfig inheritedConfig) {
    super.inherit(inheritedConfig);
    stageProviderClass =
        ConfigUtils.inheritOverwritableProperty(
            stageProviderClass, inheritedConfig.stageProviderClass);
    if (variableList == null && inheritedConfig.variableList != null) {
      variableList = new ArrayList<>(inheritedConfig.variableList.size());
      for (var variable : inheritedConfig.variableList) {
        variableList.add(variable == null ? null : variable.copyConfig());
      }
    }
    candidateCountLimit =
        ConfigUtils.inheritOverwritableProperty(
            candidateCountLimit, inheritedConfig.candidateCountLimit);
    probeCountLimit =
        ConfigUtils.inheritOverwritableProperty(probeCountLimit, inheritedConfig.probeCountLimit);
    return this;
  }

  @Override
  public @NonNull CrossVariableMultistageMoveSelectorConfig copyConfig() {
    return new CrossVariableMultistageMoveSelectorConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    visitCommonReferencedClasses(classVisitor);
    if (stageProviderClass != null) classVisitor.accept(getStageProviderClass());
    if (variableList != null) {
      for (var variable : variableList) {
        if (variable != null) variable.visitReferencedClasses(classVisitor);
      }
    }
  }

  @Override
  public boolean hasNearbySelectionConfig() {
    return false;
  }
}
