package greycos.solver.greybay.impl.sampling;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.greybay.api.sampling.SamplingObservation;
import greycos.solver.greybay.config.CategoricalDimension;
import greycos.solver.greybay.config.Dimension;
import greycos.solver.greybay.config.FixedDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.SearchSpace;

final class ConditionalModel {
  private final SearchSpace space;
  private final List<SamplingObservation> good;
  private final List<SamplingObservation> other;
  private final Map<Key, Pair> densities = new HashMap<>();

  ConditionalModel(
      SearchSpace space, List<SamplingObservation> good, List<SamplingObservation> other) {
    this.space = space;
    this.good = good;
    this.other = other;
  }

  Draw draw(SplitMix64 random, String forcedRoot) {
    var values = new LinkedHashMap<String, Object>();
    double acquisition = 0;
    for (var dimension : space.dimensions()) {
      if (!dimension.activation().isActive(values)) continue;
      if (forcedRoot != null && dimension.name().equals("template")) {
        values.put("template", forcedRoot);
        continue;
      }
      if (dimension instanceof FixedDimension fixed) {
        values.put(dimension.name(), fixed.value());
        continue;
      }
      var partial = ParameterAssignment.of(values);
      var pair =
          densities.computeIfAbsent(
              new Key(dimension.name(), space.contextKey(dimension, partial)),
              ignored -> fit(dimension, partial));
      Object value = pair.good.sample(random);
      values.put(dimension.name(), value);
      if (!pair.cold)
        acquisition += pair.good.logProbability(value) - pair.other.logProbability(value);
    }
    if (!Double.isFinite(acquisition))
      throw new IllegalArgumentException("Nonfinite TPE acquisition.");
    var assignment = ParameterAssignment.of(values);
    space.validate(assignment);
    return new Draw(assignment, acquisition);
  }

  private Pair fit(Dimension dimension, ParameterAssignment partial) {
    String context = space.contextKey(dimension, partial);
    var better = activeValues(good, dimension, context);
    var worse = activeValues(other, dimension, context);
    boolean cold = better.size() < 2 || worse.size() < 2;
    Density left = density(dimension, cold ? List.of() : better);
    return new Pair(left, cold ? left : density(dimension, worse), cold);
  }

  private List<Object> activeValues(
      List<SamplingObservation> observations, Dimension dimension, String context) {
    return observations.stream()
        .sorted(java.util.Comparator.comparingLong(SamplingObservation::trialId))
        .map(SamplingObservation::assignment)
        .filter(
            assignment ->
                assignment.contains(dimension.name())
                    && space.contextKey(dimension, assignment).equals(context))
        .map(assignment -> assignment.get(dimension.name()))
        .toList();
  }

  private static Density density(Dimension dimension, List<Object> observations) {
    return dimension instanceof CategoricalDimension categorical
        ? new CategoricalDensity(categorical, observations)
        : new NumericDensity(dimension, observations);
  }

  record Draw(ParameterAssignment assignment, double acquisition) {}

  private record Key(String name, String context) {}

  private record Pair(Density good, Density other, boolean cold) {}
}
