package greycos.solver.greybay.api;

import java.util.List;
import java.util.Set;

/**
 * Application-declared model capabilities; runtime preflight also exercises the actual bindings.
 */
public record ProblemCapabilities(
    boolean basicVariables,
    boolean listVariables,
    boolean allowsUnassigned,
    boolean pinned,
    String scoreType,
    List<String> scoreLevelLabels,
    Set<String> features) {
  public ProblemCapabilities {
    ComponentIdentity.requireText(scoreType, "scoreType");
    scoreLevelLabels = List.copyOf(scoreLevelLabels);
    features = Set.copyOf(features);
    scoreLevelLabels.forEach(label -> ComponentIdentity.requireText(label, "scoreLevelLabel"));
    features.forEach(feature -> ComponentIdentity.requireText(feature, "feature"));
  }

  public ProblemCapabilities(boolean basicVariables, boolean listVariables) {
    this(basicVariables, listVariables, false, false, "unspecified", List.of(), Set.of());
  }

  public ProblemCapabilities withFeatures(Set<String> features) {
    return new ProblemCapabilities(
        basicVariables,
        listVariables,
        allowsUnassigned,
        pinned,
        scoreType,
        scoreLevelLabels,
        features);
  }
}
