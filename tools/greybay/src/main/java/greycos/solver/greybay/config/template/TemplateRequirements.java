package greycos.solver.greybay.config.template;

import java.util.Set;

import greycos.solver.greybay.api.ProblemCapabilities;

/** Application-qualified model and callback/scope capabilities for a fixed portfolio. */
public record TemplateRequirements(Set<ModelKind> models, Set<String> features) {
  public enum ModelKind {
    BASIC,
    LIST,
    MIXED,
    EMPTY
  }

  public TemplateRequirements {
    models = Set.copyOf(models);
    features = Set.copyOf(features);
    if (models.isEmpty() || features.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException(
          "Template models must be nonempty and capability IDs nonblank.");
    }
  }

  public static TemplateRequirements allModels() {
    return new TemplateRequirements(Set.of(ModelKind.values()), Set.of());
  }

  public void validate(ProblemCapabilities capabilities) {
    var kind =
        capabilities.basicVariables()
            ? (capabilities.listVariables() ? ModelKind.MIXED : ModelKind.BASIC)
            : (capabilities.listVariables() ? ModelKind.LIST : ModelKind.EMPTY);
    if (!models.contains(kind) || !capabilities.features().containsAll(features)) {
      throw new IllegalArgumentException(
          "Portfolio requirements ("
              + this
              + ") are not satisfied by problem capabilities ("
              + capabilities
              + ").");
    }
  }
}
