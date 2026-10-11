package greycos.solver.greybay.config.template;

import java.util.Objects;

import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.SearchSpace;

/**
 * Explicit application bounds and a deterministic warmup/preflight assignment. No tuning ranges are
 * implied.
 */
public record TemplateParameters(
    ComponentIdentity identity, SearchSpace searchSpace, ParameterAssignment defaultAssignment) {
  public TemplateParameters {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(searchSpace, "searchSpace");
    Objects.requireNonNull(defaultAssignment, "defaultAssignment");
    searchSpace.validate(defaultAssignment);
  }
}
