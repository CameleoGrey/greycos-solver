package greycos.solver.core.config.geneticalgorithm;

import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.config.AbstractConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** A named mutation and its probability before eligibility filtering. */
@XmlType(propOrder = {"type", "probability"})
public final class GeneticAlgorithmMutationOperatorConfig
    extends AbstractConfig<GeneticAlgorithmMutationOperatorConfig> {

  private GeneticAlgorithmMutationType type;
  private Double probability;

  public @Nullable GeneticAlgorithmMutationType getType() {
    return type;
  }

  public void setType(@Nullable GeneticAlgorithmMutationType type) {
    this.type = type;
  }

  public @NonNull GeneticAlgorithmMutationOperatorConfig withType(
      @NonNull GeneticAlgorithmMutationType type) {
    setType(type);
    return this;
  }

  public @Nullable Double getProbability() {
    return probability;
  }

  public void setProbability(@Nullable Double probability) {
    this.probability = probability;
  }

  public @NonNull GeneticAlgorithmMutationOperatorConfig withProbability(
      @NonNull Double probability) {
    setProbability(probability);
    return this;
  }

  @Override
  public @NonNull GeneticAlgorithmMutationOperatorConfig inherit(
      @NonNull GeneticAlgorithmMutationOperatorConfig inheritedConfig) {
    type = ConfigUtils.inheritOverwritableProperty(type, inheritedConfig.type);
    probability = ConfigUtils.inheritOverwritableProperty(probability, inheritedConfig.probability);
    return this;
  }

  @Override
  public @NonNull GeneticAlgorithmMutationOperatorConfig copyConfig() {
    return new GeneticAlgorithmMutationOperatorConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    // No class references.
  }
}
