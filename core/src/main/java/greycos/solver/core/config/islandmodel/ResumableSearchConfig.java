package greycos.solver.core.config.islandmodel;

import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.config.AbstractConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Selects resident search sessions with deterministic completed-step quanta and bounded workers.
 * Logical search width remains {@link IslandModelPhaseConfig#getIslandCount()}.
 *
 * <p>Worker count and quantum must be explicitly configured somewhere in the enclosing parallel
 * hierarchy. Omitted values inherit that hierarchy's values; repeated declarations must agree.
 * Configuration objects retain null values until runtime resolution.
 */
@XmlType(propOrder = {"workerCount", "stepQuantum", "cooperationPolicy", "stagnationRounds"})
public class ResumableSearchConfig extends AbstractConfig<ResumableSearchConfig> {

  private Integer workerCount;
  private Integer stepQuantum;
  private ResumableSearchCooperationPolicy cooperationPolicy;
  private Integer stagnationRounds;

  /** Positive number of persistent workers shared by the enclosing parallel hierarchy. */
  public @Nullable Integer getWorkerCount() {
    return workerCount;
  }

  public void setWorkerCount(@Nullable Integer workerCount) {
    this.workerCount = workerCount;
  }

  /** Positive maximum number of completed steps granted to each active session per round. */
  public @Nullable Integer getStepQuantum() {
    return stepQuantum;
  }

  public void setStepQuantum(@Nullable Integer stepQuantum) {
    this.stepQuantum = stepQuantum;
  }

  /** Defaults to {@link ResumableSearchCooperationPolicy#NONE}. */
  public @Nullable ResumableSearchCooperationPolicy getCooperationPolicy() {
    return cooperationPolicy;
  }

  public void setCooperationPolicy(@Nullable ResumableSearchCooperationPolicy cooperationPolicy) {
    this.cooperationPolicy = cooperationPolicy;
  }

  /**
   * Positive number of completed rounds without episode-best improvement before restart
   * eligibility. Required for {@link ResumableSearchCooperationPolicy#RESTART_WEAKEST}.
   */
  public @Nullable Integer getStagnationRounds() {
    return stagnationRounds;
  }

  public void setStagnationRounds(@Nullable Integer stagnationRounds) {
    this.stagnationRounds = stagnationRounds;
  }

  public @NonNull ResumableSearchConfig withWorkerCount(int workerCount) {
    setWorkerCount(workerCount);
    return this;
  }

  public @NonNull ResumableSearchConfig withStepQuantum(int stepQuantum) {
    setStepQuantum(stepQuantum);
    return this;
  }

  public @NonNull ResumableSearchConfig withCooperationPolicy(
      @NonNull ResumableSearchCooperationPolicy cooperationPolicy) {
    setCooperationPolicy(cooperationPolicy);
    return this;
  }

  public @NonNull ResumableSearchConfig withStagnationRounds(int stagnationRounds) {
    setStagnationRounds(stagnationRounds);
    return this;
  }

  @Override
  public @NonNull ResumableSearchConfig inherit(@NonNull ResumableSearchConfig inheritedConfig) {
    workerCount = ConfigUtils.inheritOverwritableProperty(workerCount, inheritedConfig.workerCount);
    stepQuantum = ConfigUtils.inheritOverwritableProperty(stepQuantum, inheritedConfig.stepQuantum);
    cooperationPolicy =
        ConfigUtils.inheritOverwritableProperty(
            cooperationPolicy, inheritedConfig.cooperationPolicy);
    stagnationRounds =
        ConfigUtils.inheritOverwritableProperty(stagnationRounds, inheritedConfig.stagnationRounds);
    return this;
  }

  @Override
  public @NonNull ResumableSearchConfig copyConfig() {
    return new ResumableSearchConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    // No user-defined classes are referenced by this configuration.
  }
}
