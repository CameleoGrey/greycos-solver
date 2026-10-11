package greycos.solver.greybay.api.sampling;

import greycos.solver.greybay.api.ComponentIdentity;

/** Sequential sampler; all mutable state is returned and committed before evaluation. */
public interface Sampler {
  ComponentIdentity identity();

  SamplerState initialState(long optimizerSeed);

  CandidateProposal propose(SamplingContext context, SamplerState state);
}
