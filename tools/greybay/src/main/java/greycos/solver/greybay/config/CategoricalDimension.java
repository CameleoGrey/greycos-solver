package greycos.solver.greybay.config;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record CategoricalDimension(
    String name, List<String> choices, List<Double> weights, Activation activation)
    implements Dimension {
  public CategoricalDimension {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Dimension name must not be blank.");
    choices = List.copyOf(choices);
    weights = List.copyOf(weights);
    Objects.requireNonNull(activation, "activation");
    if (choices.isEmpty() || new HashSet<>(choices).size() != choices.size())
      throw new IllegalArgumentException(
          "Categorical choices for (" + name + ") must be nonempty and unique.");
    if (weights.size() != choices.size()
        || weights.stream().anyMatch(weight -> !Double.isFinite(weight) || weight <= 0))
      throw new IllegalArgumentException(
          "Categorical weights for (" + name + ") must be finite and positive, one per choice.");
    double max = weights.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
    double sum = weights.stream().mapToDouble(weight -> weight / max).sum();
    if (weights.stream().anyMatch(weight -> (weight / max) / sum == 0))
      throw new IllegalArgumentException(
          "Categorical prior for (" + name + ") has unrepresentable positive mass.");
    double cumulative = 0;
    for (int index = 0; index < weights.size(); index++) {
      double next = index == weights.size() - 1 ? 1 : cumulative + (weights.get(index) / max) / sum;
      if (!(next > cumulative) || next > 1 || Math.ceil(cumulative * 0x1.0p53) >= next * 0x1.0p53)
        throw new IllegalArgumentException(
            "Categorical prior for ("
                + name
                + ") has an unresolved sampling interval at choice ("
                + choices.get(index)
                + ").");
      cumulative = next;
    }
  }

  public CategoricalDimension(String name, List<String> choices) {
    this(name, choices, java.util.Collections.nCopies(choices.size(), 1.0), Activation.always());
  }

  public CategoricalDimension(String name, List<String> choices, Activation activation) {
    this(name, choices, java.util.Collections.nCopies(choices.size(), 1.0), activation);
  }

  public double[] probabilities() {
    double max = weights.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
    double sum = weights.stream().mapToDouble(weight -> weight / max).sum();
    return weights.stream().mapToDouble(weight -> (weight / max) / sum).toArray();
  }

  @Override
  public CategoricalDimension withActivation(Activation condition) {
    return new CategoricalDimension(name, choices, weights, condition);
  }

  @Override
  public void validateValue(Object value) {
    if (!(value instanceof String) || !choices.contains(value))
      throw new IllegalArgumentException(
          "Invalid categorical value (" + value + ") for (" + name + ").");
  }
}
