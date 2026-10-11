package greycos.solver.greybay.config;

import greycos.solver.greybay.api.sampling.Sampler;

public sealed interface SamplerConfig permits TpeSamplerConfig, RandomSamplerConfig {
  Sampler buildSampler();
}
