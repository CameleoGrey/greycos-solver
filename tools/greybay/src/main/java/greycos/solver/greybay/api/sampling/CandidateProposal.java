package greycos.solver.greybay.api.sampling;

import java.util.Objects;

import greycos.solver.greybay.config.ParameterAssignment;

/** Either an admission or a stop; both carry the next durable state. */
public record CandidateProposal(
    ParameterAssignment assignment,
    String candidateIdentity,
    long proposalSequence,
    ProposalReason reason,
    SamplingStopReason stopReason,
    int goodObservationCount,
    int otherObservationCount,
    SamplerState nextState) {
  public CandidateProposal {
    Objects.requireNonNull(nextState, "nextState");
    if (proposalSequence < 1 || goodObservationCount < 0 || otherObservationCount < 0)
      throw new IllegalArgumentException("Invalid proposal counters.");
    if (stopReason == null) {
      Objects.requireNonNull(assignment, "assignment");
      Objects.requireNonNull(candidateIdentity, "candidateIdentity");
      Objects.requireNonNull(reason, "reason");
    } else if (assignment != null || candidateIdentity != null)
      throw new IllegalArgumentException("Sampling stop cannot contain a candidate.");
  }

  public boolean stopped() {
    return stopReason != null;
  }
}
