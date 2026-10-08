package greycos.solver.core.config.geneticalgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in serial genetic algorithm for initialized solutions with basic planning variables, one
 * planning list variable, or both. Defaults are applied after inheritance by {@link #resolve()}.
 */
@XmlType(
    propOrder = {
      "populationSize",
      "crossoverProbability",
      "pBestRate",
      "mutationRateMultiplier",
      "tabuEntityRate",
      "noProgressAttemptLimit",
      "localImprovementMoveCountLimit",
      "moveThreadCount",
      "mutationOperatorConfigList"
    })
public final class GeneticAlgorithmPhaseConfig extends PhaseConfig<GeneticAlgorithmPhaseConfig> {
  public static final String XML_ELEMENT_NAME = "geneticAlgorithm";

  private Integer populationSize;
  private Double crossoverProbability;
  private Double pBestRate;
  private Double mutationRateMultiplier;
  private Double tabuEntityRate;
  private Long noProgressAttemptLimit;
  private Long localImprovementMoveCountLimit;
  private String moveThreadCount;

  @XmlElement(name = "mutationOperator")
  private List<GeneticAlgorithmMutationOperatorConfig> mutationOperatorConfigList;

  /** Positive number of population members; defaults to 128. */
  public @Nullable Integer getPopulationSize() {
    return populationSize;
  }

  public void setPopulationSize(@Nullable Integer populationSize) {
    this.populationSize = populationSize;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withPopulationSize(@NonNull Integer populationSize) {
    setPopulationSize(populationSize);
    return this;
  }

  /** Finite crossover probability in [0, 1] for each parent pair; defaults to 0.5. */
  public @Nullable Double getCrossoverProbability() {
    return crossoverProbability;
  }

  public void setCrossoverProbability(@Nullable Double crossoverProbability) {
    this.crossoverProbability = crossoverProbability;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withCrossoverProbability(
      @NonNull Double crossoverProbability) {
    setCrossoverProbability(crossoverProbability);
    return this;
  }

  /** Finite maximum ranked parent and replacement fraction in (0.000001, 1]; defaults to 0.05. */
  public @Nullable Double getPBestRate() {
    return pBestRate;
  }

  public void setPBestRate(@Nullable Double pBestRate) {
    this.pBestRate = pBestRate;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withPBestRate(@NonNull Double pBestRate) {
    setPBestRate(pBestRate);
    return this;
  }

  /** Finite nonnegative mutation size multiplier; defaults to 0.0. */
  public @Nullable Double getMutationRateMultiplier() {
    return mutationRateMultiplier;
  }

  public void setMutationRateMultiplier(@Nullable Double mutationRateMultiplier) {
    this.mutationRateMultiplier = mutationRateMultiplier;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withMutationRateMultiplier(
      @NonNull Double mutationRateMultiplier) {
    setMutationRateMultiplier(mutationRateMultiplier);
    return this;
  }

  /** Finite fraction in [0, 1] of recently changed assignments held tabu; defaults to 0.0. */
  public @Nullable Double getTabuEntityRate() {
    return tabuEntityRate;
  }

  public void setTabuEntityRate(@Nullable Double tabuEntityRate) {
    this.tabuEntityRate = tabuEntityRate;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withTabuEntityRate(@NonNull Double tabuEntityRate) {
    setTabuEntityRate(tabuEntityRate);
    return this;
  }

  /**
   * Positive limit on consecutive attempts without fresh valid offspring scoring before this phase
   * ends. Defaults to {@code max(128, 10 * populationSize)}.
   */
  public @Nullable Long getNoProgressAttemptLimit() {
    return noProgressAttemptLimit;
  }

  public void setNoProgressAttemptLimit(@Nullable Long noProgressAttemptLimit) {
    this.noProgressAttemptLimit = noProgressAttemptLimit;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withNoProgressAttemptLimit(
      @NonNull Long noProgressAttemptLimit) {
    setNoProgressAttemptLimit(noProgressAttemptLimit);
    return this;
  }

  /**
   * Maximum completed local-improvement probes for each fresh generation offspring. Defaults to
   * zero, which disables improvement. Seeding, cached duplicates and unchanged offspring are not
   * improved. Probes also count toward solver-wide and phase-local move limits.
   */
  public @Nullable Long getLocalImprovementMoveCountLimit() {
    return localImprovementMoveCountLimit;
  }

  public void setLocalImprovementMoveCountLimit(@Nullable Long localImprovementMoveCountLimit) {
    this.localImprovementMoveCountLimit = localImprovementMoveCountLimit;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withLocalImprovementMoveCountLimit(
      @NonNull Long localImprovementMoveCountLimit) {
    setLocalImprovementMoveCountLimit(localImprovementMoveCountLimit);
    return this;
  }

  /** Overrides the solver move-thread count; use NONE to disable move workers. */
  public @Nullable String getMoveThreadCount() {
    return moveThreadCount;
  }

  public void setMoveThreadCount(@Nullable String moveThreadCount) {
    this.moveThreadCount = moveThreadCount;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withMoveThreadCount(@NonNull String moveThreadCount) {
    setMoveThreadCount(moveThreadCount);
    return this;
  }

  /**
   * Null enables all six mutations equally. An explicit list disables omitted mutations and must
   * contain unique types with finite nonnegative probabilities summing to one.
   */
  public @Nullable List<GeneticAlgorithmMutationOperatorConfig> getMutationOperatorConfigList() {
    return mutationOperatorConfigList;
  }

  public void setMutationOperatorConfigList(
      @Nullable List<GeneticAlgorithmMutationOperatorConfig> mutationOperatorConfigList) {
    this.mutationOperatorConfigList = mutationOperatorConfigList;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withMutationOperatorConfigList(
      @NonNull List<GeneticAlgorithmMutationOperatorConfig> mutationOperatorConfigList) {
    setMutationOperatorConfigList(mutationOperatorConfigList);
    return this;
  }

  public @NonNull GeneticAlgorithmPhaseConfig withMutationOperators(
      @NonNull GeneticAlgorithmMutationOperatorConfig... mutationOperators) {
    return withMutationOperatorConfigList(new ArrayList<>(Arrays.asList(mutationOperators)));
  }

  /** Returns an independent, validated copy with defaults filled in. */
  public @NonNull GeneticAlgorithmPhaseConfig resolve() {
    var resolved = copyConfig();
    resolved.populationSize = Objects.requireNonNullElse(resolved.populationSize, 128);
    resolved.crossoverProbability = Objects.requireNonNullElse(resolved.crossoverProbability, 0.5);
    resolved.pBestRate = Objects.requireNonNullElse(resolved.pBestRate, 0.05);
    resolved.mutationRateMultiplier =
        Objects.requireNonNullElse(resolved.mutationRateMultiplier, 0.0);
    resolved.tabuEntityRate = Objects.requireNonNullElse(resolved.tabuEntityRate, 0.0);
    resolved.noProgressAttemptLimit =
        Objects.requireNonNullElse(
            resolved.noProgressAttemptLimit, Math.max(128L, 10L * resolved.populationSize));
    resolved.localImprovementMoveCountLimit =
        Objects.requireNonNullElse(resolved.localImprovementMoveCountLimit, 0L);
    if (resolved.populationSize < 1) {
      throw new IllegalArgumentException(
          "The populationSize (" + resolved.populationSize + ") must be positive.");
    }
    if (resolved.noProgressAttemptLimit < 1L) {
      throw new IllegalArgumentException(
          "The noProgressAttemptLimit (" + resolved.noProgressAttemptLimit + ") must be positive.");
    }
    if (resolved.localImprovementMoveCountLimit < 0L) {
      throw new IllegalArgumentException(
          "The localImprovementMoveCountLimit ("
              + resolved.localImprovementMoveCountLimit
              + ") must be nonnegative.");
    }
    validateUnitInterval("crossoverProbability", resolved.crossoverProbability);
    validateUnitInterval("tabuEntityRate", resolved.tabuEntityRate);
    if (!Double.isFinite(resolved.pBestRate)
        || resolved.pBestRate <= 0.000001
        || resolved.pBestRate > 1.0) {
      throw new IllegalArgumentException(
          "The pBestRate (" + resolved.pBestRate + ") must be finite and in (0.000001, 1].");
    }
    if (!Double.isFinite(resolved.mutationRateMultiplier)
        || resolved.mutationRateMultiplier < 0.0) {
      throw new IllegalArgumentException(
          "The mutationRateMultiplier ("
              + resolved.mutationRateMultiplier
              + ") must be finite and nonnegative.");
    }
    if (resolved.mutationOperatorConfigList == null) {
      resolved.mutationOperatorConfigList = new ArrayList<>();
      for (var type : GeneticAlgorithmMutationType.values()) {
        resolved.mutationOperatorConfigList.add(
            new GeneticAlgorithmMutationOperatorConfig()
                .withType(type)
                .withProbability(1.0 / GeneticAlgorithmMutationType.values().length));
      }
    }
    var seenTypes = EnumSet.noneOf(GeneticAlgorithmMutationType.class);
    double probabilitySum = 0.0;
    for (var operator : resolved.mutationOperatorConfigList) {
      if (operator == null || operator.getType() == null) {
        throw new IllegalArgumentException(
            "The mutationOperator (" + operator + ") must specify a type.");
      }
      if (!seenTypes.add(operator.getType())) {
        throw new IllegalArgumentException(
            "The mutationOperator type (" + operator.getType() + ") must be unique.");
      }
      var probability = operator.getProbability();
      if (probability == null || !Double.isFinite(probability) || probability < 0.0) {
        throw new IllegalArgumentException(
            "The mutationOperator ("
                + operator.getType()
                + ") probability ("
                + probability
                + ") must be finite and nonnegative.");
      }
      probabilitySum += probability;
    }
    if (Math.abs(probabilitySum - 1.0) > 1.0e-9) {
      throw new IllegalArgumentException(
          "The mutationOperator probabilities must sum to 1.0, but their sum is ("
              + probabilitySum
              + ").");
    }
    return resolved;
  }

  private static void validateUnitInterval(String property, double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(
          "The " + property + " (" + value + ") must be finite and in [0, 1].");
    }
  }

  @Override
  public @NonNull GeneticAlgorithmPhaseConfig inherit(
      @NonNull GeneticAlgorithmPhaseConfig inheritedConfig) {
    super.inherit(inheritedConfig);
    populationSize =
        ConfigUtils.inheritOverwritableProperty(populationSize, inheritedConfig.populationSize);
    crossoverProbability =
        ConfigUtils.inheritOverwritableProperty(
            crossoverProbability, inheritedConfig.crossoverProbability);
    pBestRate = ConfigUtils.inheritOverwritableProperty(pBestRate, inheritedConfig.pBestRate);
    mutationRateMultiplier =
        ConfigUtils.inheritOverwritableProperty(
            mutationRateMultiplier, inheritedConfig.mutationRateMultiplier);
    tabuEntityRate =
        ConfigUtils.inheritOverwritableProperty(tabuEntityRate, inheritedConfig.tabuEntityRate);
    noProgressAttemptLimit =
        ConfigUtils.inheritOverwritableProperty(
            noProgressAttemptLimit, inheritedConfig.noProgressAttemptLimit);
    localImprovementMoveCountLimit =
        ConfigUtils.inheritOverwritableProperty(
            localImprovementMoveCountLimit, inheritedConfig.localImprovementMoveCountLimit);
    moveThreadCount =
        ConfigUtils.inheritOverwritableProperty(moveThreadCount, inheritedConfig.moveThreadCount);
    if (mutationOperatorConfigList == null && inheritedConfig.mutationOperatorConfigList != null) {
      mutationOperatorConfigList =
          new ArrayList<>(inheritedConfig.mutationOperatorConfigList.size());
      for (var operator : inheritedConfig.mutationOperatorConfigList) {
        mutationOperatorConfigList.add(operator == null ? null : operator.copyConfig());
      }
    }
    return this;
  }

  @Override
  public @NonNull GeneticAlgorithmPhaseConfig copyConfig() {
    return new GeneticAlgorithmPhaseConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (terminationConfig != null) {
      terminationConfig.visitReferencedClasses(classVisitor);
    }
    if (mutationOperatorConfigList != null) {
      mutationOperatorConfigList.forEach(operator -> operator.visitReferencedClasses(classVisitor));
    }
  }
}
