package greycos.solver.core.config.iteratedlocalsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlList;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.io.jaxb.JaxbMoveSelectorConfigAdapter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Iterated local search with bounded improvement episodes and an ordered perturbation schedule. A
 * single strength selects fixed-strength ILS; multiple strengths select VNS scheduling.
 *
 * <p>The input must already be initialized. Strengths and both attempt limits are required;
 * illustrative tuning settings are not defaults. The inherited termination applies to the whole
 * phase, while the local search termination applies separately to each episode.
 */
@XmlType(
    propOrder = {
      "moveThreadCount", "localSearchConfig", "perturbationMoveSelectorConfig",
      "perturbationStrengths", "perturbationAttemptLimit", "episodeCandidateAttemptLimit",
      "iterationCountLimit", "acceptanceType"
    })
public final class IteratedLocalSearchPhaseConfig
    extends PhaseConfig<IteratedLocalSearchPhaseConfig> {

  public static final String XML_ELEMENT_NAME = "iteratedLocalSearch";

  private String moveThreadCount;

  @XmlElement(name = "localSearch")
  private LocalSearchPhaseConfig localSearchConfig;

  @XmlElement(name = "perturbation")
  @XmlJavaTypeAdapter(JaxbMoveSelectorConfigAdapter.class)
  private MoveSelectorConfig<?> perturbationMoveSelectorConfig;

  @XmlList private List<Integer> perturbationStrengths;
  private Long perturbationAttemptLimit;
  private Long episodeCandidateAttemptLimit;
  private Long iterationCountLimit;
  private IteratedLocalSearchAcceptanceType acceptanceType;

  /** Null inherits the enclosing solver or island worker setting. */
  public @Nullable String getMoveThreadCount() {
    return moveThreadCount;
  }

  public void setMoveThreadCount(@Nullable String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
  }

  /**
   * Required episode configuration; its algorithm defaults are ordinary local search defaults. Its
   * step logging mode applies to all committed phase steps, including perturbation moves.
   */
  public @Nullable LocalSearchPhaseConfig getLocalSearchConfig() {
    return localSearchConfig;
  }

  public void setLocalSearchConfig(@Nullable LocalSearchPhaseConfig localSearchConfig) {
    this.localSearchConfig = localSearchConfig;
  }

  /** Null selects the ordinary model-derived native move portfolio. */
  public @Nullable MoveSelectorConfig<?> getPerturbationMoveSelectorConfig() {
    return perturbationMoveSelectorConfig;
  }

  public void setPerturbationMoveSelectorConfig(@Nullable MoveSelectorConfig<?> selectorConfig) {
    this.perturbationMoveSelectorConfig = selectorConfig;
  }

  /** Required strictly increasing positive counts of successful perturbation moves. */
  public @Nullable List<Integer> getPerturbationStrengths() {
    return perturbationStrengths;
  }

  public void setPerturbationStrengths(@Nullable List<Integer> perturbationStrengths) {
    this.perturbationStrengths =
        perturbationStrengths == null ? null : new ArrayList<>(perturbationStrengths);
  }

  /** Required positive selection-attempt cap per perturbation, at least the largest strength. */
  public @Nullable Long getPerturbationAttemptLimit() {
    return perturbationAttemptLimit;
  }

  public void setPerturbationAttemptLimit(@Nullable Long perturbationAttemptLimit) {
    this.perturbationAttemptLimit = perturbationAttemptLimit;
  }

  /** Required positive cap on ordered consumed selection attempts across an episode. */
  public @Nullable Long getEpisodeCandidateAttemptLimit() {
    return episodeCandidateAttemptLimit;
  }

  public void setEpisodeCandidateAttemptLimit(@Nullable Long episodeCandidateAttemptLimit) {
    this.episodeCandidateAttemptLimit = episodeCandidateAttemptLimit;
  }

  /** Optional positive cap on completed iterations; initial improvement is not an iteration. */
  public @Nullable Long getIterationCountLimit() {
    return iterationCountLimit;
  }

  public void setIterationCountLimit(@Nullable Long iterationCountLimit) {
    this.iterationCountLimit = iterationCountLimit;
  }

  /** Null selects {@link IteratedLocalSearchAcceptanceType#IMPROVING_ONLY}. */
  public @Nullable IteratedLocalSearchAcceptanceType getAcceptanceType() {
    return acceptanceType;
  }

  public void setAcceptanceType(@Nullable IteratedLocalSearchAcceptanceType acceptanceType) {
    this.acceptanceType = acceptanceType;
  }

  public @NonNull IteratedLocalSearchPhaseConfig withMoveThreadCount(
      @NonNull String moveThreadCount) {
    setMoveThreadCount(moveThreadCount);
    return this;
  }

  public @NonNull IteratedLocalSearchPhaseConfig withLocalSearch(
      @NonNull LocalSearchPhaseConfig localSearchConfig) {
    setLocalSearchConfig(localSearchConfig);
    return this;
  }

  public @NonNull IteratedLocalSearchPhaseConfig withPerturbationMoveSelectorConfig(
      @NonNull MoveSelectorConfig<?> selectorConfig) {
    setPerturbationMoveSelectorConfig(selectorConfig);
    return this;
  }

  public @NonNull IteratedLocalSearchPhaseConfig withPerturbationStrengths(
      int @NonNull ... strengths) {
    setPerturbationStrengths(Arrays.stream(strengths).boxed().toList());
    return this;
  }

  public @NonNull IteratedLocalSearchPhaseConfig withPerturbationAttemptLimit(long limit) {
    setPerturbationAttemptLimit(limit);
    return this;
  }

  public @NonNull IteratedLocalSearchPhaseConfig withEpisodeCandidateAttemptLimit(long limit) {
    setEpisodeCandidateAttemptLimit(limit);
    return this;
  }

  public @NonNull IteratedLocalSearchPhaseConfig withIterationCountLimit(long limit) {
    setIterationCountLimit(limit);
    return this;
  }

  public @NonNull IteratedLocalSearchPhaseConfig withAcceptanceType(
      @NonNull IteratedLocalSearchAcceptanceType acceptanceType) {
    setAcceptanceType(acceptanceType);
    return this;
  }

  @Override
  public @NonNull IteratedLocalSearchPhaseConfig inherit(
      @NonNull IteratedLocalSearchPhaseConfig inheritedConfig) {
    super.inherit(inheritedConfig);
    moveThreadCount =
        ConfigUtils.inheritOverwritableProperty(moveThreadCount, inheritedConfig.moveThreadCount);
    localSearchConfig =
        ConfigUtils.inheritConfig(localSearchConfig, inheritedConfig.localSearchConfig);
    // Ordinary LS inheritance shares its move selector. An episode configuration owns its copy.
    if (localSearchConfig != null && localSearchConfig.getMoveSelectorConfig() != null) {
      localSearchConfig.setMoveSelectorConfig(
          (MoveSelectorConfig<?>) localSearchConfig.getMoveSelectorConfig().copyConfig());
    }
    var selector =
        ConfigUtils.inheritOverwritableProperty(
            perturbationMoveSelectorConfig, inheritedConfig.perturbationMoveSelectorConfig);
    perturbationMoveSelectorConfig = selector == null ? null : selector.copyConfig();
    setPerturbationStrengths(
        ConfigUtils.inheritOverwritableProperty(
            perturbationStrengths, inheritedConfig.perturbationStrengths));
    perturbationAttemptLimit =
        ConfigUtils.inheritOverwritableProperty(
            perturbationAttemptLimit, inheritedConfig.perturbationAttemptLimit);
    episodeCandidateAttemptLimit =
        ConfigUtils.inheritOverwritableProperty(
            episodeCandidateAttemptLimit, inheritedConfig.episodeCandidateAttemptLimit);
    iterationCountLimit =
        ConfigUtils.inheritOverwritableProperty(
            iterationCountLimit, inheritedConfig.iterationCountLimit);
    acceptanceType =
        ConfigUtils.inheritOverwritableProperty(acceptanceType, inheritedConfig.acceptanceType);
    return this;
  }

  @Override
  public @NonNull IteratedLocalSearchPhaseConfig copyConfig() {
    return new IteratedLocalSearchPhaseConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (terminationConfig != null) terminationConfig.visitReferencedClasses(classVisitor);
    if (localSearchConfig != null) localSearchConfig.visitReferencedClasses(classVisitor);
    if (perturbationMoveSelectorConfig != null)
      perturbationMoveSelectorConfig.visitReferencedClasses(classVisitor);
  }
}
