package greycos.solver.greybay.api.objective;

import java.util.List;
import java.util.Objects;

/** Exact feasibility rate followed by exact equally weighted, normalized score-level gains. */
public record BalancedObjectiveValue(
    ExactRational feasibilityRate, List<ExactRational> normalizedGains) implements ObjectiveValue {
  public BalancedObjectiveValue {
    Objects.requireNonNull(feasibilityRate, "feasibilityRate");
    normalizedGains = List.copyOf(normalizedGains);
    if (feasibilityRate.signum() < 0
        || feasibilityRate.compareTo(ExactRational.ONE) > 0
        || normalizedGains.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid balanced objective tuple (" + feasibilityRate + ", " + normalizedGains + ").");
    }
  }

  public boolean fullyFeasible() {
    return feasibilityRate.equals(ExactRational.ONE);
  }
}
