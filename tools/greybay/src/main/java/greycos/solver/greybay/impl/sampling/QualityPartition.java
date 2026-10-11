package greycos.solver.greybay.impl.sampling;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.greybay.api.sampling.SamplingContext;
import greycos.solver.greybay.api.sampling.SamplingObservation;

record QualityPartition(List<SamplingObservation> good, List<SamplingObservation> other) {
  static QualityPartition of(SamplingContext context) {
    var sorted =
        new ArrayList<>(context.history().stream().filter(SamplingObservation::eligible).toList());
    sorted.sort(
        context.qualityComparator().reversed().thenComparingLong(SamplingObservation::trialId));
    int target = (sorted.size() + 4) / 5;
    int boundary = -1, distance = Integer.MAX_VALUE;
    for (int index = 1; index < sorted.size(); index++) {
      if (context.qualityComparator().compare(sorted.get(index - 1), sorted.get(index)) == 0)
        continue;
      int candidateDistance = Math.abs(index - target);
      if (candidateDistance < distance) {
        boundary = index;
        distance = candidateDistance;
      }
    }
    return boundary < 0
        ? new QualityPartition(List.of(), List.of())
        : new QualityPartition(
            List.copyOf(sorted.subList(0, boundary)),
            List.copyOf(sorted.subList(boundary, sorted.size())));
  }

  boolean available() {
    return !good.isEmpty() && !other.isEmpty();
  }
}
