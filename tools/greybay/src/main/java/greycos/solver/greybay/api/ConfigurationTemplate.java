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

  /**
   * Approved nonfinal search-phase caps, keyed by absolute phase index in the resulting
   * configuration. The materialized configuration is validated again after this callback returns.
   * Any changes must preserve the study's frozen scoring, initialization and resource policies.
   * Return phase caps here instead of installing phase terminations directly.
   */
  default Map<Integer, Duration> approvedSearchPhaseLimits(
      SolverConfig materialized, Duration horizon, ParameterAssignment assignment) {
    return Map.of();
  }
}
