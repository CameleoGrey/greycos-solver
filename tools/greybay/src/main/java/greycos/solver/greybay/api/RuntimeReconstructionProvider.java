package greycos.solver.greybay.api;

import greycos.solver.core.config.solver.SolverConfig;

/**
 * Reconstructs fresh runtime-only components after XML loading. It must not change XML-visible
 * configuration. Its identity covers generated accessors, custom commands, clocks and resources.
 */
public interface RuntimeReconstructionProvider {
  ComponentIdentity identity();

  void reconstruct(SolverConfig solverConfig);
}
