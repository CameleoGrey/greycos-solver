package greycos.solver.greybay.impl.sampling;

import java.util.List;

import greycos.solver.greybay.config.CategoricalDimension;

final class CategoricalDensity implements Density {
  private final CategoricalDimension dimension;
  private final double[] probabilities;

  CategoricalDensity(CategoricalDimension dimension, List<Object> observations) {
    this.dimension = dimension;
    probabilities = dimension.probabilities();
    for (var observation : observations)
      probabilities[dimension.choices().indexOf(observation)] += 1;
    for (int index = 0; index < probabilities.length; index++)
      probabilities[index] /= observations.size() + 1.0;
    double cumulative = 0;
    for (int index = 0; index < probabilities.length; index++) {
      double next = index == probabilities.length - 1 ? 1 : cumulative + probabilities[index];
      if (!(next > cumulative) || next > 1 || Math.ceil(cumulative * 0x1.0p53) >= next * 0x1.0p53)
        throw new IllegalArgumentException(
            "Unresolved categorical posterior interval for ("
                + dimension.name()
                + ") choice ("
                + dimension.choices().get(index)
                + ").");
      cumulative = next;
    }
  }

  @Override
  public Object sample(SplitMix64 random) {
    if (probabilities.length == 1) return dimension.choices().getFirst();
    double draw = random.nextDouble();
    double cumulative = 0;
    for (int index = 0; index < probabilities.length - 1; index++) {
      cumulative += probabilities[index];
      if (draw < cumulative) return dimension.choices().get(index);
    }
    return dimension.choices().getLast();
  }

  @Override
  public double logProbability(Object value) {
    return StrictMath.log(probabilities[dimension.choices().indexOf(value)]);
  }
}
