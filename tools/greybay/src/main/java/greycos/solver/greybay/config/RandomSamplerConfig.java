package greycos.solver.greybay.config;

import greycos.solver.greybay.api.sampling.Sampler;
import greycos.solver.greybay.impl.sampling.RandomSampler;

/** The reference prior sampler has no mutable or undocumented defaults. */
public record RandomSamplerConfig() implements SamplerConfig {
  @Override
  public Sampler buildSampler() {
    return new RandomSampler();
  }
}
