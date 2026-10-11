package greycos.solver.greybay.api;

import java.time.Duration;
import java.util.Map;

import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.SearchSpace;

/**
 * An instance-independent, versioned materializer with an explicitly declared conditional space.
 */
public interface ConfigurationTemplate {
  ComponentIdentity identity();

  SearchSpace searchSpace();

  ParameterAssignment defaultAssignment();

  void validate(ProblemCapabilities capabilities);

  SolverConfig materialize(SolverConfig baseCopy, ParameterAssignment assignment);

  /** Approved nonfinal search-phase caps, keyed by absolute phase index. */
  default Map<Integer, Duration> approvedSearchPhaseLimits(
      SolverConfig materialized, Duration horizon, ParameterAssignment assignment) {
    return Map.of();
  }
}
