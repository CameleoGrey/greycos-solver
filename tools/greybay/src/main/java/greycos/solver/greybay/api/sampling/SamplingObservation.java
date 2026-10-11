package greycos.solver.greybay.api.sampling;

import java.util.Objects;

import greycos.solver.greybay.config.ParameterAssignment;

/** Terminal trial only. A calibration row is supplied only after exact assignment equivalence. */
public record SamplingObservation(
    long trialId,
    ParameterAssignment assignment,
    String candidateIdentity,
    boolean eligible,
    boolean calibration,
    String qualityKey) {
  public SamplingObservation {
    if (trialId < 0) throw new IllegalArgumentException("Trial ID must be nonnegative.");
    Objects.requireNonNull(assignment, "assignment");
    Objects.requireNonNull(candidateIdentity, "candidateIdentity");
    if (eligible) Objects.requireNonNull(qualityKey, "qualityKey");
  }
}
