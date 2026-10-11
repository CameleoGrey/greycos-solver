package greycos.solver.greybay.config;

import greycos.solver.greybay.api.sampling.Sampler;
import greycos.solver.greybay.impl.sampling.TpeSampler;

/** Fixed version-one numerical reference settings; custom policies implement Sampler separately. */
public record TpeSamplerConfig() implements SamplerConfig {
  public static final int STARTUP_TRIALS = 20;
  public static final int ROOT_COVERAGE = 2;
  public static final int ACQUISITION_CANDIDATES = 64;
  public static final int EXPLORATION_INTERVAL = 10;

  @Override
  public Sampler buildSampler() {
    return new TpeSampler();
  }
}
