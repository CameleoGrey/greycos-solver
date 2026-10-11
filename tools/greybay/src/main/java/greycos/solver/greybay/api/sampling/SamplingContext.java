package greycos.solver.greybay.api.sampling;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.SearchSpace;

/** The comparator returns a positive value for a better observation; held-out data never enters. */
public record SamplingContext(
    SearchSpace space,
    List<SamplingObservation> history,
    Set<String> reservedIdentities,
    Comparator<SamplingObservation> qualityComparator,
    Function<ParameterAssignment, String> identityResolver) {
  public SamplingContext {
    Objects.requireNonNull(space, "space");
    Objects.requireNonNull(qualityComparator, "qualityComparator");
    Objects.requireNonNull(identityResolver, "identityResolver");
    history =
        history.stream().sorted(Comparator.comparingLong(SamplingObservation::trialId)).toList();
    reservedIdentities = Set.copyOf(reservedIdentities);
    var ids = new HashSet<Long>();
    var candidates = new HashSet<String>();
    for (var observation : history) {
      space.validate(observation.assignment());
      if (!ids.add(observation.trialId()) || !candidates.add(observation.candidateIdentity()))
        throw new IllegalArgumentException(
            "Sampling history must contain unique trial IDs and candidate identities.");
    }
  }
}
